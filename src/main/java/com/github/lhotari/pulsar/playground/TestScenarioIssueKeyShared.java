package com.github.lhotari.pulsar.playground;

import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_BROKER_URL;
import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_SERVICE_URL;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
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
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SizeUnit;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.policies.data.Policies;
import org.apache.pulsar.common.policies.data.RetentionPolicies;
import org.apache.pulsar.common.util.FutureUtil;
import org.roaringbitmap.RoaringBitmap;

@Slf4j
public class TestScenarioIssueKeyShared {
    public static final int RECEIVE_TIMEOUT_SECONDS = 15;
    private final String namespace;
    private int consumerCount = 4;
    private int maxMessages = 1000000;
    private int messageSize = 4;

    private boolean enableBatching = false;

    public TestScenarioIssueKeyShared(String namespace) {
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
            // no retention
            policies.retention_policies = new RetentionPolicies(0, 0);
            pulsarAdmin.namespaces().createNamespace(namespaceName.toString(), policies);
            pulsarAdmin.topics().createNonPartitionedTopic(topicName);
            newTopic = true;
        } catch (PulsarAdminException.ConflictException e) {
            // topic exists, ignore
            log.info("Namespace or Topic exists {}", topicName);
        }

        Thread producerThread = null;
        if (newTopic) {
            try (Consumer<byte[]> consumer = createConsumerBuilder(pulsarClient, topicName).subscribe()) {
                // just to create the subscription
            }
            producerThread = new Thread(() -> {
                Random random = ThreadLocalRandom.current();
                try {
                    produceMessages(pulsarClient, topicName, random);
                } catch (Throwable throwable) {
                    log.error("Failed to produce messages", throwable);
                }
            });
            producerThread.start();
        } else {
            log.info("Attempting to consume remaining messages...");
        }

        List<CompletableFuture<ConsumeReport>> tasks = IntStream.range(1, consumerCount + 1).mapToObj(i -> {
            String consumerName = "consumer" + i;
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return consumeMessages(pulsarClient, topicName, consumerName);
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

        producerThread.join();
        FutureUtil.waitForAll(tasks).get();

        List<ConsumeReport> results =
                tasks.stream().map(CompletableFuture::join)
                        .filter(Objects::nonNull).collect(Collectors.toUnmodifiableList());

        RoaringBitmap joinedReceivedMessages = new RoaringBitmap();
        results.stream().map(ConsumeReport::receivedMessages).forEach(joinedReceivedMessages::or);
        int duplicates = results.stream().mapToInt(ConsumeReport::duplicates).sum();
        int unique = results.stream().mapToInt(ConsumeReport::uniqueMessages).sum();
        int received = joinedReceivedMessages.getCardinality();
        int remaining = maxMessages - received;
        log.info("Done receiving. Remaining: {} duplicates: {} unique: {}", remaining, duplicates, unique);
        if (remaining > 0) {
            log.error("Not all messages received. Remaining: " + remaining);
        }
        if (unique != maxMessages) {
            log.error("Unique message count should match maxMessages!");
        }
    }

    private void produceMessages(PulsarClient pulsarClient, String topicName, Random random) throws Throwable {
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
                byte[] key;
                if (messageSize == 4) {
                    key = value;
                } else {
                    key = intToBytes(i, 4);
                }
                // sleep for 1 ms with 5% probability
                if (random.nextInt(100) < 5) {
                    Thread.sleep(1);
                }
                producer.newMessage().orderingKey(key).value(value)
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

    private ConsumeReport consumeMessages(PulsarClient pulsarClient, String topicName, String consumerName)
            throws PulsarClientException {
//        @Cleanup
//        PulsarClient pulsarClient = PulsarClient.builder()
//                .serviceUrl(PULSAR_BROKER_URL)
//                .memoryLimit(300, SizeUnit.MEGA_BYTES)
//                .build();

        RoaringBitmap receivedMessages = new RoaringBitmap();
        int uniqueMessages = 0;
        int duplicates = 0;

        Random random = ThreadLocalRandom.current();

        try (Consumer<byte[]> consumer = createConsumerBuilder(pulsarClient, topicName)
                .receiverQueueSize(10)
                .consumerName(consumerName)
                .subscribe()) {
            int i = 0;

            while (!Thread.currentThread().isInterrupted()) {
                i++;
                Message<byte[]> msg = consumer.receive(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (msg == null) {
                    break;
                }
                int msgNum = bytesToInt(msg.getData());

                // sleep for a random time with 3% probability
                if (random.nextInt(100) < 3) {
                    try {
                        Thread.sleep(random.nextInt(100) + 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                boolean added = receivedMessages.checkedAdd(msgNum);
                if (added) {
                    uniqueMessages++;
                } else {
                    duplicates++;
                }
                log.info("Received value: {} duplicate: {} unique: {} duplicates: {}", msgNum, !added, uniqueMessages,
                        duplicates);
                consumer.acknowledgeAsync(msg);
            }
        }
        return new ConsumeReport(uniqueMessages, duplicates, receivedMessages);
    }
    private record ConsumeReport(int uniqueMessages, int duplicates, RoaringBitmap receivedMessages) {
    }

    private int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    byte[] intToBytes(final int i, int messageSize) {
        return ByteBuffer.allocate(Math.max(4, messageSize)).putInt(i).array();
    }

    private ConsumerBuilder<byte[]> createConsumerBuilder(PulsarClient pulsarClient, String topicName) {
        return pulsarClient.newConsumer()
                .subscriptionName("sub")
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .subscriptionType(SubscriptionType.Key_Shared)
                .topic(topicName);
    }

    public static void main(String[] args) throws Throwable {
        try {
            String namespace = "test_ns" + System.currentTimeMillis();
            if (args.length > 0) {
                namespace = args[0];
            }
            log.info("Using namespace {}", namespace);
            new TestScenarioIssueKeyShared(namespace).run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
