# Pulsar Chaos Tests

Pytest-based chaos testing for Apache Pulsar on Kubernetes.

## Prerequisites

- A running Kubernetes cluster (`kubectl` configured)
- Helm 3
- Python 3.11+
- [uv](https://docs.astral.sh/uv/) (`pip install uv` or `brew install uv`)

## Setup

```bash
# Create a virtual environment and install dependencies
uv venv
source .venv/bin/activate
uv pip install -e .
```

## Running

```bash
# Run all chaos tests (verbose, with live log output)
uv run pytest -v

# Run only the ZK quorum loss scenario
uv run pytest test_zk_quorum_loss.py -v

# Run a specific test
uv run pytest test_zk_quorum_loss.py::TestZookeeperQuorumLoss::test_consume_after_zk_quorum_loss -v
```

## Project Structure

```
conftest.py              # Session fixtures: Helm install/teardown, K8s clients
pulsar_perf.py           # Wrappers around pulsar-perf (produce/consume)
k8s_chaos.py             # K8s chaos operations (scale, delete pods, etc.)
test_zk_quorum_loss.py   # Scenario: ZK quorum loss without ledger rollover
```

## Adding New Scenarios

1. Create a new `test_<scenario>.py` file
2. Use the `pulsar_cluster` fixture for setup/teardown
3. Use helpers from `k8s_chaos.py` and `pulsar_perf.py`
4. Add cleanup in a `yield` fixture or `finally` block
