#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
$SCRIPT_DIR/delete_deployment.sh || exit 1
. $SCRIPT_DIR/test-env.env
echo 'Installing 1node cluster'
value_files=""
for arg in "$@"; do
    if [ -f "$arg" ]; then
        value_files+=" -f $arg"
    else
        >&2 echo "Cannot find $arg" && exit 1
    fi
done
if [ -z "$value_files" ]; then
    value_files="-f $SCRIPT_DIR/java_test_images.yaml"
fi
echo "Using values '${value_files}'"
CHART="${CHART:-apache/pulsar}"
helm upgrade --install --namespace "${DEPLOYMENT_NAMESPACE}" --create-namespace -f $SCRIPT_DIR/1node/values.yaml $value_files --set initialize=true "${DEPLOYMENT_NAME}" "$CHART"
