#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PULSAR_STANDALONE_USE_ZOOKEEPER=1 bin/pulsar standalone -c $SCRIPT_DIR/standalone_tls.conf -nss -nfw 2>&1 | tee standalone.log