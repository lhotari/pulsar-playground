#!/bin/bash
# run in pulsar directory where pulsar has been built with
# mvn -Pcore-modules,-main -T 1C clean install -DskipTests -Dspotbugs.skip=true
# remove data between test runs with "rm -rf data"
PULSAR_MEM="-Xms2g -Xmx4g -XX:MaxDirectMemorySize=6g" PULSAR_GC="-XX:+UseG1GC -XX:+PerfDisableSharedMem -XX:+AlwaysPreTouch" PULSAR_EXTRA_OPTS="-Djute.maxbuffer=20000000" PULSAR_STANDALONE_USE_ZOOKEEPER=1 bin/pulsar standalone -nss -nfw 2>&1 | tee standalone.log