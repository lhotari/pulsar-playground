package com.github.lhotari.pulsar.playground;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
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
public class TestScenarioIssue10813 {
    private static final String PULSAR_HOST = System.getenv().getOrDefault("PULSAR_HOST",
            // deployed with https://github.com/lhotari/pulsar-playground/tree/master/test-env
            "pulsar-testenv-deployment-broker.pulsar-testenv.svc.cluster.local");
    private static final String PULSAR_SERVICE_URL =
            System.getenv().getOrDefault("PULSAR_SERVICE_URL", "http://" + PULSAR_HOST + ":8080/");
    private static final String PULSAR_BROKER_URL =
            System.getenv().getOrDefault("PULSAR_BROKER_URL", "pulsar://" + PULSAR_HOST + ":6650/");

    private final String namespace;
    private int maxMessages = 100000;

    public TestScenarioIssue10813(String namespace) {
        this.namespace = namespace;
    }

    public void run() throws Throwable {
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
            pulsarAdmin.topics().createPartitionedTopic(topicName, 30);
            newTopic = true;
        } catch (PulsarAdminException.ConflictException e) {
            // topic exists, ignore
            log.info("Namespace or Topic exists {}", topicName);
        }

        if (newTopic) {
            try (Consumer<byte[]> consumer = createConsumer(pulsarClient, topicName)) {
                // just to create the subscription
            }
            try (Producer<byte[]> producer = pulsarClient.newProducer()
                    .topic(topicName)
                    .enableBatching(true)
                    .blockIfQueueFull(true)
                    .create()) {
                AtomicReference<Throwable> sendFailure = new AtomicReference<>();
                for (int i = 0; i < maxMessages; i++) {
                    // add a messages to the topic
                    producer.sendAsync(intToBytes(i)).whenComplete((messageId, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to send message to topic {}", topicName, throwable);
                            sendFailure.set(throwable);
                        }
                    });
                    if ((i + 1) % 1000 == 0) {
                        log.info("Sent {} msgs", i + 1);
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

        try (Consumer<byte[]> consumer = createConsumer(pulsarClient, topicName)) {
            for (int i = 0; i < maxMessages; i++) {
                Message<byte[]> msg = consumer.receive();
                int msgNum = bytesToInt(msg.getData());
                log.info("Received {} remaining: {}", msgNum, --remainingMessages);
                consumer.acknowledge(msg);
                if ((i + 1) % reportingInterval == 0) {
                    log.info("Received {} msgs", i + 1);
                }
            }
        }
        log.info("Done receiving.");
    }

    private int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    byte[] intToBytes(final int i) {
        return ByteBuffer.allocate(4).putInt(i).array();
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
            String namespace = "test_ns" + System.currentTimeMillis();
            if (args.length > 0) {
                namespace = args[0];
            }
            log.info("Using namespace {}", namespace);
            new TestScenarioIssue10813(namespace).run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
