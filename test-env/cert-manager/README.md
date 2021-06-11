# Installing cert-manager

### Commands


From https://cert-manager.io/docs/installation/kubernetes/
```
kubectl create namespace cert-manager
helm repo add jetstack https://charts.jetstack.io
helm repo update

helm install \
  cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --version v1.3.1 \
  --set installCRDs=true
```

ca-issuer.yaml from https://cert-manager.io/docs/configuration/selfsigned/#bootstrapping-ca-issuers

```
kubectl create namespace sandbox
kubectl apply -n sandbox -f ca-issuer.yaml
kubectl get issuers -n sandbox -o wide
```
