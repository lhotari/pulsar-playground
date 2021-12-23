#!/bin/bash
pulsar-perf produce \
    --service-url pulsar+ssl://cluster-a-pulsar-proxy.cluster-a.svc.cluster.local:6651 \
    --tls-allow-insecure \
    --auth_plugin org.apache.pulsar.client.impl.auth.AuthenticationToken \
    --auth-params "{token: \"$(kubectl exec -n cluster-a pod/cluster-a-pulsar-broker-0 -c cluster-a-pulsar-broker -- cat /pulsar/token-superuser-stripped.jwt)\"}" \
    "$@" persistent://georep/replicated/perftest

