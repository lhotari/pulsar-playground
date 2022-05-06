#!/bin/bash
# deletes bookkeeper and zookeeper PVCs 
# you should do helm upgrade with "--set initialize=true" in the parameters to reinitialize Zookeeper metadata
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
. $SCRIPT_DIR/test-env.env
$SCRIPT_DIR/check_kubectx.sh || exit 1
for node in $(kubectl get nodes -o=name); do
    kubectl cordon $node
done
kubectl -n "${DEPLOYMENT_NAMESPACE}" delete pods -l app=pulsar --wait=true --force
kubectl -n "${DEPLOYMENT_NAMESPACE}" delete sts -l 'component in (bookkeeper,zookeeper,function)' --wait=true --force
kubectl -n "${DEPLOYMENT_NAMESPACE}" delete pvc -l 'component in (bookkeeper,zookeeper,function)' --wait=true --force
for node in $(kubectl get nodes -o=name); do
    kubectl uncordon $node
done
