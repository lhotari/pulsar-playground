#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
. $SCRIPT_DIR/test-env.env

if [[ "$1" == "--upgrade" ]]; then
    shift
    echo 'Upgrading 1node cluster'
else
    $SCRIPT_DIR/delete_deployment.sh || exit 1
    initialize_params="--set initialize=true"
    echo 'Installing 1node cluster'
fi

value_files=""
for arg in "$@"; do
    if [[ "$arg" == "--set" ]]; then
        break
    fi
    if [ -f "$arg" ]; then
        value_files+=" -f $arg"
        shift
    else
        >&2 echo "Cannot find $arg" && exit 1
    fi
done
if [ -z "$value_files" ]; then
    value_files="-f $SCRIPT_DIR/java_test_images.yaml"
fi
CHART="${CHART:-apache/pulsar}"
if [[ $CHART == *datastax-pulsar* ]]; then
    value_files="-f $SCRIPT_DIR/datastax_dev-values.yaml ${value_files}"
    initialize_params="${initialize_params} --set fullnameOverride=${DEPLOYMENT_NAMESPACE}-pulsar"
else
    value_files="-f $SCRIPT_DIR/1node/values.yaml ${value_files}"
    initialize_params="${initialize_params} --set namespace=${DEPLOYMENT_NAMESPACE} --set clusterName=${DEPLOYMENT_NAMESPACE}"
fi
echo "Using values '${value_files}'"
set -x
helm upgrade --wait --debug --install --namespace "${DEPLOYMENT_NAMESPACE}" --create-namespace $value_files $initialize_params "${DEPLOYMENT_NAME}" "$CHART" "$@"
