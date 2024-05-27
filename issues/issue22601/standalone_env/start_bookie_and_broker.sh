#!/bin/bash
. ./common.sh
cd "$PULSAR_HOME"
bin/pulsar-daemon start bookie
sleep 5
bin/pulsar-daemon start broker
