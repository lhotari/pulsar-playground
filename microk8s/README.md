# microk8s

## Installation and configuration

### Installing microk8s

Installing microk8s requires snapd. It is installed by default on Ubuntu. If not, install it using:

```shell
sudo apt install snapd
```

At https://snapcraft.io/microk8s, you can find installation instructions for other operating systems.
For Amazon Linux, you would need https://github.com/bboozzoo/snapd-amazon-linux .

After snapd is installed, install microk8s using:

```shell
sudo snap install microk8s --classic --channel=1.29/stable
```

Add user to microk8s group:

```shell
sudo usermod -a -G microk8s $USER
```

Enable required microk8s features:

```shell
sudo microk8s enable hostpath-storage registry cert-manager
sudo microk8s enable metallb:192.168.140.43-192.168.140.49
```

### Install tooling

Assuming https://brew.sh/ is used to install the tooling. Please check prerequisites for installing [Homebrew on Linux](https://docs.brew.sh/Homebrew-on-Linux#requirements).

```shell
brew install kubernetes-cli helm yq jq k9s kubectx
```

### Adding microk8s context to kubectl (~/.kube/config)

```shell
# remove possible previous entry
kubectl config delete-context microk8s
kubectl config delete-cluster microk8s-cluster
kubectl config delete-user admin
# add new entry
KUBECONFIG=~/.kube/config:<(microk8s config -l) kubectl config view --flatten > /tmp/kubeconfig.new$$ && mv /tmp/kubeconfig.new$$ ~/.kube/config
chmod 0600 $HOME/.kube/config
```

### Installing Pulsar on microk8s

```shell
helm repo add apache-pulsar https://pulsar.apache.org/charts
helm repo update
# get default values
helm show values apache-pulsar/pulsar > pulsar-values.yaml
# disable anti-affinity so that all pods are scheduled on the same node
yq -i '.affinity.anti_affinity=false' pulsar-values.yaml
# enable Pulsar Functions support
yq -i ".components.functions=true" pulsar-values.yaml
# set Pulsar image tag (== version)
yq -i '.defaultPulsarImageTag="4.0.1"' pulsar-values.yaml
# install pulsar
helm upgrade --install --namespace pulsar --create-namespace pulsar apache-pulsar/pulsar --values pulsar-values.yaml
```

### Enabling DNS from local environment to k8s DNS

Originally based on instructions in
https://carlos-algms.medium.com/how-to-configure-a-linux-host-to-resolve-and-access-kubernetes-services-by-name-e1741e1247bd

```shell
sudo mkdir -p /etc/systemd/resolved.conf.d
sudo tee /etc/systemd/resolved.conf.d/00-k8s-dns-resolver.conf <<EOF
[Resolve]
Cache=yes
CacheFromLocalhost=yes
DNS=$(kubectl get pods -l k8s-app=kube-dns -n kube-system -o jsonpath='{.items[0].status.podIP}')
Domains=~default.svc.cluster.local ~svc.cluster.local ~cluster.local
DNSOverTLS=false
EOF
sudo systemctl restart systemd-resolved
```

validate DNS resolution from local environment to k8s DNS:

```shell
nslookup pulsar-broker.pulsar.svc.cluster.local
```

## Optional configurations (not required for running Pulsar)

### Installing Pulsar Resources Operator

```shell
helm repo add streamnative https://charts.streamnative.io
helm repo update
helm upgrade --install --namespace pulsar --version 0.6.2 pulsar-resources-operator streamnative/pulsar-resources-operator
```

```shell
cat <<EOF | kubectl apply -n pulsar -f -
apiVersion: resource.streamnative.io/v1alpha1
kind: PulsarConnection
metadata:
  name: pulsar-connection
spec:
  adminServiceURL: http://pulsar-broker.pulsar.svc.cluster.local:8080
  brokerServiceURL: pulsar://pulsar-broker.pulsar.svc.cluster.local:6650
  clusterName: pulsar-cluster
EOF
```

### Installing Function Mesh

```shell
helm repo add function-mesh http://charts.functionmesh.io/
helm repo update
# check https://charts.functionmesh.io/index.yaml for versions
helm upgrade --install --namespace pulsar --version 0.2.29 function-mesh function-mesh/function-mesh-operator
```