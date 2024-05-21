#!/bin/bash -xe
source common.sh
pulsar-perf consume persistent://my-tenant/my-namespace/my-topic-1 -n 10 -sp Latest -ss angus_test --batch-index-ack -st Key_Shared