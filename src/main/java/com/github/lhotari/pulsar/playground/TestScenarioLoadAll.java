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
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Reader;
import org.apache.pulsar.client.api.SizeUnit;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.policies.data.Policies;
import org.apache.pulsar.common.policies.data.RetentionPolicies;
import org.roaringbitmap.RoaringBitmap;

@Slf4j
public class TestScenarioLoadAll {
    public TestScenarioLoadAll() {

    }

    public void run() throws Throwable {
        @Cleanup
        PulsarClient pulsarClient = PulsarClient.builder()
                .serviceUrl(PULSAR_BROKER_URL)
                .build();
        @Cleanup
        PulsarAdmin pulsarAdmin = PulsarAdmin.builder()
                .serviceHttpUrl(PULSAR_SERVICE_URL)
                .build();

        for (String tenant : pulsarAdmin.tenants().getTenants()) {
            for (String namespace : pulsarAdmin.namespaces().getNamespaces(tenant)) {
                for (String topic : pulsarAdmin.topics().getList(namespace)) {
                    log.info("Handling topic: {}/{}/{}", tenant, namespace, topic);
                    try (Reader<byte[]> reader = pulsarClient
                            .newReader()
                            .topic(topic)
                            .startMessageId(MessageId.earliest)
                            .create()) {
                        // do nothing
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Throwable {
        try {
            new TestScenarioLoadAll().run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
