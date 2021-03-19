#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
echo 'Upgrading 1node cluster'
helm upgrade --install --namespace pulsar --create-namespace -f $SCRIPT_DIR/1node/values.yaml -f $SCRIPT_DIR/custom_images.yaml pulsar apache/pulsar
