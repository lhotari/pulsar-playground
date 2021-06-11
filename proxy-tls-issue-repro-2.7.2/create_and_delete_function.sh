#!/bin/bash -e
FUNC_NAME="func$(date +%s)"
echo "Creating $FUNC_NAME"
pulsar-admin functions create --jar $PWD/api-examples.jar --tenant public --namespace default --className org.apache.pulsar.functions.api.examples.ExclamationFunction --inputs fun-in-${FUNC_NAME} --output fun-out-${FUNC_NAME} --name $FUNC_NAME
echo "Waiting 10 seconds"
sleep 10
echo "Deleting $FUNC_NAME"
pulsar-admin functions delete --tenant public --namespace default --name $FUNC_NAME
