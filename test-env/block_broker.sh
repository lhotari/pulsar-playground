#!/bin/bash
COMMAND="${1:-block}"
PODNAME="${2:-pulsar-testenv-deployment-broker-0}"
KUBECTLCOMMAND="delete"
if [[ "$COMMAND" = "block" ]]; then
    KUBECTLCOMMAND="apply"
fi
microk8s.kubectl $KUBECTLCOMMAND -f - <<EOF
apiVersion: crd.projectcalico.org/v1
kind: NetworkPolicy
metadata:
  name: block-$PODNAME
  namespace: pulsar-testenv
spec:
  selector: statefulset.kubernetes.io/pod-name == '$PODNAME'
  types:
  - Ingress
  - Egress
  egress:
  - action: Deny
    protocol: TCP
    destination:
      ports:
      - 2888
      - 3888
      - 2181
      - 6650
  ingress:
  - action: Deny
    protocol: TCP
    destination:
      ports:
      - 2888
      - 3888
      - 2181
      - 6650
EOF
