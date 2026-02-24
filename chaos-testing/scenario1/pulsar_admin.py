"""
Helpers for running pulsar-admin commands inside K8s pods.
"""
import subprocess
import logging

from pulsar_perf import get_toolset_pod

logger = logging.getLogger(__name__)


def _run_pulsar_admin(namespace: str, pod: str, admin_url: str, *args: str) -> subprocess.CompletedProcess:
    cmd = (
        f"kubectl exec -n {namespace} {pod} -- "
        f"bin/pulsar-admin --admin-url {admin_url} "
        + " ".join(args)
    )
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=30)


def create_subscription(
    namespace: str,
    admin_url: str,
    topic: str,
    subscription: str,
    pod: str | None = None,
) -> None:
    """Create a subscription on a topic via pulsar-admin running in the toolset pod."""
    pod = pod or get_toolset_pod(namespace)
    logger.info(f"Creating subscription '{subscription}' on '{topic}'")
    result = _run_pulsar_admin(
        namespace, pod, admin_url,
        f"topics create-subscription -s {subscription} {topic}",
    )
    if result.returncode != 0:
        if "Subscription already exists for topic" in result.stderr:
            logger.info(f"Subscription '{subscription}' already exists")
        else:
            raise RuntimeError(
                f"pulsar-admin create-subscription failed (rc={result.returncode}):\n"
                f"{result.stderr}"
            )
    else:
        logger.info(f"Subscription '{subscription}' created")


def unload_topic(
    namespace: str,
    admin_url: str,
    topic: str,
    pod: str | None = None,
) -> None:
    """Unload a topic via pulsar-admin running in the toolset pod."""
    pod = pod or get_toolset_pod(namespace)
    logger.info(f"Unloading topic '{topic}'")
    result = _run_pulsar_admin(
        namespace, pod, admin_url,
        f"topics unload {topic}",
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"pulsar-admin unload topic failed (rc={result.returncode}):\n"
            f"{result.stderr}"
        )
    else:
        logger.info(f"Topic '{topic}' unloaded")


def delete_subscription(
    namespace: str,
    admin_url: str,
    topic: str,
    subscription: str,
    pod: str | None = None,
) -> None:
    """Delete a subscription on a topic via pulsar-admin running in the toolset pod."""
    pod = pod or get_toolset_pod(namespace)
    logger.info(f"Deleting subscription '{subscription}' on '{topic}'")
    result = _run_pulsar_admin(
        namespace, pod, admin_url,
        f"topics unsubscribe -s {subscription} --force {topic}",
    )
    if result.returncode != 0:
        if f"Subscription {subscription} not found" in result.stderr:
            logger.info(f"Subscription '{subscription}' does not exist")
        else:
            raise RuntimeError(
                f"pulsar-admin delete-subscription failed (rc={result.returncode}):\n"
                f"{result.stderr}"
            )
    else:
        logger.info(f"Subscription '{subscription}' deleted")
