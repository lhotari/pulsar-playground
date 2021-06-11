#!/bin/bash -xe
SIZE=${1:-100}
PULSAR_HOME="${PULSAR_HOME:-$(cd "$(dirname $(which pulsar-admin))"/.. && pwd)}"
cp $PULSAR_HOME/examples/api-examples.jar .
dd if=/dev/random of=randomfile bs=1024 count=$((1024 * $SIZE))
jar -uv0f api-examples.jar randomfile
rm randomfile