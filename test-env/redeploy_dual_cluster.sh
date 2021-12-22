#!/bin/bash -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
$SCRIPT_DIR/check_kubectx.sh || exit 1

function install_pulsar_cluster() {
  local ns=$1
  shift
  DEPLOYMENT_NAMESPACE="${ns}" DEPLOYMENT_NAME="${ns}-testenv-deployment" $SCRIPT_DIR/redeploy_1node_cluster.sh "$@"
}

function pulsar_admin() {
  kubectl exec -i -t -n cluster-a cluster-a-pulsar-broker-0 -- \
   bin/pulsar-admin "${@}"
}

install_pulsar_cluster cluster-a "$@"
echo -n "Wait until cluster-a broker is available..."
until nslookup cluster-a-pulsar-broker.cluster-a.svc.cluster.local >/dev/null 2>&1; do
  echo -n "."
  sleep 3;
done;
echo
install_pulsar_cluster cluster-b "$@"
echo -n "Wait until cluster-b broker is available..."
until nslookup cluster-b-pulsar-broker.cluster-b.svc.cluster.local >/dev/null 2>&1; do
  echo -n "."
  sleep 3;
done;
echo
