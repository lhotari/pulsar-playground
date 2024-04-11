package com.github.lhotari.pulsar.playground;

import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_BROKER_URL;
import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_SERVICE_URL;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.CheckedFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.policies.data.AutoTopicCreationOverride;
import org.apache.pulsar.common.policies.data.Policies;
import org.apache.pulsar.common.policies.data.RetentionPolicies;

@Slf4j
public class TestScenario1 {
    public void run() throws PulsarClientException, PulsarAdminException {
        PulsarClient pulsarClient = PulsarClient.builder()
                .serviceUrl(PULSAR_BROKER_URL)
                .build();

        PulsarAdmin pulsarAdmin = PulsarAdmin.builder()
                .serviceHttpUrl(PULSAR_SERVICE_URL)
                .build();

        NamespaceName namespace = NamespaceName.get("public", "test_ns" + System.currentTimeMillis());
        Policies policies = new Policies();
        policies.retention_policies = new RetentionPolicies(-1, -1);
        policies.autoTopicCreationOverride = AutoTopicCreationOverride.builder()
                .allowAutoTopicCreation(false)
                .build();
        pulsarAdmin.namespaces().createNamespace(namespace.toString(), policies);

        CheckedFunction<String, Void> createTopicFunction = createCreateTopicFunctionWithRetries(pulsarAdmin);

        for (int i = 0; i < 10000; i++) {
            String topicName = namespace.getPersistentTopicName("topic" + i);
            try {
                createTopicFunction.apply(topicName);
                // add a message to the topic
                try (Producer<String> producer = pulsarClient.newProducer(Schema.STRING)
                        .topic(topicName)
                        .create()) {
                    producer.send("Hello world!");
                }
            } catch (Throwable throwable) {
                log.error("Failed to create topic {} after retries", topicName, throwable);
            }
        }

    }

    private CheckedFunction<String, Void> createCreateTopicFunctionWithRetries(PulsarAdmin pulsarAdmin) {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(5)
                .intervalFunction(IntervalFunction.ofExponentialBackoff())
                .build();
        Retry retry = Retry.of("createNonPartitionedTopic", retryConfig);
        CheckedFunction<String, Void> createTopicFunction =
                Retry.<String, Void>decorateCheckedFunction(retry, topicName -> {
                    log.info("Creating {}", topicName);
                    pulsarAdmin.topics().createNonPartitionedTopic(topicName);
                    return null;
                });
        return createTopicFunction;
    }

    public static void main(String[] args) throws PulsarClientException, PulsarAdminException {
        try {
            new TestScenario1().run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
