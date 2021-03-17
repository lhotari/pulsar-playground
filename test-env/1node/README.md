# 1 node test environment

### Initializing chart and values.yaml
```
helm repo add apache https://pulsar.apache.org/charts
helm update
curl https://raw.githubusercontent.com/apache/pulsar-helm-chart/master/examples/values-minikube.yaml > values.yaml
```

### Install
```
helm upgrade --install --namespace pulsar --create-namespace -f values.yaml --set initialize=true pulsar apache/pulsar 
```

### Uninstall
```
helm uninstall pulsar -n pulsar
```

### Find out LoadBalancer IP address for pulsar-proxy (requires a k8s installation with working LB)
```
PULSAR_IP=`kubectl -n pulsar get service pulsar-proxy --output="jsonpath={.status.loadBalancer.ingress[0]['hostname','ip']}"`  
```