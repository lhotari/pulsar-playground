#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
$SCRIPT_DIR/delete_deployment.sh || exit 1
. $SCRIPT_DIR/test-env.env
echo 'Installing 1node cluster'
IMAGES_YAML=${1:-$SCRIPT_DIR/java_test_images.yaml}
echo "Using images from ${IMAGES_YAML} file"
helm upgrade --install --namespace "${DEPLOYMENT_NAMESPACE}" --create-namespace -f $SCRIPT_DIR/1node/values.yaml -f $IMAGES_YAML --set initialize=true "${DEPLOYMENT_NAME}" apache/pulsar