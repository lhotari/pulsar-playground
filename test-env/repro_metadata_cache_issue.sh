#!/bin/bash
TENANT=mytenant$$
NS="${TENANT}/myns"
TOPIC="persistent://${NS}/mytopic"
BROKER0=http://pulsar-testenv-pulsar-broker-0.pulsar-testenv-pulsar-broker.pulsar-testenv.svc.cluster.local:8080
BROKER1=http://pulsar-testenv-pulsar-broker-1.pulsar-testenv-pulsar-broker.pulsar-testenv.svc.cluster.local:8080
shopt -s expand_aliases
alias echo='{ set +x; } 2> /dev/null; doecho'
doecho() {
  builtin echo "$*"
  set -x
}
set -x
echo "Creating a tenant"
pulsar-admin --admin-url "$BROKER0" tenants create "$TENANT"
echo "Creating a namespace"
pulsar-admin --admin-url "$BROKER0" namespaces create "$NS"
echo "List topics on broker-1: (it's expected to be empty)"
pulsar-admin --admin-url "$BROKER1" topics list "$NS"
echo "Create the topic"
pulsar-admin --admin-url "$BROKER0" topics create "$TOPIC"
echo "Wait 10 seconds to ensure that it's not a race condition"
sleep 10
echo "The topic should get listed on broker-1:"
pulsar-admin --admin-url "$BROKER1" topics list "$NS"
echo "Listing topics on broker-0:"
pulsar-admin --admin-url "$BROKER0" topics list "$NS"
