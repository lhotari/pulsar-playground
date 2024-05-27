#!/bin/bash
. ./common.sh
cd "$PULSAR_HOME"
bin/pulsar-daemon start bookie
sleep 5
OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" bin/pulsar-daemon start broker
