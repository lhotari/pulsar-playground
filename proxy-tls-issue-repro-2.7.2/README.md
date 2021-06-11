# repro for Pulsar Proxy issue when uploading large files over TLS

This issue can be reproduced in Pulsar 2.7.2 

### Prerequisite

There should be a Pulsar cluster deployed with TLS enabled. This can deployed to a local minikube/kind/microk8s/k3s cluster.

Follow instructions in [test-env/cert-manager](../test-env/cert-manager) to install cert-manager in the cluster.

Then install Pulsar

Commands executed in test-env directory
```
./redeploy_1node_cluster.sh pulsar_2.7.2_images.yaml values-tls.yaml
```

### Configuring pulsar-admin to connect with TLS to the deployed cluster

make a copy of the client_tls.conf file in test-env directory and edit the settings so that a TLS connection can be established to the 
configured cluster. Set the `PULSAR_CLIENT_CONF` environment variable so that it points to this file:
```
export PULSAR_CLIENT_CONF=$PWD/client_tls.conf
```

test that the connection works:
```
pulsar-admin namespaces list public
```

### Creating a large (>100MB) api-examples.jar file


Copy api-examples.jar from PULSAR_HOME/examples/api-examples.jar and add a 100MB file to it to increase the size
```
# this might work only on linux, uses dd and /dev/random 
./create_large_functions_jar_file.sh
```

### Create functions until there's a failure

```
while ./create_and_delete_function.sh; do :; done
```


