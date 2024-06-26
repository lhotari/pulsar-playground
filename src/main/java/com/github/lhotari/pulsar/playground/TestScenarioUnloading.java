package com.github.lhotari.pulsar.playground;

import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_BROKER_URL;
import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_SERVICE_URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.PulsarVersion;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
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
import org.roaringbitmap.RoaringBitmap;

@Slf4j
public class TestScenarioUnloading {
    private final String namespace;
    private int maxMessages = 10000;
    private int messageSize = 4;

    private int unloadThreadCount = 10;

    private boolean enableBatching = false;

    public TestScenarioUnloading(String namespace) {
        this.namespace = namespace;
    }

    public void run() throws Throwable {
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

        // Unload the topic every ms for 120 seconds
        long stopUnloadingTime = System.currentTimeMillis() + 120000;
        Thread[] unloadingThreads = new Thread[unloadThreadCount];
        Phaser phaser = new Phaser(2);
        Random random = new Random();
        for (int i = 0; i < unloadThreadCount; i++) {
            Thread unloadingThread = new Thread(() -> {
                try (PulsarAdmin admin = PulsarAdmin.builder()
                        .serviceHttpUrl(PULSAR_SERVICE_URL)
                        .build()) {
                    while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() < stopUnloadingTime) {
                        try {
                            Thread.sleep(random.nextInt(7) + 1);
                            phaser.arriveAndAwaitAdvance();
                            log.info("Triggering unload. remaining time: {} s",
                                    (stopUnloadingTime - System.currentTimeMillis()) / 1000);
                            //admin.topics().unload(topicName);
                            admin.namespaces().unload(namespaceName.toString());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (PulsarAdminException e) {
                            String message = e.getMessage();
                            if (message.contains("is being unloaded")) {
                                log.info("Failed to unload namespace. Namespace is being unloaded.");
                            } else if (message.contains("Namespace is not active")) {
                                log.info("Failed to unload namespace. Namespace is not active.");
                            } else if (message.contains("Topic is already fenced")) {
                                log.info("Failed to unload topic. Topic is already fenced.");
                            } else {
                                log.error("Failed to unload topic", e);
                            }
                        }
                    }
                } catch (PulsarClientException e) {
                    throw new RuntimeException(e);
                }
            });
            unloadingThread.start();
            unloadingThreads[i] = unloadingThread;
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
                    .sendTimeout(0, TimeUnit.SECONDS)
                    .create()) {
                AtomicReference<Throwable> sendFailure = new AtomicReference<>();
                for (int i = 1; i <= maxMessages; i++) {
                    Thread.sleep(1L);
                    // add a messages to the topic
                    producer.sendAsync(intToBytes(i, messageSize)).whenComplete((messageId, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to send message to topic {}", topicName, throwable);
                            sendFailure.set(throwable);
                        }
                    });
                    if (i % 100 == 0) {
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
                .ackTimeout(0, TimeUnit.SECONDS)
                .receiverQueueSize(Math.max(maxMessages / 10, 1000))
                .consumerName("consumer")
                .subscribe()) {
            int i = 0;

            while (!Thread.currentThread().isInterrupted()) {
                i++;
                Message<byte[]> msg = consumer.receive(30, TimeUnit.SECONDS);
                if (msg == null) {
                    break;
                }
                int msgNum = bytesToInt(msg.getData());

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
        }

        Arrays.stream(unloadingThreads).forEach(Thread::interrupt);

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
            new TestScenarioUnloading(namespace).run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
