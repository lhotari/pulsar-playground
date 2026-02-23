"""
Helpers for running pulsar-perf inside K8s pods.
"""
import os
import signal
import subprocess
import logging
import time
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class PerfProcess:
    """Wraps a background pulsar-perf process running in a K8s pod."""
    proc: subprocess.Popen
    description: str
    _collected: bool = field(default=False, init=False, repr=False)
    _output_cache: str = field(default="", init=False, repr=False)

    def stop(self, timeout: int = 15) -> str:
        """Terminate the process group and return combined stdout + stderr.

        Idempotent: subsequent calls return the cached output from the first call.
        """
        if self._collected:
            return self._output_cache
        self._collected = True
        try:
            os.killpg(os.getpgid(self.proc.pid), signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            stdout, stderr = self.proc.communicate(timeout=timeout)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(os.getpgid(self.proc.pid), signal.SIGKILL)
            except ProcessLookupError:
                pass
            stdout, stderr = self.proc.communicate()
        self._output_cache = (stdout or "") + (stderr or "")
        logger.info(f"[{self.description}] exited rc={self.proc.returncode}")
        return self._output_cache

    @property
    def is_running(self) -> bool:
        return self.proc.poll() is None


def _kubectl_exec_cmd(namespace: str, pod: str, container: str | None = None) -> str:
    """Build the kubectl exec prefix."""
    cmd = f"kubectl exec -n {namespace} {pod}"
    if container:
        cmd += f" -c {container}"
    cmd += " --"
    return cmd


def get_toolset_pod(namespace: str) -> str:
    """Return the name of the toolset pod using the component=toolset label."""
    result = subprocess.run(
        f"kubectl get pods -n {namespace} -l component=toolset "
        f"-o jsonpath='{{.items[0].metadata.name}}'",
        shell=True,
        capture_output=True,
        text=True,
    )
    result.check_returncode()
    pod = result.stdout.strip().strip("'")
    assert pod, "No toolset pod found"
    return pod


def start_producer(
    namespace: str,
    service_url: str,
    topic: str,
    rate: int = 50,
    message_size: int = 1024,
    pod: str | None = None,
) -> PerfProcess:
    """
    Start pulsar-perf produce in the background.
    Runs inside the toolset pod via kubectl exec.
    """
    pod = pod or get_toolset_pod(namespace)
    prefix = _kubectl_exec_cmd(namespace, pod)
    cmd = (
        f"{prefix} bin/pulsar-perf produce "
        f"--service-url {service_url} "
        f"--rate {rate} "
        f"--size {message_size} "
        f"{topic}"
    )
    logger.info(f"Starting producer: {cmd}")
    proc = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, start_new_session=True)
    time.sleep(2)  # let it initialise
    assert proc.poll() is None, "Producer exited unexpectedly"
    return PerfProcess(proc=proc, description=f"producer->{topic}")


def start_consumer(
    namespace: str,
    service_url: str,
    topic: str,
    subscription: str,
    num_messages: int = 0,
    pod: str | None = None,
) -> PerfProcess:
    """
    Start pulsar-perf consume in the background.
    If num_messages > 0, add -m flag to receive only that many messages.
    """
    pod = pod or get_toolset_pod(namespace)
    prefix = _kubectl_exec_cmd(namespace, pod)
    m_flag = f"-m {num_messages}" if num_messages > 0 else ""
    cmd = (
        f"{prefix} bin/pulsar-perf consume "
        f"--service-url {service_url} "
        f"--subscriptions {subscription} "
        f"{m_flag} "
        f"{topic}"
    )
    logger.info(f"Starting consumer: {cmd}")
    proc = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, start_new_session=True)
    time.sleep(2)
    assert proc.poll() is None, "Consumer exited unexpectedly"
    return PerfProcess(proc=proc, description=f"consumer->{topic}:{subscription}")


def run_consumer_and_wait(
    namespace: str,
    service_url: str,
    topic: str,
    subscription: str,
    num_messages: int = 1,
    timeout: int = 30,
    pod: str | None = None,
) -> str:
    """
    Run pulsar-perf consume synchronously and return its output.
    Useful for verifying that a consumer can (or cannot) receive messages.
    """
    pod = pod or get_toolset_pod(namespace)
    prefix = _kubectl_exec_cmd(namespace, pod)
    cmd = (
        f"{prefix} bin/pulsar-perf consume "
        f"--service-url {service_url} "
        f"--subscriptions {subscription} "
        f"-m {num_messages} "
        f"{topic}"
    )
    logger.info(f"Running consumer (sync, timeout={timeout}s): {cmd}")
    result = subprocess.run(
        cmd, shell=True, capture_output=True, text=True, timeout=timeout
    )
    logger.info(f"Consumer exited rc={result.returncode}")
    logger.debug(f"STDOUT: {result.stdout}")
    logger.debug(f"STDERR: {result.stderr}")
    return result.stdout + result.stderr
