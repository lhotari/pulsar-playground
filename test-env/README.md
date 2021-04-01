# Test environments

## microk8s on Ubuntu

### Installing
```
sudo snap install --classic microk8s
sudo usermod -a -G microk8s $USER
```

### Fixing networking (run after every startup)
```
# On Ubuntu 20.10 networking is broken. This is a workaround:
sudo iptables -P FORWARD ACCEPT
```
Don't use this setting on a public network. 

### Fixing high CPU by gvfs-udisk2-volume-monitor process

There's an issue with microk8s & gvfs-udisks2-volume-monitor

https://github.com/ubuntu/microk8s/issues/500
```
systemctl stop --user gvfs-udisks2-volume-monitor
systemctl disable --user gvfs-udisks2-volume-monitor
```

gvfs-udisk2-volume-monitor is responsible for attaching USB sticks etc. 
to enable and start it again:
```
systemctl enable --user gvfs-udisks2-volume-monitor
systemctl start --user gvfs-udisks2-volume-monitor
```

### Change ip range to 172.17.0.0/16 & 172.30.183.0/24

[Original source](https://github.com/ubuntu/microk8s/issues/276#issuecomment-754704993)

#### Configuration:

instructions using these aliases
```bash
alias kubectl='microk8s kubectl'
alias calicoctl="kubectl exec -i -n kube-system calicoctl -- /calicoctl"
```


1. The range where cluster IPs are from. By default this is set to `10.152.183.1/24`. To change the cluster ip range you need to:

    * Stop all services with `microk8s stop`
    * Clean the current datastore and CNI with:

            sudo su -c bash
            shopt -s extglob
            (cd /var/snap/microk8s/current/var/kubernetes/backend/; rm -v !(cluster.key|cluster.crt) )
            echo "Address: 127.0.0.1:19001" > /var/snap/microk8s/current/var/kubernetes/backend/init.yaml
            rm /var/snap/microk8s/current/args/cni-network/calico-kubeconfig

    * Edit `nano /var/snap/microk8s/current/args/kube-apiserver` and update the argument of the API server `--service-cluster-ip-range=10.152.183.0/24` to `--service-cluster-ip-range=172.30.183.0/24` .
    * Edit `nano /var/snap/microk8s/current/certs/csr.conf.template` and replace `IP.2 = 10.152.183.1` with the the new IP `IP.2 = 172.30.183.1` the kubernetes service will have in the new IP range.
    * If you are also setting up a proxy, update `nano /var/snap/microk8s/current/args/containerd-env` with the respective IP ranges from:

            # NO_PROXY=10.1.0.0/16,10.152.183.0/24
    * to:

            # NO_PROXY=172.17.0.0/16,172.30.183.0/24

    * Start all services with `microk8s start`
    * Reload the CNI with `kubectl apply -f /var/snap/microk8s/current/args/cni-network/cni.yaml`
    * To enable dns you should make a copy of the dns manifest with `cp /snap/microk8s/current/actions/coredns.yaml /tmp/`
    * In this manifest copy `nano /tmp/coredns.yaml` update the `clusterIP: 10.152.183.10` to this IP `clusterIP: 172.30.183.10` in the new range and replace the `$ALLOWESCALATION` string with `false`.
      `$NAMESERVERS` should be replaced in the `/tmp/coredns.yaml` file with `1.1.1.1 1.0.0.1`.
    * Apply the manifest with `kubectl apply -f /tmp/coredns.yaml`
    * Add/Modify the following two arguments on the kubelt arguments at `nano /var/snap/microk8s/current/args/kubelet`:

            --cluster-domain cluster.local
            --cluster-dns 172.30.183.10

    * Restart MicroK8s with `microk8s stop; microk8s start`.

2. The IP range pods get their IPs from. By default this is set to `10.1.0.0/16`. To change this IP range you need to:
    * Edit `nano /var/snap/microk8s/current/args/kube-proxy` and update the `--cluster-cidr=10.1.0.0/16` argument to `--cluster-cidr=172.17.0.0/16`.
    * If you are also setting up a proxy, update `nano /var/snap/microk8s/current/args/containerd-env` with the respective IP ranges from:

            # NO_PROXY=10.1.0.0/16,10.152.183.0/24
    * to:

            # NO_PROXY=172.17.0.0/16,172.30.183.0/24

    * Restart MicroK8s with `microk8s stop; microk8s start`.
    * Edit `nano /var/snap/microk8s/current/args/cni-network/cni.yaml` and replace the new IP range from:

            - name: CALICO_IPV4POOL_CIDR
              value: "10.1.0.0/16"

    * to:

            - name: CALICO_IPV4POOL_CIDR
              value: "172.17.0.0/16"

    * Apply the above yaml with `kubectl apply -f /var/snap/microk8s/current/args/cni-network/cni.yaml`.

    * Restart MicroK8s with `microk8s stop; microk8s start`.

#### Calico CTL install

``` bash
kubectl apply -f https://docs.projectcalico.org/manifests/calicoctl.yaml
```
#### Configure Calico

``` bash
calicoctl get ippool -o wide
calicoctl delete pool default-ipv4-ippool
microk8s stop
microk8s start
```
The `default-ipv4-ippool`  is recreated on reboot with the settings from `/var/snap/microk8s/current/args/cni-network/cni.yaml`
Verify the pod IP-s! They should use the new IP range:
``` bash
kubectl get pod -o wide --all-namespaces
```

### Configuring (after re-login so that microk8s group membership is effective)
```
microk8s enable ingress
microk8s enable storage
microk8s enable registry
microk8s enable metallb:192.168.140.43-192.168.140.49
```

### Adding to kubectl
```
microk8s config -l > $HOME/.kube/config
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

### Enabling DNS from local environment to k8s DNS

Following instructions in 
https://carlos-algms.medium.com/how-to-configure-a-linux-host-to-resolve-and-access-kubernetes-services-by-name-e1741e1247bd

```
sudo mkdir -p /etc/systemd/resolved.conf.d
sudo tee /etc/systemd/resolved.conf.d/00-k8s-dns-resolver.conf <<EOF
[Resolve]
Cache=yes
DNS=172.30.183.10
Domains=default.svc.cluster.local svc.cluster.local cluster.local
EOF
sudo service systemd-resolved restart
```
