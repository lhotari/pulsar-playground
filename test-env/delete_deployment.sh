#!/bin/bash
echo 'Uninstalling old installation'
helm uninstall pulsar -n pulsar && { echo 'Waiting 10 seconds...'; sleep 10; }
echo 'Removing pulsar namespace'
kubectl delete namespaces/pulsar --grace-period=0 --force