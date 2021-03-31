#!/bin/bash -xe
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"

function pulsar_admin() {
  kubectl exec -i -t -n cluster-a cluster-a-pulsar-broker-0 -- \
   bin/pulsar-admin "${@}"
}

pulsar_admin "${@}"