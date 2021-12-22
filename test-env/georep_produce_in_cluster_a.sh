#!/bin/bash
pulsar-perf produce --service-url pulsar://cluster-a-pulsar-proxy.cluster-a.svc.cluster.local:6650 "$@" persistent://georep/replicated/perftest
