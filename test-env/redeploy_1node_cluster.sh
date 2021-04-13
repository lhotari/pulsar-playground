#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
echo 'Uninstalling old installation'
helm uninstall pulsar -n pulsar && { echo 'Waiting 10 seconds...'; sleep 10; }
echo 'Removing pulsar namespace'
kubectl delete namespaces/pulsar
echo 'Installing 1node cluster'
custom_images=${1:-$SCRIPT_DIR/custom_images.yaml}
echo "Using custom images from ${custom_images} file"
helm upgrade --install --namespace pulsar --create-namespace -f $SCRIPT_DIR/1node/values.yaml -f $custom_images --set initialize=true pulsar apache/pulsar
