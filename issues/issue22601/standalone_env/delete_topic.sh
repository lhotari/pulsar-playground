#!/bin/bash
. ./common_perf_client.sh
pulsar-admin topics delete persistent://public/default/my-topic
