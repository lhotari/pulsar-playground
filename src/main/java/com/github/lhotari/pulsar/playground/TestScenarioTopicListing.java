package com.github.lhotari.pulsar.playground;

import io.netty.channel.EventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.PulsarClientImpl;
import org.apache.pulsar.client.impl.conf.ClientConfigurationData;
import org.apache.pulsar.client.util.ExecutorProvider;
import org.apache.pulsar.client.util.ScheduledExecutorProvider;
import org.apache.pulsar.common.util.netty.EventLoopUtil;

import java.util.concurrent.TimeUnit;

import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_BROKER_URL;

@Slf4j
public class TestScenarioTopicListing {
    public static final int NUMBER_OF_CLIENTS = 5000;
    protected final EventLoopGroup ioEventLoopGroup;
    private final ExecutorProvider clientSharedInternalExecutorProvider;
    private final ExecutorProvider clientSharedExternalExecutorProvider;
    private final ScheduledExecutorProvider clientSharedScheduledExecutorProvider;
    private final Timer clientSharedTimer;
    private final ExecutorProvider clientSharedLookupExecutorProvider;

    public TestScenarioTopicListing() {
        this.ioEventLoopGroup = EventLoopUtil.newEventLoopGroup(8, false, new DefaultThreadFactory("pulsar-io"));
        this.clientSharedInternalExecutorProvider =
                new ExecutorProvider(1, "client-shared-internal-executor");
        this.clientSharedExternalExecutorProvider =
                new ExecutorProvider(1, "client-shared-external-executor");
        this.clientSharedScheduledExecutorProvider =
                new ScheduledExecutorProvider(1, "client-shared-scheduled-executor");
        this.clientSharedTimer =
                new HashedWheelTimer(new DefaultThreadFactory("client-shared-timer"), 1, TimeUnit.MILLISECONDS);
        this.clientSharedLookupExecutorProvider =
                new ScheduledExecutorProvider(1, "client-shared-lookup-executor");
    }

    public void run() throws PulsarClientException, PulsarAdminException, InterruptedException {
        for (int i = 0; i < NUMBER_OF_CLIENTS; i++) {
            PulsarClient client = createClientImpl(null, null);
            Consumer<byte[]> consumer = client.newConsumer().subscriptionName("sub" + i).topicsPattern(".*").messageListener(
                    (consumer1, message) -> {
                        log.info("Received from topic: {}", message.getTopicName());
                        try {
                            consumer1.acknowledge(message);
                        } catch (PulsarClientException e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).subscribe();
        }
        log.info("Created {} Pulsar clients with topic listing consumers. Exit with CTRL-C to stop the test.", NUMBER_OF_CLIENTS);
        Thread.currentThread().join();
    }

    public PulsarClientImpl createClientImpl(ClientConfigurationData conf,
                                             java.util.function.Consumer<PulsarClientImpl.PulsarClientImplBuilder> customizer)
            throws PulsarClientException {
        PulsarClientImpl.PulsarClientImplBuilder pulsarClientImplBuilder = PulsarClientImpl.builder()
                .conf(conf != null ? conf : createClientConfigurationData())
                .eventLoopGroup(ioEventLoopGroup)
                .timer(clientSharedTimer)
                .internalExecutorProvider(clientSharedInternalExecutorProvider)
                .externalExecutorProvider(clientSharedExternalExecutorProvider)
                .scheduledExecutorProvider(clientSharedScheduledExecutorProvider)
                .lookupExecutorProvider(clientSharedLookupExecutorProvider);
        if (customizer != null) {
            customizer.accept(pulsarClientImplBuilder);
        }
        return pulsarClientImplBuilder.build();
    }

    private ClientConfigurationData createClientConfigurationData() {
        ClientConfigurationData conf = new ClientConfigurationData();
        conf.setServiceUrl(PULSAR_BROKER_URL);
        return conf;
    }

    public static void main(String[] args) throws PulsarClientException, PulsarAdminException {
        try {
            new TestScenarioTopicListing().run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
