#!/bin/bash -xe
# installs or upgrades chart
helm upgrade --install --namespace pulsar --create-namespace \
  pulsar charts/pulsar \
  --set affinity.anti_affinity=false --set proxy.replicaCount=1 --set victoria-metrics-k8s-stack.grafana.adminPassword=verysecureword123 \
  "$@"
