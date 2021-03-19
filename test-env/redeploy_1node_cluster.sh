#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
echo 'Uninstalling old installation'
helm uninstall pulsar -n pulsar && { echo 'Waiting 10 seconds...'; sleep 10; }
echo 'Removing pulsar namespace'
kubectl delete namespaces/pulsar
echo 'Installing 1node cluster'
helm upgrade --install --namespace pulsar --create-namespace -f $SCRIPT_DIR/1node/values.yaml -f $SCRIPT_DIR/custom_images.yaml --set initialize=true pulsar apache/pulsar
