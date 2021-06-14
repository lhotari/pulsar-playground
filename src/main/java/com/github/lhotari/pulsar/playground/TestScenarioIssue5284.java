package com.github.lhotari.pulsar.playground;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.policies.data.Policies;
import org.apache.pulsar.common.policies.data.RetentionPolicies;

@Slf4j
public class TestScenarioIssue5284 implements Runnable {
    private static final String PULSAR_HOST = System.getenv().getOrDefault("PULSAR_HOST",
            // deployed with https://github.com/lhotari/pulsar-playground/tree/master/test-env
            "pulsar-testenv-deployment-proxy.pulsar-testenv.svc.cluster.local");
    private static final String PULSAR_SERVICE_URL =
            System.getenv().getOrDefault("PULSAR_SERVICE_URL", "http://" + PULSAR_HOST + ":8080/");
    private static final String PULSAR_BROKER_URL =
            System.getenv().getOrDefault("PULSAR_BROKER_URL", "pulsar://" + PULSAR_HOST + ":6650/");

    private final int instanceId;
    private final String namespace;
    private int maxMessages = 100000;
    private int partitions = 10;
    private int messageSize = 20000;
    private int maxPendingMessages = 100;
    private int maxPendingMessagesAcrossPartitions = 200;

    public TestScenarioIssue5284(int instanceId) {
        this.instanceId = instanceId;
        this.namespace = "test_ns_" + instanceId + "_" + System.currentTimeMillis();
    }

    @Override
    public void run() {
        try {
            doRun();
        } catch (Throwable throwable) {
            log.error("[{}] Problem in test run", instanceId, throwable);
        }
    }

    private void doRun() throws Throwable {
        PulsarClient pulsarClient = PulsarClient.builder()
                .serviceUrl(PULSAR_BROKER_URL)
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
            pulsarAdmin.topics().createPartitionedTopic(topicName, partitions);
            newTopic = true;
        } catch (PulsarAdminException.ConflictException e) {
            // topic exists, ignore
            log.info("[{}] Namespace or Topic exists {}", instanceId, topicName);
        }

        if (newTopic) {
            try (Consumer<byte[]> consumer = createConsumer(pulsarClient, topicName)) {
                // just to create the subscription
            }
            try (Producer<byte[]> producer = pulsarClient.newProducer()
                    .topic(topicName)
                    .enableBatching(true)
                    .blockIfQueueFull(true)
                    .maxPendingMessages(maxPendingMessages)
                    .maxPendingMessagesAcrossPartitions(maxPendingMessagesAcrossPartitions)
                    .create()) {
                AtomicReference<Throwable> sendFailure = new AtomicReference<>();
                for (int i = 0; i < maxMessages; i++) {
                    // add a messages to the topic
                    producer.sendAsync(intToBytes(i, messageSize)).whenComplete((messageId, throwable) -> {
                        if (throwable != null) {
                            log.error("[{}] Failed to send message to topic {}", instanceId, topicName, throwable);
                            sendFailure.set(throwable);
                        }
                    });
                    if ((i + 1) % 1000 == 0) {
                        log.info("[{}] Sent {} msgs", instanceId, i + 1);
                    }
                    Throwable throwable = sendFailure.get();
                    if (throwable != null) {
                        throw throwable;
                    }
                }
                log.info("[{}] Flushing", instanceId);
                producer.flush();
            }
            log.info("[{}] Done sending.", instanceId);
        } else {
            log.info("[{}] Attempting to consume remaining messages...", instanceId);
        }

        int reportingInterval = newTopic ? 1000 : 1;

        int remainingMessages = maxMessages;

        try (Consumer<byte[]> consumer = createConsumer(pulsarClient, topicName)) {
            for (int i = 0; i < maxMessages; i++) {
                Message<byte[]> msg = consumer.receive();
                int msgNum = bytesToInt(msg.getData());
                log.info("[{}] Received {} remaining: {}", instanceId, msgNum, --remainingMessages);
                consumer.acknowledge(msg);
                if ((i + 1) % reportingInterval == 0) {
                    log.info("[{}] Received {} msgs", instanceId, i + 1);
                }
            }
        }
        log.info("[{}] Done receiving.", instanceId);
    }

    private int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    byte[] intToBytes(final int i, int messageSize) {
        return ByteBuffer.allocate(Math.max(4, messageSize)).putInt(i).array();
    }

    private Consumer<byte[]> createConsumer(PulsarClient pulsarClient, String topicName) throws PulsarClientException {
        Consumer<byte[]> consumer = pulsarClient.newConsumer()
                .subscriptionName("sub")
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .subscriptionType(SubscriptionType.Shared)
                .topic(topicName)
                .subscribe();
        return consumer;
    }


    public static void main(String[] args) throws Throwable {
        try {
            ExecutorService executorService = Executors.newCachedThreadPool();
            int concurrency = 10;
            List<? extends Future<?>> futures = IntStream.range(1, concurrency + 1)
                    .mapToObj(i -> executorService.submit(new TestScenarioIssue5284(i)))
                    .collect(Collectors.toList());
            for (Future<?> future : futures) {
                future.get();
            }
            executorService.shutdown();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
