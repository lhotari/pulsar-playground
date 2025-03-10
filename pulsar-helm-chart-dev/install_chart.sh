#!/bin/bash -xe
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
# installs or upgrades chart
helm upgrade --install --namespace pulsar --create-namespace \
  pulsar charts/pulsar \
  --values "${SCRIPT_DIR}"/../../pulsar-helm-chart/examples/values-testing.yaml \
  "$@"
