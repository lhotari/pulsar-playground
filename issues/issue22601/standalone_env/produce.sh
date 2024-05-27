#!/bin/bash
. ./common_perf_client.sh
pulsar-perf produce persistent://public/default/my-topic -r 6000 -s 2000 -bp 2 -db -b 1