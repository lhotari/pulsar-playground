#!/bin/bash
TENANT=mytenant$$
NS="${TENANT}/myns"
TOPIC="persistent://${NS}/mytopic"
BROKER0=http://pulsar-testenv-pulsar-broker-0.pulsar-testenv-pulsar-broker.pulsar-testenv.svc.cluster.local:8080
BROKER1=http://pulsar-testenv-pulsar-broker-1.pulsar-testenv-pulsar-broker.pulsar-testenv.svc.cluster.local:8080
set -x
pulsar-admin --admin-url "$BROKER0" tenants create "$TENANT"
pulsar-admin --admin-url "$BROKER0" namespaces create "$NS"
pulsar-admin --admin-url "$BROKER1" topics list "$NS"
pulsar-admin --admin-url "$BROKER0" topics create "$TOPIC"
pulsar-admin --admin-url "$BROKER1" topics list "$NS"
