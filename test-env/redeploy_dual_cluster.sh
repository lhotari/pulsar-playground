#!/bin/bash -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
$SCRIPT_DIR/check_kubectx.sh || exit 1

function install_pulsar_cluster() {
  local ns=$1
  shift
  DEPLOYMENT_NAMESPACE="${ns}" DEPLOYMENT_NAME="${ns}-testenv-deployment" $SCRIPT_DIR/redeploy_1node_cluster.sh "$@"
}

function restart_proxies_and_brokers() {
  echo "Kill proxy pods to restart..."
  kubectl delete pod -n cluster-a -l component=proxy
  kubectl delete pod -n cluster-b -l component=proxy

  echo "Wait for proxies to start..."
  kubectl wait --timeout=600s --for=condition=ready pod -n cluster-a -l component=proxy
  kubectl wait --timeout=600s --for=condition=ready pod -n cluster-b -l component=proxy

  echo "Restart brokers..."
  kubectl rollout restart statefulset -n cluster-a cluster-a-pulsar-broker
  kubectl rollout restart statefulset -n cluster-b cluster-b-pulsar-broker
  echo "Waiting for brokers..."
  kubectl rollout status statefulset -n cluster-a cluster-a-pulsar-broker
  kubectl rollout status statefulset -n cluster-b cluster-b-pulsar-broker
}

install_pulsar_cluster cluster-a "$@"
echo -n "Wait until cluster-a broker is available..."
until nslookup cluster-a-pulsar-broker.cluster-a.svc.cluster.local >/dev/null 2>&1; do
  echo -n "."
  sleep 3;
echo
done;
install_pulsar_cluster cluster-b "$@"
echo -n "Wait until cluster-b broker is available..."
until nslookup cluster-b-pulsar-broker.cluster-b.svc.cluster.local >/dev/null 2>&1; do
  echo -n "."
  sleep 3;
done;
echo

echo "Wait for proxies to start..."
kubectl wait --timeout=600s --for=condition=ready pod -n cluster-a -l component=proxy
kubectl wait --timeout=600s --for=condition=ready pod -n cluster-b -l component=proxy

