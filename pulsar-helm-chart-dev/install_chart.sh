#!/bin/bash -xe
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
CHART_HOME="${SCRIPT_DIR}"/../../pulsar-helm-chart
helm dependency update "${CHART_HOME}"/charts/pulsar
# installs or upgrades chart
helm upgrade --install --namespace pulsar --create-namespace \
  pulsar "${CHART_HOME}"/charts/pulsar \
  --values "${CHART_HOME}"/examples/values-testing.yaml \
  "$@"
