#!/bin/bash

function dump_stats() {
    local id="$1"
    local url="$2"
    pulsar-admin --admin-url "$url" topics partitioned-stats --per-partition --get-precise-backlog --get-subscription-backlog-size persistent://georep/replicated/perftest | tee /tmp/replicated_stats_${id}_`date +%s`.json
}

dump_stats a http://cluster-a-pulsar-proxy.cluster-a.svc.cluster.local:8080
dump_stats b http://cluster-b-pulsar-proxy.cluster-b.svc.cluster.local:8080