#!/bin/bash
DATE_PART=$(date +%s)
set -x
pulsar-admin topics stats persistent://public/default/my-topic | tee topic_stats_${DATE_PART}.json
pulsar-admin topics stats-internal persistent://public/default/my-topic | tee topic_internal_stats_${DATE_PART}.json