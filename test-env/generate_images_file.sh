#!/bin/bash
tag="${1:-"latest"}"
repository="${2:-"datastax/lunastreaming"}"

cat <<EOF
image:
  zookeeper:
    repository: $repository
    tag: $tag
  bookkeeper:
    repository: $repository
    tag: $tag
  autorecovery:
    repository: $repository
    tag: $tag
  broker:
    repository: $repository
    tag: $tag
  brokerSts:
    repository: $repository
    tag: $tag
  proxy:
    repository: $repository
    tag: $tag
  function:
    repository: $repository
    tag: $tag
pulsar_metadata:
  image:
    repository: $repository
    tag: $tag
images:
  zookeeper:
    repository: $repository
    tag: $tag
  bookie:
    repository: $repository
    tag: $tag
  autorecovery:
    repository: $repository
    tag: $tag
  broker:
    repository: $repository
    tag: $tag
  proxy:
    repository: $repository
    tag: $tag
  functions:
    repository: $repository
    tag: $tag
EOF
