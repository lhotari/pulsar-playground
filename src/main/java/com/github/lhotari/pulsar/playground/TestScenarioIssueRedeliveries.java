package com.github.lhotari.pulsar.playground;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.PulsarVersion;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.SizeUnit;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.policies.data.Policies;
import org.apache.pulsar.common.policies.data.RetentionPolicies;
import org.roaringbitmap.RoaringBitmap;

@Slf4j
public class TestScenarioIssueRedeliveries {
    private static final String PULSAR_HOST = System.getenv().getOrDefault("PULSAR_HOST",
            "localhost");
    private static final String PULSAR_SERVICE_URL =
            System.getenv().getOrDefault("PULSAR_SERVICE_URL", "http://" + PULSAR_HOST + ":8080/");
    private static final String PULSAR_BROKER_URL =
            System.getenv().getOrDefault("PULSAR_BROKER_URL", "pulsar://" + PULSAR_HOST + ":6650/");

    private final String namespace;
    private int maxMessages = 1000000;
    private int messageSize = 4;

    private boolean enableBatching = true;

    private boolean enableRetry = false;

    public TestScenarioIssueRedeliveries(String namespace) {
        this.namespace = namespace;
    }

    public void run() throws Throwable {
        PulsarClient pulsarClient = PulsarClient.builder()
                .serviceUrl(PULSAR_BROKER_URL)
                .memoryLimit(300, SizeUnit.MEGA_BYTES)
                .build();

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

        if (newTopic) {
            try (Consumer<byte[]> consumer = createConsumerBuilder(pulsarClient, topicName).subscribe()) {
                // just to create the subscription
            }
            try (Producer<byte[]> producer = pulsarClient.newProducer()
                    .topic(topicName)
                    .enableBatching(enableBatching)
                    .batchingMaxMessages(Math.max(50, maxMessages / 10000))
                    .blockIfQueueFull(true)
                    .create()) {
                AtomicReference<Throwable> sendFailure = new AtomicReference<>();
                for (int i = 1; i <= maxMessages; i++) {
                    // add a messages to the topic
                    producer.sendAsync(intToBytes(i, messageSize)).whenComplete((messageId, throwable) -> {
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
        } else {
            log.info("Attempting to consume remaining messages...");
        }

        int reportingInterval = newTopic ? 1000 : 1;

        int remainingMessages = maxMessages;
        RoaringBitmap receivedMessages = new RoaringBitmap();
        int duplicates = 0;
        int reconsumed = 0;

        try (Consumer<byte[]> consumer = createConsumerBuilder(pulsarClient, topicName)
                .enableRetry(enableRetry)
                .ackTimeout(5, TimeUnit.SECONDS)
                .negativeAckRedeliveryDelay(5, TimeUnit.SECONDS)
                .batchReceivePolicy(BatchReceivePolicy.DEFAULT_POLICY)
                .deadLetterPolicy(DeadLetterPolicy.builder().maxRedeliverCount(Integer.MAX_VALUE).build())
                .receiverQueueSize(Math.max(maxMessages / 10, 1000))
                .consumerName("consumer")
                .subscribe()) {
            int i = 0;

            Thread triggerReliveryThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(500);
                        log.info("Triggering redeliverUnacknowledgedMessages");
                        consumer.redeliverUnacknowledgedMessages();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            triggerReliveryThread.start();

            while (!Thread.currentThread().isInterrupted()) {
                i++;
                Message<byte[]> msg = consumer.receive(20, TimeUnit.SECONDS);
                if (msg == null) {
                    break;
                }
                int msgNum = bytesToInt(msg.getData());

                int mod100 = i % 100;

                if (enableRetry) {
                    // reconsume about 5% of the messages
                    if (mod100 == 2 || mod100 == 5 || mod100 == 11 || mod100 == 17 || mod100 == 23) {
                        reconsumed++;
                        log.info("Reconsuming {} msgNum: {} reconsumed: {}", i, msgNum, reconsumed);
                        consumer.reconsumeLater(msg, 5, TimeUnit.SECONDS);
                        continue;
                    }
                }

                // nack about 5% of the messages
                if (mod100 == 3 || mod100 == 7 || mod100 == 13 || mod100 == 19 || mod100 == 29) {
                    reconsumed++;
                    log.info("Nacking {} msgNum: {} reconsumed: {}", i, msgNum, reconsumed);
                    consumer.negativeAcknowledge(msg);
                    continue;
                }

                boolean added = receivedMessages.checkedAdd(msgNum);
                if (added) {
                    --remainingMessages;
                } else {
                    duplicates++;
                }
                log.info("Received {} duplicate: {} remaining: {}", msgNum, !added, remainingMessages);
                consumer.acknowledge(msg);
                if (i % reportingInterval == 0) {
                    log.info("Received {} msgs. remaining: {} duplicates: {}", i, remainingMessages, duplicates);
                }
            }

            triggerReliveryThread.interrupt();
        }
        log.info("Done receiving. Remaining: {} duplicates: {} reconsumed: {}", remainingMessages, duplicates,
                reconsumed);
        if (remainingMessages > 0) {
            log.error("Not all messages received. Remaining: " + remainingMessages);
        }
        log.info("Pulsar client version: {} {} {} {}", PulsarVersion.getVersion(), PulsarVersion.getGitBranch(),
                PulsarVersion.getGitSha(), PulsarVersion.getBuildTime());
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
                .subscriptionType(SubscriptionType.Shared)
                .topic(topicName);
    }

    public static void main(String[] args) throws Throwable {
        try {
            String namespace = "test_ns" + System.currentTimeMillis();
            if (args.length > 0) {
                namespace = args[0];
            }
            log.info("Using namespace {}", namespace);
            new TestScenarioIssueRedeliveries(namespace).run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
