#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
sudo snap remove --purge microk8s
sudo snap install microk8s --classic --channel=1.32/stable
sudo microk8s enable hostpath-storage registry cert-manager
sudo microk8s enable metallb:192.168.140.43-192.168.140.49
# remove possible previous entry
kubectl config delete-context microk8s
kubectl config delete-cluster microk8s-cluster
kubectl config delete-user admin
# add new entry
KUBECONFIG=~/.kube/config:<(microk8s config -l) kubectl config view --flatten > /tmp/kubeconfig.new$$ && mv /tmp/kubeconfig.new$$ ~/.kube/config
chmod 0600 $HOME/.kube/config
$SCRIPT_DIR/reconfigure_dns.sh
