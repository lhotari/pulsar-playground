package com.github.lhotari.pulsar.playground;

import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_BROKER_URL;
import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_SERVICE_URL;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.PulsarVersion;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.BatcherBuilder;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.KeySharedPolicy;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SizeUnit;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.policies.data.DelayedDeliveryPolicies;
import org.apache.pulsar.common.policies.data.Policies;
import org.apache.pulsar.common.policies.data.RetentionPolicies;
import org.apache.pulsar.common.policies.data.SubscriptionStats;
import org.apache.pulsar.common.util.FutureUtil;
import org.roaringbitmap.RoaringBitmap;

/**
 * Reproduce the issue https://github.com/apache/pulsar/issues/21958
 * Msg backlog & unack msg remains when using acknowledgeAsync
 * This reproduces when using the Key_Shared subscription type.
 *
 * Instructions for running this test scenario:
 *
 * Start a standalone Pulsar broker with the following command in a separate terminal:
 * docker run --name pulsar-standalone --rm -it -e PULSAR_STANDALONE_USE_ZOOKEEPER=1 -p 8080:8080 -p 6650:6650 apachepulsar/pulsar:3.2.3 /pulsar/bin/pulsar standalone -nss -nfw  2>&1 | tee standalone.log
 *
 * Build the test applications with the following command:
 * ./gradlew build
 *
 * Run the test scenario with the following command:
 * java -cp build/libs/pulsar-playground-all.jar com.github.lhotari.pulsar.playground.TestScenarioAckIssue 2>&1 | tee test.log
 *
 * At the end, there will be a 15 second waiting period to allow the consumers to finish processing messages.
 */
@Slf4j
public class TestScenarioAckIssue {
    public static final int RECEIVE_TIMEOUT_SECONDS = 15;
    private final String namespace;
    private int consumerCount = 4;
    private int maxMessages = 1000000;
    private int messageSize = 4;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(consumerCount);

    private boolean enableBatching = false;

    public TestScenarioAckIssue(String namespace) {
        this.namespace = namespace;
    }

    public void run() throws Throwable {
        log.info("Pulsar client version: {} {} {} {}", PulsarVersion.getVersion(), PulsarVersion.getGitBranch(),
                PulsarVersion.getGitSha(), PulsarVersion.getBuildTime());

        log.info("Waiting 2 seconds before starting...");
        Thread.sleep(2000L);

        @Cleanup
        PulsarClient pulsarClient = PulsarClient.builder()
                .serviceUrl(PULSAR_BROKER_URL)
                .memoryLimit(300, SizeUnit.MEGA_BYTES)
                .build();

        @Cleanup
        PulsarAdmin pulsarAdmin = PulsarAdmin.builder()
                .serviceHttpUrl(PULSAR_SERVICE_URL)
                .build();

        NamespaceName namespaceName = NamespaceName.get("public", namespace);

        String topicName = namespaceName.getPersistentTopicName("test");
        boolean newTopic = false;
        try {
            Policies policies = new Policies();
            // Configure offload to S3 emulated with localstack s3 service on port 4566
            // bucket name is "pulsar", created with "AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://localhost:4566 s3 mb s3://pulsar"
            // modifying conf/standalone.conf to use offload to s3:
            // managedLedgerMaxEntriesPerLedger=10000 managedLedgerMinLedgerRolloverTimeMinutes=0 PULSAR_PREFIX_managedLedgerOffloadDriver=aws-s3 PULSAR_PREFIX_s3ManagedLedgerOffloadRegion=us-east-1 PULSAR_PREFIX_s3ManagedLedgerOffloadBucket=pulsar PULSAR_PREFIX_s3ManagedLedgerOffloadServiceEndpoint=http://localhost:4566 PULSAR_PREFIX_s3ManagedLedgerOffloadCredentialId=test PULSAR_PREFIX_s3ManagedLedgerOffloadCredentialSecret=test docker/pulsar/scripts/apply-config-from-env.py conf/standalone.conf
            policies.offload_threshold = 0;
            policies.offload_deletion_lag_ms = 0L;
            policies.offload_threshold_in_seconds = 0;
            // no retention
            policies.retention_policies = new RetentionPolicies(0, 0);
            policies.delayed_delivery_policies = DelayedDeliveryPolicies.builder()
                    .active(false)
                    .build();
            pulsarAdmin.namespaces().createNamespace(namespaceName.toString(), policies);
            pulsarAdmin.topics().createNonPartitionedTopic(topicName);
            newTopic = true;
        } catch (PulsarAdminException.ConflictException e) {
            // topic exists, ignore
            log.info("Namespace or Topic exists {}", topicName);
        }

        Thread producerThread = null;
        if (newTopic) {
            try (Consumer<byte[]> consumer = createConsumerBuilder(pulsarClient, topicName, "sub1").subscribe()) {
                // just to create the subscription
            }
            producerThread = new Thread(() -> {
                try {
                    produceMessages(pulsarClient, topicName);
                } catch (Throwable throwable) {
                    log.error("Failed to produce messages", throwable);
                }
            });
            producerThread.start();
        } else {
            log.info("Attempting to consume remaining messages...");
        }
        producerThread.join();

        Phaser ackPhaser = new Phaser(consumerCount / 2);

        List<CompletableFuture<ConsumeReport>> tasks = IntStream.range(1, consumerCount + 1).mapToObj(i -> {
            String consumerName = "consumer" + i;
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return consumeMessages(topicName, consumerName, "sub" + i, i % 2 == 0, ackPhaser, pulsarAdmin);
                } catch (PulsarClientException e) {
                    log.error("Failed to consume messages", e);
                    return null;
                }
            }, runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName(consumerName);
                thread.start();
            });
        }).collect(Collectors.toUnmodifiableList());

        FutureUtil.waitForAll(tasks).get();

        List<ConsumeReport> results =
                tasks.stream().map(CompletableFuture::join)
                        .filter(Objects::nonNull).collect(Collectors.toUnmodifiableList());

        results.stream().sorted(Comparator.comparing(ConsumeReport::consumerName))
                .forEach(report ->
                        log.info(
                                "Consumer {} received {} unique messages {} duplicates in {} s, max latency "
                                        + "difference of subsequent messages {} ms\n"
                                        + "Using async ack in random order: {}\n"
                                        + "Unacknowledged messages: {}{}\n"
                                        + "Backlog: {}{}",
                                report.consumerName(), report.uniqueMessages(), report.duplicates(),
                                TimeUnit.MILLISECONDS.toSeconds(report.durationMillis()),
                                report.maxLatencyDifferenceMillis(),
                                report.ackAsync() ? "yes" : "no",
                                report.subscriptionStats().getUnackedMessages(),
                                report.subscriptionStats().getUnackedMessages() != 0 ? " <-- Acknowledgements lost!" :
                                        "",
                                report.subscriptionStats().getMsgBacklog(),
                                report.subscriptionStats().getMsgBacklog() != 0 ? " <-- Should be 0!" : ""));
    }

    private void produceMessages(PulsarClient pulsarClient, String topicName) throws Throwable {
        try (Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topicName)
                .enableBatching(enableBatching)
                .batcherBuilder(BatcherBuilder.KEY_BASED)
                .batchingMaxMessages(Math.max(50, maxMessages / 10000))
                .blockIfQueueFull(true)
                .create()) {
            AtomicReference<Throwable> sendFailure = new AtomicReference<>();
            for (int i = 1; i <= maxMessages; i++) {
                byte[] value = intToBytes(i, messageSize);
                producer.newMessage().value(value)
                        .keyBytes(value)
                        // set System.nanoTime() as event time
                        .eventTime(System.nanoTime())
                        .sendAsync().whenComplete((messageId, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to send message to topic {}", topicName, throwable);
                        sendFailure.set(throwable);
                    }
                });
                if (i % 1000 == 0) {
                    log.info("Sent {} msgs", i);
                }
                Throwable throwable = sendFailure.get();
                if (throwable != null) {
                    throw throwable;
                }
            }
            log.info("Flushing");
            producer.flush();
        }
        log.info("Done sending.");
    }

    private ConsumeReport consumeMessages(String topicName, String consumerName, String subscriptionName, boolean ackAsync,
                                          Phaser ackPhaser, PulsarAdmin pulsarAdmin)
            throws PulsarClientException {
        @Cleanup
        PulsarClient pulsarClient = PulsarClient.builder()
                .serviceUrl(PULSAR_BROKER_URL)
                .memoryLimit(300, SizeUnit.MEGA_BYTES)
                .build();

        RoaringBitmap receivedMessages = new RoaringBitmap();
        int uniqueMessages = 0;
        int duplicates = 0;

        Random random = ThreadLocalRandom.current();
        long startTimeNanos = System.nanoTime();
        long maxLatencyDifferenceNanos = 0;
        SubscriptionStats subscriptionStats = null;

        try (Consumer<byte[]> consumer = createConsumerBuilder(pulsarClient, topicName, subscriptionName)
                .consumerName(consumerName)
                .subscribe()) {
            int i = 0;

            long previousLatencyNanos = -1;
            while (!Thread.currentThread().isInterrupted()) {
                i++;
                Message<byte[]> msg = consumer.receive(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (msg == null) {
                    break;
                }

                // we set System.nanoTime() as event time in publishing for e2e latency calculation
                long latencyNanos = Math.max(System.nanoTime() - msg.getEventTime(), 0);
                if (previousLatencyNanos != -1) {
                    long latencyDifferenceNanos = Math.abs(latencyNanos - previousLatencyNanos);
                    if (latencyDifferenceNanos > maxLatencyDifferenceNanos) {
                        maxLatencyDifferenceNanos = latencyDifferenceNanos;
                        log.info("Max latency difference increased: {} ms",
                                TimeUnit.NANOSECONDS.toMillis(maxLatencyDifferenceNanos));
                    }
                }
                previousLatencyNanos = latencyNanos;

                int msgNum = bytesToInt(msg.getData());

                boolean added = receivedMessages.checkedAdd(msgNum);
                if (added) {
                    uniqueMessages++;
                } else {
                    duplicates++;
                }
                log.info("Received value: {} duplicate: {} unique: {} duplicates: {}", msgNum, !added, uniqueMessages,
                        duplicates);
                if (ackAsync) {
                    executorService.schedule(() -> {
                        CompletableFuture.runAsync(() -> {
                            // increase chances for race condition
                            ackPhaser.arriveAndAwaitAdvance();
                            consumer.acknowledgeAsync(msg).exceptionally(throwable -> {
                                log.error("Failed to acknowledge message", throwable);
                                return null;
                            });
                        });
                    }, random.nextInt(100) + 1, TimeUnit.MILLISECONDS);
                } else {
                    consumer.acknowledge(msg);
                }
            }

            try {
                subscriptionStats =
                        pulsarAdmin.topics().getStats(topicName).getSubscriptions().get(subscriptionName);
            } catch (PulsarAdminException e) {
                log.error("Failed to get unack messages", e);
            }
        }
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
        return new ConsumeReport(consumerName, uniqueMessages, duplicates, receivedMessages, durationMillis,
                TimeUnit.NANOSECONDS.toMillis(maxLatencyDifferenceNanos), ackAsync, subscriptionStats);
    }
    private record ConsumeReport(String consumerName, int uniqueMessages, int duplicates, RoaringBitmap receivedMessages,
                                 long durationMillis, long maxLatencyDifferenceMillis, boolean ackAsync, SubscriptionStats subscriptionStats) {
    }

    private int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    byte[] intToBytes(final int i) {
        return intToBytes(i, 4);
    }

    byte[] intToBytes(final int i, int messageSize) {
        return ByteBuffer.allocate(Math.max(4, messageSize)).putInt(i).array();
    }

    private ConsumerBuilder<byte[]> createConsumerBuilder(PulsarClient pulsarClient, String topicName,
                                                          String subscriptionName) {
        return pulsarClient.newConsumer()
                .subscriptionName(subscriptionName)
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .subscriptionType(SubscriptionType.Key_Shared)
                .keySharedPolicy(KeySharedPolicy.autoSplitHashRange())
                .topic(topicName);
    }

    public static void main(String[] args) throws Throwable {
        try {
            String namespace = "test_ns" + System.currentTimeMillis();
            if (args.length > 0) {
                namespace = args[0];
            }
            log.info("Using namespace {}", namespace);
            new TestScenarioAckIssue(namespace).run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
