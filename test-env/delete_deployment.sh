#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
. $SCRIPT_DIR/test-env.env
$SCRIPT_DIR/check_kubectx.sh || exit 1
echo 'Uninstalling old installation'
helm uninstall "${DEPLOYMENT_NAME}" -n "${DEPLOYMENT_NAMESPACE}" && { echo 'Waiting 10 seconds...'; sleep 10; }
echo 'Removing "${DEPLOYMENT_NAMESPACE}" namespace'
kubectl delete namespaces/"${DEPLOYMENT_NAMESPACE}" --grace-period=0 --force
exit 0