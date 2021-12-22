#!/bin/bash
# requires yq, https://mikefarah.gitbook.io/yq/
# https://mikefarah.gitbook.io/yq/operators/assign-update#updated-multiple-paths
image_tag="$1" yq eval -i '(.images.zookeeper.tag, .images.bookie.tag, .images.autorecovery.tag, .images.broker.tag, .images.proxy.tag, .images.functions.tag, .pulsar_metadata.image.tag, .image.broker.tag, .image.brokerSts.tag, .image.function.tag, .image.zookeeper.tag, .image.bookkeeper.tag, .image.proxy.tag) |= env(image_tag)' $2

