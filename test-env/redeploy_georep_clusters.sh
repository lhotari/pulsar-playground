#!/bin/bash
CHART=datastax-pulsar/pulsar ./redeploy_dual_cluster.sh datastax_tls_and_token_auth.yaml "$@" --set brokerSts.replicaCount=3
