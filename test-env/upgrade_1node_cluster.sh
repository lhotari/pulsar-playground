#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
$SCRIPT_DIR/redeploy_1node_cluster.sh --upgrade "$@"
