#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
echo 'Upgrading 1node cluster'
IMAGES_YAML=${1:-$SCRIPT_DIR/java_test_images.yaml}
helm upgrade --install --namespace pulsar --create-namespace -f $SCRIPT_DIR/1node/values.yaml -f $IMAGES_YAML pulsar apache/pulsar
