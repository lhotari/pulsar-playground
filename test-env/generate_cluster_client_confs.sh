#!/bin/bash
for cluster in cluster-a cluster-b; do
    cat > client_${cluster}.conf <<EOF
webServiceUrl=https://${cluster}-pulsar-proxy.${cluster}.svc.cluster.local:8443
brokerServiceUrl=pulsar+ssl://${cluster}-pulsar-proxy.${cluster}.svc.cluster.local:6651
authPlugin=org.apache.pulsar.client.impl.auth.AuthenticationToken
authParams=token:$(kubectl exec -n ${cluster} pod/${cluster}-pulsar-broker-0 -c ${cluster}-pulsar-broker -- cat /pulsar/token-superuser-stripped.jwt)
tlsAllowInsecureConnection=true
EOF
done
