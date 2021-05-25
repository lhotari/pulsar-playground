#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
. $SCRIPT_DIR/test-env.env
if [[ "$(kubectl config current-context)" != "${EXPECTED_KUBECTX}" ]]; then
    echo "Unexpected current kubectl context $(kubectl config current-context). '${EXPECTED_KUBECTX}' is expected. Terminating..."
    exit 1
fi
exit 0