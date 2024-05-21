#!/bin/bash -xe
source common.sh
pulsar-perf produce persistent://my-tenant/my-namespace/my-topic-1 -r 6000 -kb -s 2000 -bp 2 -bm 1000  -b 1 -mk random