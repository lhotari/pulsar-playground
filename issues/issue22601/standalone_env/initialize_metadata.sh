#!/bin/bash
. ./common.sh
cd "$PULSAR_HOME"
bin/pulsar initialize-cluster-metadata \
  --cluster pulsar-cluster-1 \
  --metadata-store zk:localhost:2181 \
  --configuration-metadata-store zk:localhost:2181 \
  --web-service-url http://localhost:8080 \
  --web-service-url-tls https://localhost:8443 \
  --broker-service-url pulsar://localhost:6650 \
  --broker-service-url-tls pulsar+ssl://localhost:6651