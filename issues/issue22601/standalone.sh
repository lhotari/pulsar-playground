#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
OPTS="-XX:ActiveProcessorCount=8" PULSAR_STANDALONE_USE_ZOOKEEPER=1 bin/pulsar standalone -c $SCRIPT_DIR/standalone_tls.conf --wipe-data --num-bookies 3 -nss -nfw 2>&1 | tee standalone.log