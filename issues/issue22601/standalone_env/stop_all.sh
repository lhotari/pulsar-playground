#!/bin/bash
. ./common.sh
cd "$PULSAR_HOME"
bin/pulsar-daemon stop broker
bin/pulsar-daemon stop bookie
bin/pulsar-daemon stop zookeeper
