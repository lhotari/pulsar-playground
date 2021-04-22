#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
$SCRIPT_DIR/delete_deployment.sh
echo 'Installing 1node cluster'
IMAGES_YAML=${1:-$SCRIPT_DIR/java_test_images.yaml}
echo "Using images from ${IMAGES_YAML} file"
helm upgrade --install --namespace pulsar --create-namespace -f $SCRIPT_DIR/1node/values.yaml -f $IMAGES_YAML --set initialize=true pulsar apache/pulsar
