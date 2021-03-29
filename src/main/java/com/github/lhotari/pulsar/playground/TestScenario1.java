package com.github.lhotari.pulsar.playground;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.CheckedFunction1;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.policies.data.AutoTopicCreationOverride;
import org.apache.pulsar.common.policies.data.Policies;
import org.apache.pulsar.common.policies.data.RetentionPolicies;

@Slf4j
public class TestScenario1 {
    private static final String PULSAR_SERVICE_URL = System.getenv().getOrDefault("PULSAR_SERVICE_URL", "http://pulsar-proxy.pulsar.svc.cluster.local:8080");

    public void run() throws PulsarClientException, PulsarAdminException {
        PulsarAdmin pulsarAdmin = PulsarAdmin.builder()
                .serviceHttpUrl(PULSAR_SERVICE_URL)
                .build();

        NamespaceName namespace = NamespaceName.get("public", "test_ns" + System.currentTimeMillis());
        Policies policies = new Policies();
        policies.retention_policies = new RetentionPolicies(-1, -1);
        policies.autoTopicCreationOverride = new AutoTopicCreationOverride(false, null, null);
        pulsarAdmin.namespaces().createNamespace(namespace.toString(), policies);

        CheckedFunction1<String, Void> createTopicFunction = createCreateTopicFunctionWithRetries(pulsarAdmin);

        for (int i = 0; i < 10000; i++) {
            String topicName = namespace.getPersistentTopicName("topic" + i);
            try {
                createTopicFunction.apply(topicName);
            } catch (Throwable throwable) {
                log.error("Failed to create topic {} after retries", topicName, throwable);
            }
        }

    }

    private CheckedFunction1<String, Void> createCreateTopicFunctionWithRetries(PulsarAdmin pulsarAdmin) {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(5)
                .intervalFunction(IntervalFunction.ofExponentialBackoff())
                .build();
        Retry retry = Retry.of("createNonPartitionedTopic", retryConfig);
        CheckedFunction1<String, Void> createTopicFunction =
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
