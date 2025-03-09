#!/bin/bash
sudo tee /etc/systemd/resolved.conf.d/00-k8s-dns-resolver.conf <<EOF
[Resolve]
Cache=yes
CacheFromLocalhost=yes
DNS=$(kubectl get pods -l k8s-app=kube-dns -n kube-system -o jsonpath='{.items[0].status.podIP}')
Domains=~default.svc.cluster.local ~svc.cluster.local ~cluster.local
DNSOverTLS=false
EOF
sudo systemctl restart systemd-resolved
