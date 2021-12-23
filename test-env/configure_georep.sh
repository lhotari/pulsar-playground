#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
command="${1:-start}"
norestart="${2:-1}"

cluster_a_id=cluster-a-pulsar
cluster_a_hostname=cluster-a-pulsar-proxy.cluster-a.svc.cluster.local

cluster_b_id=cluster-b-pulsar
cluster_b_hostname=cluster-b-pulsar-proxy.cluster-b.svc.cluster.local

georep_tenant="georep"
georep_namespace="${georep_tenant}/replicated"
georep_topicbase="perftest"
georep_topic="persistent://${georep_namespace}/${georep_topicbase}"
partition_count=200

function set_current_cluster() {
    current_namespace="$1"
}
set_current_cluster "cluster-a"


function run_command_in_cluster() {
    local admin_script="$1"
    kubectl exec -i -n "$current_namespace" "$(kubectl get pod -n "$current_namespace" -l component=broker -o name | head -1)" -- bash -xc "export PATH=/pulsar/bin:\$PATH; ${admin_script}"
}

function stop_georep() {
    local own_cluster_name="$1"

    read -r -d '' admin_script <<EOF
pulsar-admin namespaces set-clusters -c ${own_cluster_name} ${georep_namespace}
EOF

    run_command_in_cluster "${admin_script}"
}

function delete_georep_peer_cluster() {
    local peer_cluster_name="$1"

    read -r -d '' admin_script <<EOF
# delete peer cluster
pulsar-admin clusters delete ${peer_cluster_name}
EOF

    run_command_in_cluster "${admin_script}"
}

function unload_georep_topic() {
    read -r -d '' admin_script <<EOF
source /pulsar/conf/client.conf
pulsar_jwt=\$(cat /pulsar/token-superuser-stripped.jwt)

# unload individual partitions
curl -L --retry 3 -k -H "Authorization: Bearer \${pulsar_jwt}" --parallel --parallel-immediate --parallel-max 10 -X PUT "\${webServiceUrl}admin/v2/persistent/${georep_namespace}/${georep_topicbase}-partition-[0-$((partition_count - 1))]/unload"
# unload partitioned topic
curl -L --retry 3 -k -H "Authorization: Bearer \${pulsar_jwt}" -X PUT "\${webServiceUrl}admin/v2/persistent/${georep_namespace}/${georep_topicbase}/unload"
EOF

    run_command_in_cluster "${admin_script}"
}

function delete_georep_topic() {
    read -r -d '' admin_script <<EOF
source /pulsar/conf/client.conf
pulsar_jwt=\$(cat /pulsar/token-superuser-stripped.jwt)

# delete partitioned topic
curl -L --retry 3 -k -H "Authorization: Bearer \${pulsar_jwt}" -X DELETE "\${webServiceUrl}admin/v2/persistent/${georep_namespace}/${georep_topicbase}/partitions?force=true&deleteSchema=false"
# delete individual partitions
curl -L --retry 3 -k -H "Authorization: Bearer \${pulsar_jwt}" --parallel --parallel-immediate --parallel-max 10 -X DELETE "\${webServiceUrl}admin/v2/persistent/${georep_namespace}/${georep_topicbase}-partition-[0-$((partition_count - 1))]?force=true&deleteSchema=false"
EOF

    run_command_in_cluster "${admin_script}"
}

function delete_georep_namespace() {
    read -r -d '' admin_script <<EOF
source /pulsar/conf/client.conf
pulsar_jwt=\$(cat /pulsar/token-superuser-stripped.jwt)

# force delete namespace
curl -L --retry 3 -k -H "Authorization: Bearer \${pulsar_jwt}" -X DELETE "\${webServiceUrl}admin/v2/namespaces/${georep_namespace}?force=true"
EOF

    run_command_in_cluster "${admin_script}"
}


function set_up_georep (){
    local own_cluster_name="$1"
    local peer_cluster_dns="$2"
    local peer_cluster_name="$3"
   

    echo "Setting up georep for peer cluster ${peer_cluster_name}, creating ${georep_topic} and removing the existing subscription..."

    read -r -d '' admin_script <<EOF
pulsar-admin tenants create ${georep_tenant}
pulsar-admin namespaces create -b 64 ${georep_namespace}
pulsar-admin clusters create --tls-enable --tls-allow-insecure --tls-trust-certs-filepath /pulsar/certs/tls.crt --auth-plugin org.apache.pulsar.client.impl.auth.AuthenticationToken --auth-parameters file:///pulsar/token-superuser-stripped.jwt --broker-url-secure pulsar+ssl://${peer_cluster_dns}:6651 --url-secure https://${peer_cluster_dns}:8443 ${peer_cluster_name}
#pulsar-admin clusters create --broker-url pulsar://${peer_cluster_dns}:6650 --url http://${peer_cluster_dns}:8080 ${peer_cluster_name}
pulsar-admin tenants update --allowed-clusters "${cluster_a_id},${cluster_b_id}" ${georep_tenant}
pulsar-admin namespaces set-clusters -c ${own_cluster_name},${peer_cluster_name} ${georep_namespace}
pulsar-admin namespaces get-clusters ${georep_namespace}
pulsar-admin topics create-partitioned-topic -p $partition_count ${georep_topic}
EOF

    run_command_in_cluster "${admin_script}"
}

function unconfigure_georep() {
    echo "Unconfiguring geo-replication..."
    if [[ $norestart -ne 1 ]]; then
        echo "Restarting between stages."
    else
        echo "No restarts will be used."
    fi

    # georeplication has to be stopped before deleting topics
    set_current_cluster cluster-a
    stop_georep "${cluster_a_id}"
    set_current_cluster cluster-b
    stop_georep "${cluster_b_id}"
    set_current_cluster cluster-a
    delete_georep_peer_cluster "${cluster_b_id}"
    set_current_cluster cluster-b
    delete_georep_peer_cluster "${cluster_a_id}"

    # wait for georep to stop
    echo "Wait 10 seconds..."
    sleep 10

    # delete topics
    set_current_cluster cluster-a
    unload_georep_topic
    delete_georep_topic
    delete_georep_namespace

    if [[ $norestart -ne 1 ]]; then
        # restart brokers
        kubectl rollout restart statefulset -n cluster-a cluster-a-pulsar-broker
    fi

    set_current_cluster cluster-b
    unload_georep_topic
    delete_georep_topic
    delete_georep_namespace

    if [[ $norestart -ne 1 ]]; then
        # restart brokers
        kubectl rollout restart statefulset -n cluster-b cluster-b-pulsar-broker

        echo "Wait for brokers to restart..."
        set_current_cluster cluster-a
        kubectl rollout status statefulset -n cluster-a cluster-a-pulsar-broker
        set_current_cluster cluster-b
        kubectl rollout status statefulset -n cluster-b cluster-b-pulsar-broker
    fi

    echo "Wait 10 seconds"
    sleep 10
}

function configure_georep() {
    echo "Configuring geo-replication..."

    # setup georeplication and create topics
    set_current_cluster cluster-a
    set_up_georep "${cluster_a_id}" "${cluster_b_hostname}" "${cluster_b_id}" 
    set_current_cluster cluster-b
    set_up_georep "${cluster_b_id}" "${cluster_a_hostname}" "${cluster_a_id}"

    echo "Wait 15 seconds..."
    sleep 15
}

if [[ $command == "start" ]]; then
    configure_georep
else
    unconfigure_georep
fi
