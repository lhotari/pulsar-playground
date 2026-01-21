#!/bin/bash
EXPECTED_KUBECTX=${EXPECTED_KUBECTL:-orbstack}
if [[ "$(kubectl config current-context)" != "${EXPECTED_KUBECTX}" ]]; then
    echo "Unexpected current kubectl context $(kubectl config current-context). '${EXPECTED_KUBECTX}' is expected. Terminating..."
    exit 1
fi
helm upgrade --install --create-namespace --namespace=pulsar-dev pulsar apache-pulsar/pulsar \
--values values.yaml \
--wait --timeout 10m --debug