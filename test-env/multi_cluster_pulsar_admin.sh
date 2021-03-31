#!/bin/bash -xe
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
CLUSTER_NAME=cluster-a
if [[ "$1" == "--cluster-b" ]]; then
  CLUSTER_NAME=cluster-b
  shift
fi
function pulsar_admin() {
  kubectl exec -i -t -n $CLUSTER_NAME $CLUSTER_NAME-pulsar-broker-0 -- \
   bin/pulsar-admin "${@}"
}

pulsar_admin "${@}"