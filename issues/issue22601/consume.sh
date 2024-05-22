#!/bin/bash -xe
source common.sh
sub="${1:-"angus_test"}"
exec pulsar-perf consume persistent://my-tenant/my-namespace/my-topic-1 -n 10 -sp Earliest -ss "$sub" --batch-index-ack -st Key_Shared