#!/bin/bash
sudo rm /etc/systemd/resolved.conf.d/00-k8s-dns-resolver.conf
sudo systemctl restart systemd-resolved
sudo microk8s stop
sudo snap disable microk8s
