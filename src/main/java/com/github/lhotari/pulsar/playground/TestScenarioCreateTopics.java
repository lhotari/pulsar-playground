package com.github.lhotari.pulsar.playground;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.policies.data.AutoTopicCreationOverride;
import org.apache.pulsar.common.policies.data.Policies;
import org.apache.pulsar.common.policies.data.RetentionPolicies;
import org.apache.pulsar.common.policies.data.TenantInfo;

@Slf4j
public class TestScenarioCreateTopics {
    private static final String PULSAR_SERVICE_URL =
            System.getenv().getOrDefault("PULSAR_SERVICE_URL",
                    "http://pulsar-testenv-pulsar-broker.pulsar-testenv.svc.cluster.local:8080");

    @Parameter(names = {"--admin-url"},
            description = "Admin Service URL to which to connect.\n")
    String adminUrl = PULSAR_SERVICE_URL;

    @Parameter(names = {"--tenant-prefix"},
            description = "Tenant prefix")
    String tenantPrefix = "tenant";

    @Parameter(names = {"--tenants"},
            description = "Number of tenants")
    int numberOfTenants = 10;
    @Parameter(names = {"--namespaces"},
            description = "Number of namespaces")
    int numberOfNamespaces = 10;
    @Parameter(names = {"--topics"},
            description = "Number of topics")
    int numberOfTopics = 10;

    @Parameter(names = {"-h", "--help"},
            description = "Help",
            help = true)
    private boolean help = false;

    public void run() throws PulsarClientException, PulsarAdminException {
        PulsarAdmin pulsarAdmin = PulsarAdmin.builder()
                .serviceHttpUrl(adminUrl)
                .build();

        TenantInfo tenantInfo = createTenantInfo(pulsarAdmin);
        Policies policies = createPolicies();

        for (int i = 0; i < numberOfTenants; i++) {
            String tenantName = String.format(tenantPrefix + "%03d", (i + 1));
            log.info("Creating tenant {}", tenantName);
            pulsarAdmin.tenants().createTenant(tenantName, tenantInfo);
            for (int j = 0; j < numberOfNamespaces; j++) {
                String namespacePart = String.format("namespace%03d", (j + 1));
                NamespaceName namespace = NamespaceName.get(tenantName, namespacePart);
                log.info("Creating namespace {}", namespace);
                pulsarAdmin.namespaces().createNamespace(namespace.toString(), policies);
                for (int k = 0; k < numberOfTopics; k++) {
                    String topicName = namespace.getPersistentTopicName(String.format("topic%03d", (k + 1)));
                    log.info("Creating {}", topicName);
                    pulsarAdmin.topics().createNonPartitionedTopic(topicName);
                }
            }
        }
    }

    private static Policies createPolicies() {
        Policies policies = new Policies();
        policies.retention_policies = new RetentionPolicies(-1, -1);
        policies.autoTopicCreationOverride = AutoTopicCreationOverride.builder()
                .allowAutoTopicCreation(false)
                .build();
        return policies;
    }

    private static TenantInfo createTenantInfo(PulsarAdmin pulsarAdmin) throws PulsarAdminException {
        Set<String> clusters = new HashSet<>(pulsarAdmin.clusters().getClusters());
        return TenantInfo.builder()
                .allowedClusters(clusters)
                .adminRoles(Collections.emptySet())
                .build();
    }

    public static void main(String[] args) {
        try {
            TestScenarioCreateTopics scenario = new TestScenarioCreateTopics();
            JCommander jc = JCommander.newBuilder()
                    .addObject(scenario)
                    .build();
            jc.parse(args);
            if (scenario.help) {
                jc.usage();
                return;
            }
            scenario.run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
