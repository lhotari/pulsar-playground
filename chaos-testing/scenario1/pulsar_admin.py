"""
Helpers for running pulsar-admin commands inside K8s pods.
"""
import subprocess
import logging

from pulsar_perf import get_toolset_pod

logger = logging.getLogger(__name__)


def create_subscription(
    namespace: str,
    admin_url: str,
    topic: str,
    subscription: str,
    pod: str | None = None,
) -> None:
    """Create a subscription on a topic via pulsar-admin running in the toolset pod."""
    pod = pod or get_toolset_pod(namespace)
    cmd = (
        f"kubectl exec -n {namespace} {pod} -- "
        f"bin/pulsar-admin --admin-url {admin_url} "
        f"topics create-subscription "
        f"-s {subscription} "
        f"{topic}"
    )
    logger.info(f"Creating subscription '{subscription}' on '{topic}'")
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=30)
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
