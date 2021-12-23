#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
export PULSAR_CLIENT_CONF=$SCRIPT_DIR/client_cluster-a.conf
pulsar-perf produce \
    "$@" persistent://georep/replicated/perftest

