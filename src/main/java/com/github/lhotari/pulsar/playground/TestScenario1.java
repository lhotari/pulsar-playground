package com.github.lhotari.pulsar.playground;

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
    private static final String PULSAR_IP = System.getenv().getOrDefault("PULSAR_IP", "10.64.140.45");

    public void run() throws PulsarClientException, PulsarAdminException {
        PulsarAdmin pulsarAdmin = PulsarAdmin.builder()
                .serviceHttpUrl("http://" + PULSAR_IP)
                .build();

        NamespaceName namespace = NamespaceName.get("pulsar", "test_ns" + System.currentTimeMillis());
        Policies policies = new Policies();
        policies.retention_policies = new RetentionPolicies(-1, -1);
        policies.autoTopicCreationOverride = new AutoTopicCreationOverride(false, null, null);
        pulsarAdmin.namespaces().createNamespace(namespace.toString(), policies);

        for (int i = 0; i < 10000; i++) {
            String topicName = namespace.getPersistentTopicName("topic" + i);
            log.info("Creating {}", topicName);
            pulsarAdmin.topics().createNonPartitionedTopic(topicName);
        }

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
