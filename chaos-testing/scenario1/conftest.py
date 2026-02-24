"""
Pytest fixtures for Pulsar chaos testing.

Handles Helm-based cluster lifecycle and provides
Kubernetes and Pulsar utility helpers.
"""
import subprocess
import time
import logging
import pytest
from kubernetes import client, config
from pulsar_perf import get_toolset_pod

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
HELM_RELEASE = "pulsar-chaos"
NAMESPACE = "pulsar-chaos"
HELM_CHART = "apache/pulsar"
HELM_REPO_URL = "https://pulsar.apache.org/charts"
PULSAR_IMAGE_REPO = "apachepulsar/pulsar"

HELM_SET_VALUES = {
    "affinity.anti_affinity": "false",
    "defaultPulsarImageRepository": PULSAR_IMAGE_REPO,
    "defaultPulsarImageTag": "4.1.3",
    "components.proxy": "false",
}

# Values that must be passed as --set-string to avoid Helm coercing them to
# booleans/numbers, which breaks ConfigMap data fields (strings only).
HELM_SET_STRING_VALUES = {
    "pulsar_metadata.metadataStoreAllowReadOnlyOperations": "true",
    "pulsar_metadata.bookkeeper.usePulsarMetadataClientDriver": "true",
    "pulsar_metadata.metadataStoreBatchingEnabled": "false",
    "broker.waitZookeeperTimeout": "0",
    "broker.waitBookkeeperTimeout": "0",
    "bookkeeper.metadata.waitZookeeperTimeout": "0",
    # The following ones aren't required after 4.6.0 release of Apache Pulsar Helm chart
    "broker.configData.PULSAR_PREFIX_metadataStoreAllowReadOnlyOperations": "true",
    "zookeeper.configData.OPTS": "-Dreadonlymode.enabled=true",
}

# Added only when --short-rollover is passed; forces frequent ledger rollovers.
HELM_SET_STRING_VALUES_SHORT_ROLLOVER = {
    "broker.configData.managedLedgerMaxEntriesPerLedger": "50",
    "broker.configData.managedLedgerMaxLedgerRolloverTimeMinutes": "2",
    "broker.configData.managedLedgerMinLedgerRolloverTimeMinutes": "0",
}

# Timeouts (seconds)
HELM_INSTALL_TIMEOUT = "600s"
POD_READY_TIMEOUT = 300
POD_POLL_INTERVAL = 10


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def run(cmd: str, check: bool = True, timeout: int = 120) -> subprocess.CompletedProcess:
    """Run a shell command and return the result."""
    logger.info(f"Running: {cmd}")
    result = subprocess.run(
        cmd, shell=True, capture_output=True, text=True, timeout=timeout
    )
    if check and result.returncode != 0:
        logger.error(f"STDOUT: {result.stdout}")
        logger.error(f"STDERR: {result.stderr}")
        result.check_returncode()
    return result


def wait_for_pods_ready(
    v1: client.CoreV1Api,
    namespace: str,
    label_selector: str,
    expected_count: int,
    timeout: int = POD_READY_TIMEOUT,
):
    """Block until *expected_count* pods matching the selector are Ready."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        pods = v1.list_namespaced_pod(namespace, label_selector=label_selector)
        ready = [
            p
            for p in pods.items
            if p.status.phase == "Running"
            and all(
                cs.ready
                for cs in (p.status.container_statuses or [])
            )
        ]
        if len(ready) >= expected_count:
            logger.info(
                f"{len(ready)}/{expected_count} pods ready for {label_selector}"
            )
            return
        logger.info(
            f"Waiting … {len(ready)}/{expected_count} ready for {label_selector}"
        )
        time.sleep(POD_POLL_INTERVAL)
    raise TimeoutError(
        f"Only {len(ready)}/{expected_count} pods became ready "
        f"for selector '{label_selector}' within {timeout}s"
    )


# ---------------------------------------------------------------------------
# CLI options
# ---------------------------------------------------------------------------
def pytest_addoption(parser):
    parser.addoption(
        "--keep-cluster",
        action="store_true",
        default=False,
        help="Skip Helm uninstall and namespace deletion after the test run. "
        "Useful for re-running tests against the same cluster.",
    )
    parser.addoption(
        "--skip-deploy",
        action="store_true",
        default=False,
        help="Skip all Helm commands (repo add/update, install, uninstall). "
        "Assumes the cluster is already running. Implies --keep-cluster behaviour.",
    )
    parser.addoption(
        "--short-rollover",
        action="store_true",
        default=False,
        help="Configure brokers for rapid ledger rollover "
        "(managedLedgerMaxEntriesPerLedger=50, rollover times 0/2 min). "
        "Useful for tests that need many ledger segments quickly.",
    )
    parser.addoption(
        "--helm-chart",
        default=HELM_CHART,
        help="Helm chart path or repo reference (default: apache/pulsar). "
        "Can be a local directory path for modified charts.",
    )


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------
@pytest.fixture(scope="session")
def k8s_clients():
    """Load kubeconfig and return API clients."""
    config.load_kube_config()
    return {
        "core": client.CoreV1Api(),
        "apps": client.AppsV1Api(),
    }


@pytest.fixture(scope="session")
def pulsar_cluster(request, k8s_clients):
    """
    Install Pulsar via Helm for the entire test session.

    Uses ``helm upgrade --install`` so it works both for fresh installs
    and for re-runs against an existing cluster (with --keep-cluster).

    Pass ``--keep-cluster`` to skip teardown after the test run.
    """
    keep_cluster = request.config.getoption("--keep-cluster")
    skip_deploy = request.config.getoption("--skip-deploy")
    helm_chart = request.config.getoption("--helm-chart")
    v1: client.CoreV1Api = k8s_clients["core"]

    if skip_deploy:
        logger.info("--skip-deploy set: skipping all Helm commands, assuming cluster is already running")
    else:
        # --- add helm repo ---
        run(f"helm repo add apache {HELM_REPO_URL}")
        run("helm repo update")

        # --- build --set / --set-string flags ---
        set_flags = " ".join(f"--set {k}={v}" for k, v in HELM_SET_VALUES.items())
        set_string_values = dict(HELM_SET_STRING_VALUES)
        if request.config.getoption("--short-rollover"):
            set_string_values.update(HELM_SET_STRING_VALUES_SHORT_ROLLOVER)
        set_string_flags = " ".join(
            f"--set-string {k}={v}" for k, v in set_string_values.items()
        )

        # --- install (or upgrade if already present) ---
        run(
            f"helm upgrade --install {HELM_RELEASE} {helm_chart} "
            f"--namespace {NAMESPACE} "
            f"--create-namespace "
            f"--timeout {HELM_INSTALL_TIMEOUT} "
            f"--wait "
            f"{set_flags} {set_string_flags}",
            timeout=660,
        )

    # --- wait for core components ---
    for component, count in [("zookeeper", 3), ("broker", 1), ("bookie", 3), ("toolset", 1)]:
        wait_for_pods_ready(
            v1,
            NAMESPACE,
            label_selector=f"app=pulsar,component={component}",
            expected_count=count,
        )

    logger.info("Pulsar cluster is ready")

    yield {
        "namespace": NAMESPACE,
        "release": HELM_RELEASE,
    }

    # --- teardown ---
    if skip_deploy or keep_cluster:
        logger.info("Skipping Helm uninstall and namespace deletion")
        # kill the toolset pod to ensure a clean slate for pulsar-perf processes, which rely on it
        toolset_pod = get_toolset_pod(NAMESPACE)
        subprocess.Popen(
            f"kubectl delete pod {toolset_pod} -n {NAMESPACE}",
            shell=True,
            start_new_session=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    else:
        run(f"helm uninstall {HELM_RELEASE} --namespace {NAMESPACE}", check=False)
        run(f"kubectl delete namespace {NAMESPACE} --wait=false", check=False)


@pytest.fixture(scope="session")
def pulsar_service_url(pulsar_cluster):
    """Return the internal Pulsar binary service URL."""
    ns = pulsar_cluster["namespace"]
    release = pulsar_cluster["release"]
    return f"pulsar://{release}-broker:6650"


@pytest.fixture(scope="session")
def pulsar_admin_url(pulsar_cluster):
    """Return the internal Pulsar HTTP admin URL."""
    ns = pulsar_cluster["namespace"]
    release = pulsar_cluster["release"]
    return f"http://{release}-broker:8080"
