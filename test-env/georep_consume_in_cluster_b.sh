#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
export PULSAR_CLIENT_CONF=$SCRIPT_DIR/client_cluster-b.conf
pulsar-perf consume \
    "$@" persistent://georep/replicated/perftest
