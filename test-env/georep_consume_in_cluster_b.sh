#!/bin/bash
pulsar-perf consume --service-url pulsar://cluster-b-pulsar-proxy.cluster-b.svc.cluster.local:6650 "$@" persistent://georep/replicated/perftest
