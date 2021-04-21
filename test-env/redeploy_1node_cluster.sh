#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
echo 'Uninstalling old installation'
helm uninstall pulsar -n pulsar && { echo 'Waiting 10 seconds...'; sleep 10; }
echo 'Removing pulsar namespace'
kubectl delete namespaces/pulsar
echo 'Installing 1node cluster'
IMAGES_YAML=${1:-$SCRIPT_DIR/java_test_images.yaml}
echo "Using images from ${IMAGES_YAML} file"
helm upgrade --install --namespace pulsar --create-namespace -f $SCRIPT_DIR/1node/values.yaml -f $IMAGES_YAML --set initialize=true pulsar apache/pulsar
