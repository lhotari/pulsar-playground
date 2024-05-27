#!/bin/bash
. ./common_perf_client.sh
pulsar-perf consume persistent://public/default/my-topic -n 10 -sp Latest -ss angus_test -st Key_Shared