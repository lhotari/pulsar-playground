#!/bin/bash
function ptbx_k_logs() {
  {
    while read -r namespace name; do
      printf "kubectl logs -f -n %s pod/%s | sed -e 's/^/[%s] /'\0" "$namespace" "$name" "$name"
    done < <(kubectl get "$@" pods --no-headers -o custom-columns=":metadata.namespace,:metadata.name")
  } | xargs -0 parallel --
}
filter="${1-:'ERROR|WARN|Exception'}"
ptbx_k_logs -n pulsar-testenv -l app=pulsar | { [[ "$filter" != "" ]] && egrep "$filter" || cat; }