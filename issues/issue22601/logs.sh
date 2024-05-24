#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
function ptbx_k_logs() {
  {
    follow_arg="-f"
    if [[ "$no_follow" == "1" ]]; then
       follow_arg=""
    fi
    while read -r namespace name; do
      printf "kubectl logs %s -n %s pod/%s | sed -e 's/^/[%s] /'\0" "$follow_arg" "$namespace" "$name" "$name"
    done < <(kubectl get "$@" pods --no-headers -o custom-columns=":metadata.namespace,:metadata.name")
  } | xargs -0 parallel --
}
no_follow=0
if [[ "$1" == "--no-follow" ]]; then
    no_follow=1
    shift
fi
filter="${1:-"extract_exceptions"}"
ptbx_k_logs -n pulsar-testenv -l app=pulsar | { 
    if [[ "$filter" == "extract_exceptions" ]]; then
        $SCRIPT_DIR/extract_exceptions.py
    elif [[ "$filter" == "" ]]; then
        cat
    else
        egrep "$filter"
    fi
}