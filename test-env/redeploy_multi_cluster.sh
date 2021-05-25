#!/bin/bash -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
$SCRIPT_DIR/check_kubectx.sh || exit 1
IMAGES_YAML=${1:-$SCRIPT_DIR/java_test_images.yaml}

function remove_pulsar_installation() {
  local ns=${1:-pulsar}
  echo 'Uninstalling old installation'
  helm uninstall $ns -n $ns && {
    echo 'Waiting 10 seconds...'
    sleep 10
  }
  echo 'Removing pulsar namespace'
  kubectl delete namespaces/$ns --grace-period=0 --force
}

function install_global_zk() {
  local ns=${1:-global-zk}
  echo "Installing global zookeeper in $ns"
  helm upgrade --install --namespace $ns --create-namespace \
    -f $SCRIPT_DIR/configuration_store.yaml -f $IMAGES_YAML \
    --set namespace=${ns} \
    $ns apache/pulsar
}

function install_pulsar_cluster() {
  local ns=$1
  local global_zk_ns=$2
  shift 2
  echo "Installing 1node cluster in $ns"
  helm upgrade --install --namespace $ns --create-namespace \
    -f $SCRIPT_DIR/1node/values.yaml -f $IMAGES_YAML \
    -f $SCRIPT_DIR/minimal_components.yaml \
    --set namespace=${ns} \
    --set clusterName=${ns} \
    --set pulsar_metadata.configurationStore=${global_zk_ns}-pulsar-zookeeper.${global_zk_ns}.svc.cluster.local \
    --set initialize=true \
    "$@" \
    $ns apache/pulsar
}

function pulsar_admin() {
  kubectl exec -i -t -n cluster-a cluster-a-pulsar-broker-0 -- \
   bin/pulsar-admin "${@}"
}

remove_pulsar_installation cluster-a || true
remove_pulsar_installation cluster-b || true
remove_pulsar_installation global-zk || true

install_global_zk global-zk
install_pulsar_cluster cluster-a global-zk --set components.functions=false
echo -n "Wait until cluster-a broker is available..."
until nslookup cluster-a-pulsar-broker.cluster-a.svc.cluster.local 2>&1 >/dev/null; do
  echo -n "."
  sleep 3;
done;
echo
install_pulsar_cluster cluster-b global-zk --set components.functions=false
echo -n "Wait until cluster-b broker is available..."
until nslookup cluster-b-pulsar-broker.cluster-b.svc.cluster.local 2>&1 >/dev/null; do
  echo -n "."
  sleep 3;
done;
echo
# configure georep tenant and georep/default namespace
pulsar_admin tenants create georep --allowed-clusters cluster-a,cluster-b
pulsar_admin namespaces create georep/default --clusters cluster-a,cluster-b

# if functions are enabled for cluster-b, this would be necessary
# however there are other issues when functions are used across clusters
# workaround issue https://github.com/apache/pulsar/issues/5325
#kubectl exec -i -t -n cluster-a cluster-a-pulsar-broker-0 -- \
#  bash -c 'bin/pulsar-admin namespaces set-clusters --clusters cluster-a,cluster-b public/functions'