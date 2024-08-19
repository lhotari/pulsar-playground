package com.github.lhotari.pulsar.playground;

import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_BROKER_URL;
import static com.github.lhotari.pulsar.playground.TestEnvironment.PULSAR_SERVICE_URL;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.PulsarVersion;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.BatcherBuilder;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.KeySharedPolicy;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SizeUnit;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.policies.data.DelayedDeliveryPolicies;
import org.apache.pulsar.common.policies.data.PersistentTopicInternalStats;
import org.apache.pulsar.common.policies.data.Policies;
import org.apache.pulsar.common.policies.data.RetentionPolicies;
import org.apache.pulsar.common.policies.data.TopicStats;
import org.apache.pulsar.common.util.FutureUtil;
import org.apache.pulsar.common.util.ObjectMapperFactory;
import org.roaringbitmap.RoaringBitmap;

@Slf4j
public class TestScenarioKeySharedReconnects {
    public static final int RECEIVE_TIMEOUT_SECONDS = 15;
    private final String namespace;
    private int consumerCount = 4;
    private int maxMessages = 1000000;
    private int messageSize = 4;

    private boolean enableBatching = false;
    private AtomicInteger messagesInFlight = new AtomicInteger();
    private int targetMessagesInFlight = maxMessages / 20;
    private AtomicInteger maxAckHoles = new AtomicInteger();
    private volatile AckHoleReport ackHoleReport;
    private long minimumConnectTimeMillis = 2000;

    public TestScenarioKeySharedReconnects(String namespace) {
        this.namespace = namespace;
    }

    record AckHoleReport(int maxAckHoles, TopicStats topicStats, PersistentTopicInternalStats internalStats) {
    }

    public void run() throws Throwable {
        log.info("Pulsar client version: {} {} {} {}", PulsarVersion.getVersion(), PulsarVersion.getGitBranch(),
                PulsarVersion.getGitSha(), PulsarVersion.getBuildTime());

        log.info("Waiting 2 seconds before starting...");
        Thread.sleep(2000L);

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
            policies.delayed_delivery_policies = DelayedDeliveryPolicies.builder()
                    .active(false)
                    .build();
            pulsarAdmin.namespaces().createNamespace(namespaceName.toString(), policies);
            pulsarAdmin.topics().createNonPartitionedTopic(topicName);
            newTopic = true;
        } catch (PulsarAdminException.ConflictException e) {
            // topic exists, ignore
            log.info("Namespace or Topic exists {}", topicName);
        }

        Thread producerThread = null;
        if (newTopic) {
            try (Consumer<byte[]> consumer = createConsumerBuilder(pulsarClient, topicName).subscribe()) {
                // just to create the subscription
            }
            producerThread = new Thread(() -> {
                try {
                    produceMessages(pulsarClient, topicName);
                } catch (Throwable throwable) {
                    log.error("Failed to produce messages", throwable);
                }
            });
            producerThread.start();
        } else {
            log.info("Attempting to consume remaining messages...");
        }

        Thread ackHoleMonitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    TopicStats stats = pulsarAdmin.topics().getStats(topicName);
                    int ackHoles = stats.getSubscriptions().values().stream()
                            .mapToInt(subscriptionStats -> subscriptionStats.getNonContiguousDeletedMessagesRanges())
                            .max().orElse(0);
                    if (ackHoles > 0) {
                        log.info("Ack holes: {}", ackHoles);
                        int maxValue = maxAckHoles.updateAndGet(currentValue -> Math.max(currentValue, ackHoles));
                        if (ackHoles == maxValue) {
                            ackHoleReport = new AckHoleReport(ackHoles, stats,
                                    pulsarAdmin.topics().getInternalStats(topicName));
                        }
                    }
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (PulsarAdminException e) {
                    log.error("Failed to get ack holes", e);
                }
            }
        });
        ackHoleMonitorThread.start();

        List<CompletableFuture<ConsumeReport>> tasks = IntStream.range(1, consumerCount + 1).mapToObj(i -> {
            String consumerName = "consumer" + i;
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return consumeMessages(topicName, consumerName, i % consumerCount == 0);
                } catch (PulsarClientException e) {
                    log.error("Failed to consume messages", e);
                    return null;
                }
            }, runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName(consumerName);
                thread.start();
            });
        }).collect(Collectors.toUnmodifiableList());

        producerThread.join();
        FutureUtil.waitForAll(tasks).get();

        List<ConsumeReport> results =
                tasks.stream().map(CompletableFuture::join)
                        .filter(Objects::nonNull).collect(Collectors.toUnmodifiableList());

        RoaringBitmap joinedReceivedMessages = new RoaringBitmap();
        results.stream().map(ConsumeReport::receivedMessages).forEach(joinedReceivedMessages::or);
        int duplicates = results.stream().mapToInt(ConsumeReport::duplicates).sum();
        int unique = results.stream().mapToInt(ConsumeReport::uniqueMessages).sum();
        int received = joinedReceivedMessages.getCardinality();
        int remaining = maxMessages - received;
        long maxLatencyIncreaseMillis =
                results.stream().mapToLong(ConsumeReport::maxLatencyDifferenceMillis).max().orElse(0);

        ackHoleMonitorThread.interrupt();
        ackHoleMonitorThread.join();

        log.info("Done receiving. Remaining: {} duplicates: {} unique: {}\n"
                        + "max latency difference of subsequent messages: {} ms\n"
                        + "max ack holes: {}",
                remaining, duplicates, unique,
                maxLatencyIncreaseMillis,
                maxAckHoles.get());
        if (remaining > 0) {
            log.error("Not all messages received. Remaining: " + remaining);
        }
        if (unique != maxMessages) {
            log.error("Unique message count should match maxMessages!");
        }
        results.stream().sorted(Comparator.comparing(ConsumeReport::consumerName))
                .forEach(report ->
                        log.info(
                                "Consumer {} received {} unique messages {} duplicates in {} s, max latency "
                                        + "difference of subsequent messages {} ms, connect count: {}",
                                report.consumerName(), report.uniqueMessages(), report.duplicates(),
                                TimeUnit.MILLISECONDS.toSeconds(report.durationMillis()),
                                report.maxLatencyDifferenceMillis(), report.connectCount()));

        File statsFile = File.createTempFile("stats", ".json");
        writeJsonToFile(statsFile, pulsarAdmin.topics().getStats(topicName));
        log.info("Wrote topic stats to {}", statsFile);
        File internalStatsFile = File.createTempFile("internalStats", ".json");
        writeJsonToFile(internalStatsFile, pulsarAdmin.topics().getInternalStats(topicName, true));
        log.info("Wrote internal stats to {}", internalStatsFile);
    }

    private void writeJsonToFile(File jsonFile, Object object) throws IOException {
        ObjectWriter writer = ObjectMapperFactory.getMapper().writer();
        try (JsonGenerator generator = writer.createGenerator(jsonFile, JsonEncoding.UTF8).useDefaultPrettyPrinter()) {
            generator.writeObject(object);
        }
    }

    private void produceMessages(PulsarClient pulsarClient, String topicName) throws Throwable {
        try (Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topicName)
                .enableBatching(enableBatching)
                .batcherBuilder(BatcherBuilder.KEY_BASED)
                .batchingMaxMessages(Math.max(50, maxMessages / 10000))
                .blockIfQueueFull(true)
                .create()) {
            AtomicReference<Throwable> sendFailure = new AtomicReference<>();
            for (int i = 1; i <= maxMessages; i++) {
                byte[] value = intToBytes(i, messageSize);
                byte[] key;
                if (messageSize == 4) {
                    key = value;
                } else {
                    key = intToBytes(i, 4);
                }
                producer.newMessage().orderingKey(key).value(value)
                        // set System.nanoTime() as event time
                        .eventTime(System.nanoTime())
                        .sendAsync().whenComplete((messageId, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to send message to topic {}", topicName, throwable);
                        sendFailure.set(throwable);
                    }
                });
                int currentMessagesInFlight = messagesInFlight.incrementAndGet();
                while (currentMessagesInFlight > targetMessagesInFlight) {
                    // throttling
                    Thread.sleep(100);
                    currentMessagesInFlight = messagesInFlight.get();
                }
                if (i % 1000 == 0) {
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
    }

    private ConsumeReport consumeMessages(String topicName, String consumerName, boolean simulateReconnecting)
            throws PulsarClientException {

        HandlingState state = new HandlingState();
        Random random = ThreadLocalRandom.current();
        long startTimeNanos = System.nanoTime();
        int connectCount = 0;
        boolean keepConnected = true;

        while (keepConnected) {
            try (PulsarClient pulsarClient = PulsarClient.builder()
                    .serviceUrl(PULSAR_BROKER_URL)
                    .memoryLimit(300, SizeUnit.MEGA_BYTES)
                    .build()) {

                keepConnected = false;

                long minimumConnectTimeNanosWithJitter =
                        TimeUnit.MILLISECONDS.toNanos(
                                this.minimumConnectTimeMillis + random.nextLong(this.minimumConnectTimeMillis));

                AtomicBoolean handlerRunning = new AtomicBoolean(true);

                try (Consumer<byte[]> consumer = createConsumerBuilder(pulsarClient, topicName)
                        .consumerName(consumerName)
                        .messageListener((c, msg) -> handleMessage(c, msg, state, handlerRunning))
                        .subscribe()) {
                    connectCount++;
                    long startConnectTimeNanos = System.nanoTime();

                    while (!Thread.currentThread().isInterrupted()) {
                        long currentNanos = System.nanoTime();
                        if (simulateReconnecting
                                && currentNanos - startConnectTimeNanos > minimumConnectTimeNanosWithJitter) {
                            // disconnect and reconnect
                            state.handlingLock.lock();
                            try {
                                handlerRunning.set(false);
                                keepConnected = true;
                            } finally {
                                state.handlingLock.unlock();
                            }
                            break;
                        }
                        Thread.sleep(1000);
                        long lastReceivedNanos = state.lastReceivedMessageNanos.get();
                        if (lastReceivedNanos != -1L && currentNanos - lastReceivedNanos > TimeUnit.SECONDS.toNanos(
                                RECEIVE_TIMEOUT_SECONDS)) {
                            log.info("Consumer {} timed out", consumerName);
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (keepConnected) {
                try {
                    // sleep for a random time before reconnecting
                    Thread.sleep(100 + random.nextInt(100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
        return new ConsumeReport(consumerName, state.uniqueMessages, state.duplicates, state.receivedMessages(), durationMillis,
                TimeUnit.NANOSECONDS.toMillis(state.maxLatencyDifferenceNanos), connectCount);
    }

    private class HandlingState {
        private final RoaringBitmap receivedMessages = new RoaringBitmap();
        private final ReentrantLock handlingLock = new ReentrantLock();

        public synchronized <T> T withReceivedMessages(java.util.function.Function<RoaringBitmap, T> consumer) {
            return consumer.apply(receivedMessages);
        }

        public synchronized RoaringBitmap receivedMessages() {
            return receivedMessages;
        }

        final AtomicLong lastReceivedMessageNanos = new AtomicLong(-1L);
        volatile long maxLatencyDifferenceNanos = 0;
        volatile long previousLatencyNanos = -1;
        volatile int uniqueMessages = 0;
        volatile int duplicates = 0;
    }

    private void handleMessage(Consumer<byte[]> consumer, Message<byte[]> msg, HandlingState state,
                               AtomicBoolean handlerRunning) {
        state.handlingLock.lock();
        try {
            if (!handlerRunning.get()) {
                // nack the message if the handler is not running
                consumer.negativeAcknowledge(msg);
                return;
            }
            Random random = ThreadLocalRandom.current();
            state.lastReceivedMessageNanos.set(System.nanoTime());

            // we set System.nanoTime() as event time in publishing for e2e latency calculation
            long latencyNanos = Math.max(System.nanoTime() - msg.getEventTime(), 0);
            if (state.previousLatencyNanos != -1) {
                long latencyDifferenceNanos = Math.abs(latencyNanos - state.previousLatencyNanos);
                if (latencyDifferenceNanos > state.maxLatencyDifferenceNanos) {
                    state.maxLatencyDifferenceNanos = latencyDifferenceNanos;
                    log.info("Max latency difference increased: {} ms",
                            TimeUnit.NANOSECONDS.toMillis(state.maxLatencyDifferenceNanos));
                }
            }
            state.previousLatencyNanos = latencyNanos;

            int msgNum = bytesToInt(msg.getData());

            // sleep for a random time with 3% probability
            if (random.nextInt(100) < 3) {
                try {
                    Thread.sleep(random.nextInt(100) + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            boolean added = state.withReceivedMessages(receivedMessages -> receivedMessages.checkedAdd(msgNum));
            if (added) {
                state.uniqueMessages++;
            } else {
                state.duplicates++;
            }
            log.info("Received value: {} duplicate: {} unique: {} duplicates: {}", msgNum, !added,
                    state.uniqueMessages,
                    state.duplicates);
            try {
                consumer.acknowledge(msg);
            } catch (PulsarClientException e) {
                log.warn("Failed to ack message", e);
            }
            messagesInFlight.decrementAndGet();
        } finally {
            state.handlingLock.unlock();
        }
    }

    private record ConsumeReport(String consumerName, int uniqueMessages, int duplicates, RoaringBitmap receivedMessages,
                                 long durationMillis, long maxLatencyDifferenceMillis, int connectCount) {
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
                .subscriptionType(SubscriptionType.Key_Shared)
                .keySharedPolicy(KeySharedPolicy.autoSplitHashRange().setAllowOutOfOrderDelivery(true))
                .topic(topicName);
    }

    public static void main(String[] args) throws Throwable {
        try {
            String namespace = "test_ns" + System.currentTimeMillis();
            if (args.length > 0) {
                namespace = args[0];
            }
            log.info("Using namespace {}", namespace);
            new TestScenarioKeySharedReconnects(namespace).run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
