# Test environments

## microk8s on Ubuntu

Installing
```
sudo snap install --classic microk8s
sudo usermod -a -G microk8s $USER
```

Configuring (after re-login so that microk8s group membership is effective)
```
microk8s enable host-access
microk8s enable dns
microk8s enable ingress
microk8s enable storage
microk8s enable registry
microk8s enable metallb:10.64.140.43-10.64.140.49
```

Fixing networking
```
# On Ubuntu 20.10 networking is broken. This is a workaround:
sudo iptables -P FORWARD ACCEPT
```

Adding to kubectl
```
microk8s config -l > $HOME/.kube/microk8s.config
chmod 0600 $HOME/.kube/config
```
