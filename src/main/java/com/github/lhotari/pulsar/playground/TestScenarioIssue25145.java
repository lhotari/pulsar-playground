package com.github.lhotari.pulsar.playground;

import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_BROKER_URL;
import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_SERVICE_URL;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Cleanup;
import lombok.SneakyThrows;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;

/**
 * Attempt to reproduce an issue described in https://github.com/apache/pulsar/issues/25145.
 * This class is based on PulsarBatchAckPseudoDemo shared in the issue description.
 */
public class TestScenarioIssue25145 {
    static final String TOPIC_NAME = "partitioned_topic" + System.currentTimeMillis();
    static Set<MessageId> sentMessageIds = ConcurrentHashMap.newKeySet();
    static Map<MessageId, Set<String>> receiptTracker = new ConcurrentHashMap<>();
    static Map<String, AtomicLong> ackCounters = Map.of("sub-1", new AtomicLong(0), "sub-2", new AtomicLong(0));

    public static void main(String[] args) throws PulsarClientException, InterruptedException, PulsarAdminException {
        PulsarClient client = PulsarClient.builder()
                .serviceUrl(PULSAR_BROKER_URL)
                .build();

        @Cleanup
        PulsarAdmin pulsarAdmin = PulsarAdmin.builder()
                .serviceHttpUrl(PULSAR_SERVICE_URL)
                .build();

        try {
            pulsarAdmin.topics().createPartitionedTopic(TOPIC_NAME, 16);
        } catch (PulsarAdminException.ConflictException e) {
            // Topic already exists, ignore.
        }

        // Run producer and consumers concurrently in background threads.
        Thread producerThread = startThread(() -> producerTask(client));
        Thread consumerThread1 = startThread(() -> consumerTask(client, "sub-1"));
        Thread consumerThread2 = startThread(() -> consumerTask(client, "sub-2"));

        // wait until all threads complete
        producerThread.join();
        consumerThread1.join();
        consumerThread2.join();

        // --- Verification ---
        System.out.println("--- Total Acked Messages per Subscription ---");
        System.out.printf("Subscription [sub-1]: %d acks%n", ackCounters.get("sub-1").get());
        System.out.printf("Subscription [sub-2]: %d acks%n", ackCounters.get("sub-2").get());

        boolean inconsistencyFound = false;
        // Find and report any message that wasn't received by BOTH subscriptions.
        for (MessageId sentId : sentMessageIds) {
            Set<String> receivedBy = receiptTracker.getOrDefault(sentId, Collections.emptySet());
            if (receivedBy.size() < 2) {
                inconsistencyFound = true;
                if (!receivedBy.contains("sub-1")) {
                    System.err.printf("[%s] not received from [sub-1]!%n", sentId);
                }
                if (!receivedBy.contains("sub-2")) {
                    System.err.printf("[%s] not received from [sub-2]!%n", sentId);
                }
            }
        }

        System.out.println("Shutting down...");
        client.close();
        System.out.println("Done.");
        if (inconsistencyFound) {
            System.err.println("Inconsistency detected!");
            System.exit(1);
        }
    }

    private static Thread startThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.start();
        return thread;
    }

    // --- Producer Task Logic ---
    @SneakyThrows
    static void producerTask(PulsarClient client) {
        System.out.println("Starting producer task...");
        Producer<String> producer = client.newProducer(Schema.STRING)
                .topic(TOPIC_NAME)
                .batchingMaxMessages(1000)
                .create();

        for (int i = 0; i < 1_000_000; i++) {
            // Asynchronously send and register the MessageId upon completion.
            producer.sendAsync("message-payload-" + i).thenAccept(messageId -> {
                sentMessageIds.add(messageId);
            });
        }
        producer.flush();
        System.out.println("Finished producer task.");
    }

    // --- Consumer Task Logic ---
    @SneakyThrows
    static void consumerTask(PulsarClient client, String subscriptionName) {
        System.out.println("Starting consumer task for subscription [" + subscriptionName + "]...");
        Consumer consumer = client.newConsumer().topic(TOPIC_NAME)
                .subscriptionName(subscriptionName)
                .enableBatchIndexAcknowledgment(true)
                .acknowledgmentGroupTime(100, TimeUnit.MILLISECONDS)
                .maxAcknowledgmentGroupSize(1000)
                .subscribe();

        try {
            while (true) { // Run until the main thread stops it.
                Message<String> message = consumer.receive(10, TimeUnit.SECONDS);
                if (message == null) {
                    break;
                }

                // Step 1: Mark as received BEFORE acknowledging.
                Set<String> receipts =
                        receiptTracker.computeIfAbsent(message.getMessageId(), k -> ConcurrentHashMap.newKeySet());
                receipts.add(subscriptionName);

                // Step 2: Increment the counter for the final report.
                ackCounters.get(subscriptionName).incrementAndGet();

                // Step 3: Acknowledge the message individually.
                consumer.acknowledge(message);
            }
        } catch (PulsarClientException e) {
            if (!(e.getCause() instanceof InterruptedException)) {
                throw e;
            }
        }

        System.out.println("Finished consumer task for subscription [" + subscriptionName + "].");
    }
}