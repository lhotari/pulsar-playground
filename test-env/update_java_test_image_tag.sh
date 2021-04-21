#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
# requires yq, https://mikefarah.gitbook.io/yq/
# https://mikefarah.gitbook.io/yq/operators/assign-update#updated-multiple-paths
cd $SCRIPT_DIR
java_test_image_tag="${1:-$(docker images localhost:32000/apachepulsar/java-test-image --format '{{ .Tag }}' | head -n 1)}" yq eval -i '(.images.zookeeper.tag, .images.bookie.tag, .images.autorecovery.tag, .images.broker.tag, .images.proxy.tag, .images.functions.tag, .pulsar_metadata.image.tag) |= env(java_test_image_tag)' java_test_images.yaml

