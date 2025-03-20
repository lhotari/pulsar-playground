#!/bin/bash -xe
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
CHART_HOME="${SCRIPT_DIR}"/../../pulsar-helm-chart
DEFAULT_CHART="${CHART_HOME}/charts/pulsar"

# Initialize variables with defaults
chart_path="${DEFAULT_CHART}"
no_update=0

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-dependency-update)
            no_update=1
            shift
            ;;
        --chart=*)
            chart_path="${1#*=}"
            no_update=1
            shift
            ;;
        --chart)
            chart_path="$2"
            no_update=1
            shift 2
            ;;
        *)
            # Store remaining arguments for helm
            break
            ;;
    esac
done

if [[ "$no_update" != "1" ]]; then
    helm dependency update "${chart_path}"
fi

# installs or upgrades chart
helm upgrade --install --namespace pulsar --create-namespace \
  pulsar "${chart_path}" \
  --values "${CHART_HOME}"/examples/values-testing.yaml \
  "$@"
