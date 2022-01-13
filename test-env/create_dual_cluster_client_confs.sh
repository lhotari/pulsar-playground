#!/usr/bin/env bash
cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null

function set_current_cluster() {
    current_namespace="$1"
}

function run_command_in_cluster() {
    local admin_script="$1"
    kubectl exec -i -n "$current_namespace" "$(kubectl get pod -n "$current_namespace" -l component=broker -o name | head -1)" -c ${current_namespace}-pulsar-broker -- bash -c "export PATH=/pulsar/bin:\$PATH; ${admin_script}"
}

function create_client_conf_for_cluster() {
    local cid=$1
    set_current_cluster "cluster-${cid}"
    token="$(run_command_in_cluster "cat /pulsar/token-superuser-stripped.jwt")"
    cat > client_cluster-${cid}.conf <<EOF
webServiceUrl=https://cluster-${cid}-pulsar-broker.cluster-${cid}.svc.cluster.local:8443
brokerServiceUrl=pulsar+ssl://cluster-${cid}-pulsar-broker.cluster-${cid}.svc.cluster.local:6651
authPlugin=org.apache.pulsar.client.impl.auth.AuthenticationToken
authParams=token:${token}
tlsAllowInsecureConnection=true
EOF
}

create_client_conf_for_cluster a
create_client_conf_for_cluster b
