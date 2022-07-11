# Test environments

## Usage notes

### Enabling Debugger

The syntax for enabling a debugger on port 5005 in Java:

- For Java 11 (Java 9+): `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`
- Java 8: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005`

For debugging the broker, one can add the parameter to the `PULSAR_EXTRA_OPTS` environment variable
defined by `broker.configData.PULSAR_EXTRA_OPTS`:

```yaml
broker:
  configData:
    PULSAR_EXTRA_OPTS: >
      -XX:+UnlockDiagnosticVMOptions
      -XX:+DebugNonSafepoints
      -XX:+FlightRecorder
      -XX:FlightRecorderOptions=stackdepth=1024
      -Dpulsar.allocator.leak_detection=Advanced
      -Dio.netty.leakDetectionLevel=advanced
      -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

## microk8s on Ubuntu

### Installing
```
sudo snap install --classic microk8s
sudo usermod -a -G microk8s $USER
sudo microk8s enable dns
```

### Fixing networking (run after every startup)
```
# On Ubuntu 20.10 networking is broken. This is a workaround:
sudo iptables -P FORWARD ACCEPT
```
Don't use this setting on a public network.

### Fixing high CPU by gvfs-udisk2-volume-monitor process

There's an issue with [microk8s & gvfs-udisks2-volume-monitor](https://github.com/ubuntu/microk8s/issues/500)

The workaround is to ignore loopback devices in udisks by adding this udev rule:
```
sudo tee /etc/udev/rules.d/90-loopback.rules <<EOF
# hide loopback devices from udisks
SUBSYSTEM=="block", DEVPATH=="/devices/virtual/block/loop*", ENV{UDISKS_PRESENTATION_HIDE}="1", ENV{UDISKS_IGNORE}="1"
EOF
```
Rebooting is needed after making this change.

### Change ip range to 172.17.0.0/16 & 172.30.183.0/24 (OPTIONAL, only if you have conflicts with VPN software)

The reason for doing this is that some VPN software conflicts with the default IP ranges that microk8s uses. You can ignore this step completely if you don't have a problem with the default IP ranges.


* [Original source](https://github.com/ubuntu/microk8s/issues/276#issuecomment-754704993)
* [Alternative, official instructions](https://microk8s.io/docs/change-cidr)

#### Configuration:

instructions using these aliases
```bash
alias kubectl='microk8s kubectl'
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

          sed 's/--service-cluster-ip-range=10.152.183.0/--service-cluster-ip-range=172.30.183.0/' -i /var/snap/microk8s/current/args/kube-apiserver


  * Edit `nano /var/snap/microk8s/current/certs/csr.conf.template` and replace `IP.2 = 10.152.183.1` with the the new IP `IP.2 = 172.30.183.1` the kubernetes service will have in the new IP range.

          sed 's/10.152.183.1/172.30.183.1/' -i /var/snap/microk8s/current/certs/csr.conf.template


  * If you are also setting up a proxy, update `nano /var/snap/microk8s/current/args/containerd-env` with the respective IP ranges from:

          # NO_PROXY=10.1.0.0/16,10.152.183.0/24
  * to:

          # NO_PROXY=172.17.0.0/16,172.30.183.0/24
          
          sed 's/10.1.0.0\/16,10.152.183.0\/24/172.17.0.0\/16,172.30.183.0\/24/' -i /var/snap/microk8s/current/args/containerd-env

  * Start all services with `microk8s start`
  * Reload the CNI with `microk8s.kubectl apply -f /var/snap/microk8s/current/args/cni-network/cni.yaml`
  * To enable dns you should make a copy of the dns manifest with `cp /snap/microk8s/current/actions/coredns.yaml /tmp/`
  * In this manifest copy `nano /tmp/coredns.yaml` update the `clusterIP: 10.152.183.10` to this IP `clusterIP: 172.30.183.10` in the new range and replace the `$ALLOWESCALATION` string with `false`.
    `$NAMESERVERS` should be replaced in the `/tmp/coredns.yaml` file with `1.1.1.1 1.0.0.1`.
    
           sed 's/10.152.183.10/172.30.183.10/' -i /tmp/coredns.yaml
           sed 's/$ALLOWESCALATION/false/' -i /tmp/coredns.yaml
           sed 's/$NAMESERVERS/1.1.1.1 1.0.0.1/' -i /tmp/coredns.yaml

  * Apply the manifest with `microk8s.kubectl apply -f /tmp/coredns.yaml`
  * Add/Modify the following two arguments on the kubelt arguments at `nano /var/snap/microk8s/current/args/kubelet`:
         
        cat >> /var/snap/microk8s/current/args/kubelet << EOF
        --cluster-domain cluster.local
        --cluster-dns 172.30.183.10
        EOF

  * Restart MicroK8s with `microk8s stop; microk8s start`.

2. The IP range pods get their IPs from. By default this is set to `10.1.0.0/16`. To change this IP range you need to:
  * Edit `nano /var/snap/microk8s/current/args/kube-proxy` and update the `--cluster-cidr=10.1.0.0/16` argument to `--cluster-cidr=172.17.0.0/16`.

        sed 's/10.1.0.0/172.17.0.0/' -i /var/snap/microk8s/current/args/kube-proxy

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

        sed 's/10.1.0.0/172.17.0.0/' -i /var/snap/microk8s/current/args/cni-network/cni.yaml

  * Apply the above yaml with `microk8s.kubectl apply -f /var/snap/microk8s/current/args/cni-network/cni.yaml`.

  * Remove default-ipv4-ippool (it will get recreated after startup)

        microk8s.kubectl delete ippool/default-ipv4-ippool

  * Restart MicroK8s with `microk8s stop; microk8s start`.

The `default-ipv4-ippool`  is recreated on reboot with the settings from `/var/snap/microk8s/current/args/cni-network/cni.yaml`
Verify the pod IP-s! They should use the new IP range:
``` bash
microk8s.kubectl get pod -o wide --all-namespaces
```

### Configuring (after re-login so that microk8s group membership is effective)
```
microk8s enable ingress
microk8s enable hostpath-storage
microk8s enable registry
microk8s enable metallb:192.168.140.43-192.168.140.49
```

### Adding to kubectl
```
# remove possible previous entry
kubectl config delete-context microk8s
kubectl config delete-cluster microk8s-cluster
kubectl config delete-user admin
KUBECONFIG=~/.kube/config:<(microk8s config -l) kubectl config view --flatten > /tmp/kubeconfig.new$$ && mv /tmp/kubeconfig.new$$ ~/.kube/config
chmod 0600 $HOME/.kube/config
```

### Access to microk8s containerd with microk8s group membership
```
# edit gid=0 -> gid=997 to add access to microk8s group
sudo sed 's/gid = 0/gid = 997/' -i /var/snap/microk8s/current/args/containerd-template.toml
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

Originally based on instructions in
https://carlos-algms.medium.com/how-to-configure-a-linux-host-to-resolve-and-access-kubernetes-services-by-name-e1741e1247bd

```
sudo mkdir -p /etc/systemd/resolved.conf.d
sudo tee /etc/systemd/resolved.conf.d/00-k8s-dns-resolver.conf <<EOF
[Resolve]
Cache=yes
CacheFromLocalhost=yes
DNS=172.30.183.10
Domains=~default.svc.cluster.local ~svc.cluster.local ~cluster.local
DNSOverTLS=false
EOF
sudo service systemd-resolved restart
```

## Enabling k8s feature gates for microk8s

edit the https://kubernetes.io/docs/reference/command-line-tools-reference/feature-gates/#feature-gates-for-alpha-or-beta-features argument in `/var/snap/microk8s/current/args/kube-apiserver`
```
vim /var/snap/microk8s/current/args/kube-apiserver
```


## Installing promtail and Loki

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
helm upgrade --install loki grafana/loki
helm upgrade --install promtail grafana/promtail --set "loki.serviceName=loki" --set "config.lokiAddress=http://loki.default.svc.cluster.local:3100/loki/api/v1/push"
```

Use "http://loki.default.svc.cluster.local:3100" in Grafana for the Loki datasource.
