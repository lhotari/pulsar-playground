"""
Kubernetes chaos operations — scaling, pod deletion, etc.
"""
import time
import logging
from kubernetes import client

logger = logging.getLogger(__name__)


def scale_statefulset(
    apps: client.AppsV1Api,
    name: str,
    namespace: str,
    replicas: int,
):
    """Scale a StatefulSet to the desired replica count."""
    logger.info(f"Scaling StatefulSet {namespace}/{name} to {replicas} replicas")
    apps.patch_namespaced_stateful_set_scale(
        name=name,
        namespace=namespace,
        body={"spec": {"replicas": replicas}},
    )


def wait_for_statefulset_ready(
    apps: client.AppsV1Api,
    name: str,
    namespace: str,
    expected_replicas: int,
    timeout: int = 180,
):
    """Block until the StatefulSet has exactly *expected_replicas* ready."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        ss = apps.read_namespaced_stateful_set(name, namespace)
        ready = ss.status.ready_replicas or 0
        if ready == expected_replicas:
            logger.info(f"StatefulSet {name}: {ready}/{expected_replicas} ready")
            return
        logger.info(f"Waiting … StatefulSet {name}: {ready}/{expected_replicas} ready")
        time.sleep(5)
    raise TimeoutError(
        f"StatefulSet {name} did not reach {expected_replicas} ready replicas "
        f"within {timeout}s"
    )


def get_statefulset_name(
    apps: client.AppsV1Api,
    namespace: str,
    label_selector: str,
) -> str:
    """Find the first StatefulSet matching the label selector."""
    ss_list = apps.list_namespaced_stateful_set(namespace, label_selector=label_selector)
    assert ss_list.items, f"No StatefulSet found for selector '{label_selector}'"
    return ss_list.items[0].metadata.name
