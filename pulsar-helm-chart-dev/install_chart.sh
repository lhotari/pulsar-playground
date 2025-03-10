#!/bin/bash -xe
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
CHART_HOME="${SCRIPT_DIR}"/../../pulsar-helm-chart
if [[ "$1" == "--skip-dependency-update" ]]; then
  no_update=1
  shift
fi
if [[ "$no_update" != "1" ]]; then
    helm dependency update "${CHART_HOME}"/charts/pulsar
fi
# installs or upgrades chart
helm upgrade --install --namespace pulsar --create-namespace \
  pulsar "${CHART_HOME}"/charts/pulsar \
  --values "${CHART_HOME}"/examples/values-testing.yaml \
  "$@"
