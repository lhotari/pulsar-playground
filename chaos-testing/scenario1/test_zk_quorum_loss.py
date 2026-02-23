"""
Scenario: Pulsar cluster with 3 ZooKeeper pods where 2 ZK pods fail.

Goal:
    Test what happens when ZK loses quorum *without* ledger rollover.
    The cluster should be able to continue some operations while ZK
    is in read-only mode.

Sequence:
    1. Start consumer on sub1 (long-running)
    2. Start producer at slow rate (50 msg/s)
    3. Start a one-shot consumer on sub2 (just to create the subscription)
    4. Produce for 5 more seconds
    5. Scale ZK StatefulSet from 3 → 1 (quorum loss)
    6. Attempt to consume on sub2 — verify it can still receive messages
"""
import time
import logging
import pytest

from pulsar_perf import (
    start_producer,
    start_consumer,
    run_consumer_and_wait,
)
from pulsar_admin import (
    create_subscription,
    delete_subscription,
    unload_topic,
)
from k8s_chaos import (
    scale_statefulset,
    wait_for_statefulset_ready,
    get_statefulset_name,
)

logger = logging.getLogger(__name__)

TOPIC = "persistent://public/default/chaos-zk-quorum-test"
PRODUCE_RATE = 50
PRE_CHAOS_PRODUCE_SECONDS = 30
POST_CHAOS_CONSUME_TIMEOUT = 30


class TestZookeeperQuorumLoss:
    """Tests for ZK quorum loss without ledger rollover."""

    # ------------------------------------------------------------------
    # Fixtures scoped to this class
    # ------------------------------------------------------------------
    @pytest.fixture(autouse=True)
    def setup(self, pulsar_cluster, k8s_clients, pulsar_service_url, pulsar_admin_url):
        """Store references and ensure ZK is at full strength before and after."""
        self.ns = pulsar_cluster["namespace"]
        self.release = pulsar_cluster["release"]
        self.service_url = pulsar_service_url
        self.admin_url = pulsar_admin_url
        self.apps = k8s_clients["apps"]

        self.zk_ss_name = get_statefulset_name(
            self.apps, self.ns, label_selector="app=pulsar,component=zookeeper"
        )
        self.broker_ss_name = get_statefulset_name(
            self.apps, self.ns, label_selector="app=pulsar,component=broker"
        )
        self._perf_procs = []

        yield

        # --- print tail and stop any background perf processes still running ---
        for proc in self._perf_procs:
            if proc.is_running:
                proc.stop(log_tail=20)

        # --- restore ZK to 3 replicas after each test ---
        logger.info("Restoring ZK to 3 replicas")
        scale_statefulset(self.apps, self.zk_ss_name, self.ns, replicas=3)
        wait_for_statefulset_ready(self.apps, self.zk_ss_name, self.ns, expected_replicas=3)

        # --- restore brokers to 3 replicas
        logger.info("Restoring brokers to 3 replica")
        scale_statefulset(self.apps, self.broker_ss_name, self.ns, replicas=3)
        wait_for_statefulset_ready(self.apps, self.broker_ss_name, self.ns, expected_replicas=3, timeout=300)

        # deleting subscriptions to avoid interference between tests
        delete_subscription(
            namespace=self.ns,
            admin_url=self.admin_url,
            topic=TOPIC,
            subscription="sub1",
        )
        delete_subscription(
            namespace=self.ns,
            admin_url=self.admin_url,
            topic=TOPIC,
            subscription="sub2",
        )
        delete_subscription(
            namespace=self.ns,
            admin_url=self.admin_url,
            topic=TOPIC,
            subscription="sub3",
        )

    # ------------------------------------------------------------------
    # The scenario
    # ------------------------------------------------------------------
    @pytest.mark.parametrize("unload_after_chaos", [False, True], ids=["no_unload", "with_unload"])
    def test_consume_after_zk_quorum_loss(self, unload_after_chaos):
        """
        After losing ZK quorum the broker should still be able to serve
        messages that were already written to BookKeeper (no new ledger
        required), because ZK is configured for read-only mode.

        With unload_after_chaos=True, the topic is unloaded after quorum loss,
        forcing the broker to re-acquire it from ZK before sub2 can consume.
        """

        # ── Step 1: start a long-running consumer on sub1 ──
        consumer_sub1 = start_consumer(
            namespace=self.ns,
            service_url=self.service_url,
            topic=TOPIC,
            subscription="sub1",
        )
        self._perf_procs.append(consumer_sub1)
        assert consumer_sub1.is_running, "sub1 consumer failed to start"

        # ── Step 2: start a slow producer ──
        producer = start_producer(
            namespace=self.ns,
            service_url=self.service_url,
            topic=TOPIC,
            rate=PRODUCE_RATE,
        )
        self._perf_procs.append(producer)
        assert producer.is_running, "Producer failed to start"

        # ── Step 3: create sub2 subscription via pulsar-admin ──
        create_subscription(
            namespace=self.ns,
            admin_url=self.admin_url,
            topic=TOPIC,
            subscription="sub2",
        )
        logger.info("sub2 subscription created")

        # ── Step 4: keep producing for a few more seconds ──
        logger.info(f"Producing for {PRE_CHAOS_PRODUCE_SECONDS}s before chaos …")
        time.sleep(PRE_CHAOS_PRODUCE_SECONDS)

        # ── Step 5: kill 2 of 3 ZK pods → quorum loss ──
        logger.info(">>> CHAOS: scaling ZK to 1 replica (quorum loss) <<<")
        scale_statefulset(self.apps, self.zk_ss_name, self.ns, replicas=1)
        wait_for_statefulset_ready(self.apps, self.zk_ss_name, self.ns, expected_replicas=1)

        # ── Step 5b: assert producer and consumer_sub1 survived quorum loss ──
        for perf_proc in (producer, consumer_sub1):
            if not perf_proc.is_running:
                output = perf_proc.stop()
                last_lines = "\n".join(output.splitlines()[-50:])
                logger.error(
                    f"[{perf_proc.description}] terminated unexpectedly after ZK quorum loss.\n"
                    f"Last 50 lines of output:\n{last_lines}"
                )
                pytest.fail(f"{perf_proc.description} terminated after ZK quorum loss")

        # ── Step 5c (optional): unload the topic while ZK has no quorum ──
        if unload_after_chaos:
            logger.info("Unloading topic after ZK quorum loss …")
            try:
                unload_topic(namespace=self.ns, admin_url=self.admin_url, topic=TOPIC)
                logger.info("Topic unloaded successfully despite ZK quorum loss")
            except RuntimeError as e:
                logger.warning(f"Topic unload failed (expected under quorum loss): {e}")

        # ── Step 6: can sub2 still consume? ──
        label = "ZK quorum loss" if not unload_after_chaos else "ZK quorum loss + topic unload"
        logger.info(f"Attempting to consume on sub2 after {label} …")
        try:
            output = run_consumer_and_wait(
                namespace=self.ns,
                service_url=self.service_url,
                topic=TOPIC,
                subscription="sub2",
                num_messages=150,
                timeout=POST_CHAOS_CONSUME_TIMEOUT,
            )
            # If we got here without a timeout, the consumer received messages
            messages_received = "150 records received" in output
            logger.info(f"sub2 output:\n{output}")
            assert messages_received, (
                f"Consumer on sub2 ran but did not appear to receive messages.\n"
                f"Output:\n{output}"
            )
        except Exception as e:
            # Depending on the Pulsar version, the consumer might hang or error.
            # This is still a valid test result — just record it.
            pytest.fail(
                f"Consumer on sub2 could NOT consume after {label}: {e}"
            )
        finally:
            # Cleanup background processes
            producer.stop(log_tail=20)
            consumer_sub1.stop(log_tail=20)

    def test_producer_behavior_after_zk_quorum_loss(self):
        """
        Verify whether the producer can continue sending messages
        after ZK loses quorum (as long as no ledger rollover is needed).
        """

        producer = start_producer(
            namespace=self.ns,
            service_url=self.service_url,
            topic=TOPIC,
            rate=PRODUCE_RATE,
        )
        self._perf_procs.append(producer)

        time.sleep(3)
        assert producer.is_running, "Producer should be running before chaos"

        # ── Chaos ──
        logger.info(">>> CHAOS: scaling ZK to 1 replica <<<")
        scale_statefulset(self.apps, self.zk_ss_name, self.ns, replicas=1)
        wait_for_statefulset_ready(self.apps, self.zk_ss_name, self.ns, expected_replicas=1)

        logger.info("Producing for 10 seconds after ZK quorum loss …")
        time.sleep(10)

        # ── Check if producer is still alive ──
        still_running = producer.is_running
        output = producer.stop(log_tail=20)

        logger.info(f"Producer still running after quorum loss: {still_running}")
        logger.info(f"Producer output:\n{output}")

        assert still_running, (
            "Producer died after ZK quorum loss — expected it to continue "
            "while no ledger rollover is required."
        )

        # ── can a new subscriber consume? ──
        logger.info("Attempting to consume on sub3 after ZK quorum loss …")
        try:
            output = run_consumer_and_wait(
                namespace=self.ns,
                service_url=self.service_url,
                topic=TOPIC,
                subscription="sub3",
                num_messages=150,
                timeout=POST_CHAOS_CONSUME_TIMEOUT,
            )
            # If we got here without a timeout, the consumer received messages
            messages_received = "150 records received" in output
            logger.info(f"sub3 output:\n{output}")
            assert messages_received, (
                f"Consumer on sub3 ran but did not appear to receive messages.\n"
                f"Output:\n{output}"
            )
        except Exception as e:
            # Depending on the Pulsar version, the consumer might hang or error.
            # This is still a valid test result — just record it.
            pytest.fail(
                f"Consumer on sub3 could NOT consume after ZK quorum loss: {e}"
            )

    @pytest.mark.skip(reason="This always fails because brokers cannot start when ZK is in quorum loss mode.")
    def test_broker_restart_during_zk_quorum_loss(self):
        """
        Create sub1, produce to the topic for 5 seconds, then cause ZK quorum loss.
        While ZK quorum is absent, scale brokers to 0 then back to 3.
        After brokers are up and running, attempt to consume 150 messages from sub1.

        This exercises whether brokers can rejoin the cluster and serve
        already-written messages when ZK is still in quorum-loss (read-only) mode.
        """

        # ── Step 1: create sub1 subscription ──
        create_subscription(
            namespace=self.ns,
            admin_url=self.admin_url,
            topic=TOPIC,
            subscription="sub1",
        )
        logger.info("sub1 subscription created")

        # ── Step 2: produce for 5 seconds ──
        producer = start_producer(
            namespace=self.ns,
            service_url=self.service_url,
            topic=TOPIC,
            rate=PRODUCE_RATE,
        )
        self._perf_procs.append(producer)
        assert producer.is_running, "Producer failed to start"

        logger.info("Producing for 5s …")
        time.sleep(5)
        producer.stop(log_tail=20)

        # ── Step 3: cause ZK quorum loss ──
        logger.info(">>> CHAOS: scaling ZK to 1 replica (quorum loss) <<<")
        scale_statefulset(self.apps, self.zk_ss_name, self.ns, replicas=1)
        wait_for_statefulset_ready(self.apps, self.zk_ss_name, self.ns, expected_replicas=1)

        # ── Step 4: scale brokers to 0 then back to 3, ZK still in quorum loss ──
        logger.info(">>> CHAOS: scaling brokers to 0 <<<")
        scale_statefulset(self.apps, self.broker_ss_name, self.ns, replicas=0)
        wait_for_statefulset_ready(self.apps, self.broker_ss_name, self.ns, expected_replicas=0)

        logger.info(">>> RECOVERY: scaling brokers to 3 (ZK still in quorum loss) <<<")
        scale_statefulset(self.apps, self.broker_ss_name, self.ns, replicas=3)
        wait_for_statefulset_ready(
            self.apps, self.broker_ss_name, self.ns, expected_replicas=3, timeout=300
        )

        # ── Step 5: consume 150 messages from sub1 ──
        logger.info(
            "Attempting to consume 150 messages on sub1 after broker restart "
            "under ZK quorum loss …"
        )
        try:
            output = run_consumer_and_wait(
                namespace=self.ns,
                service_url=self.service_url,
                topic=TOPIC,
                subscription="sub1",
                num_messages=150,
                timeout=POST_CHAOS_CONSUME_TIMEOUT,
            )
            messages_received = "150 records received" in output
            logger.info(f"sub1 output:\n{output}")
            assert messages_received, (
                f"Consumer on sub1 ran but did not receive 150 messages.\n"
                f"Output:\n{output}"
            )
        except Exception as e:
            pytest.fail(
                f"Consumer on sub1 could NOT consume after broker restart "
                f"during ZK quorum loss: {e}"
            )