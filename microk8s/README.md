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