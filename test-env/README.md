# Test environments

## microk8s on Ubuntu

### Installing
```
sudo snap install --classic microk8s
sudo usermod -a -G microk8s $USER
```

### Configuring (after re-login so that microk8s group membership is effective)
```
microk8s enable host-access
microk8s enable dns
microk8s enable ingress
microk8s enable storage
microk8s enable registry
microk8s enable metallb:10.64.140.43-10.64.140.49
```

### Fixing networking
```
# On Ubuntu 20.10 networking is broken. This is a workaround:
sudo iptables -P FORWARD ACCEPT
```

### Adding to kubectl
```
microk8s config -l > $HOME/.kube/microk8s.config
chmod 0600 $HOME/.kube/config
```

### Access to microk8s containerd with microk8s group membership
```
# edit gid=0 -> gid=997 to add access to microk8s group
sudo vim /var/snap/microk8s/current/args/containerd-template.toml
microk8s stop
microk8s start
# this should work without sudo now
microk8s.ctr c ls 
```

### Add crictl tool for microk8s containers

Install from https://github.com/kubernetes-sigs/cri-tools/releases

configuration for crictl
```
export CONTAINER_RUNTIME_ENDPOINT=unix:///var/snap/microk8s/common/run/containerd.sock
```

### Enabling ingress with metallb

See https://gist.github.com/djjudas21/ca27aab44231bdebb0e72d30e00553ff

```
wget https://gist.githubusercontent.com/djjudas21/ca27aab44231bdebb0e72d30e00553ff/raw/f73b748cdcdb704aee7acc8af61bdf222111b8e6/ingress-service.yaml
microk8s kubectl apply -f ingress-service.yaml
microk8s kubectl -n ingress get svc
```