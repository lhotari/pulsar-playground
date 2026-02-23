# Apache Pulsar Chaos Testing

## Introduction

This repository contains some Pulsar chaos testing scenarios.

## Scenarios

### Pulsar k8s cluster with 3 ZooKeeper (ZK) pods where 2 ZK pods fail

Goal: Test what happens when ZK loses quorum without ledger rollover

Assumption: Pulsar cluster would be able to continue some operations while ZK is in read-only mode.

Setup:
- Use Apache Pulsar Helm chart to install Pulsar.
  - While installing:
    - Set `affinity.anti_affinity=false`
    - Set `broker.configData.PULSAR_PREFIX_metadataStoreAllowReadOnlyOperations=true`
    - Set `defaultPulsarImageRepository=apache/pulsar` for use of smaller image

Sequence:
- Use pulsar-perf to consume from the topic, using subscription `sub1`
- Use pulsar-perf to start producing to a topic
  - Use a slow rate (50 msg/s) to avoid ledger rollover happening quickly
- Start another pulsar-perf to consume 1 msg from the topic using subscription `sub2`, so that just the subscription gets created
- Keep producing for 5 seconds
- Scale the ZK stateful set to 1 replica to lose quorum
- Use pulsar-perf to consume using subscription `sub2` to see if it can start consuming

