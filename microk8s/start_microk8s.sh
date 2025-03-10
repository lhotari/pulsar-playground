#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
sudo snap enable microk8s
sudo microk8s start
echo "Waiting 5 seconds"
sleep 5
echo "Reconfiguring dns"
"$SCRIPT_DIR"/reconfigure_dns.sh
