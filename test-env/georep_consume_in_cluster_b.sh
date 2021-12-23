#!/bin/bash
pulsar-perf consume \
    --service-url pulsar+ssl://cluster-b-pulsar-proxy.cluster-b.svc.cluster.local:6651 \
    --tls-allow-insecure \
    --auth_plugin org.apache.pulsar.client.impl.auth.AuthenticationToken \
    --auth-params "{token: \"$(kubectl exec -n cluster-b pod/cluster-b-pulsar-broker-0 -c cluster-b-pulsar-broker -- cat /pulsar/token-superuser-stripped.jwt)\"}" \
    "$@" persistent://georep/replicated/perftest
