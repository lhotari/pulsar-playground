#!/bin/bash
# update microk8s context used remotely in private network
# from one machines to a Ubuntu server where microk8s is running
SSH_HOST=${1:-xps}
SERVER_IP=${2:-10.34.1.241}
# remove possible previous entry
kubectl config delete-context microk8s
kubectl config delete-cluster microk8s-cluster
kubectl config delete-user admin
# add new entry
KUBECONFIG=~/.kube/config:<(ssh $SSH_HOST microk8s config -l | sed "s/127.0.0.1/$SERVER_IP/") kubectl config view --flatten > /tmp/kubeconfig.new$$ && mv /tmp/kubeconfig.new$$ ~/.kube/config
chmod 0600 $HOME/.kube/config