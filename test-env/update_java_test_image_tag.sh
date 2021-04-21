#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
# requires yq, https://mikefarah.gitbook.io/yq/
# https://mikefarah.gitbook.io/yq/operators/assign-update#updated-multiple-paths
cd $SCRIPT_DIR
./update_image_tag.sh "${1:-$(docker images localhost:32000/apachepulsar/java-test-image --format '{{ .Tag }}' | head -n 1)}" java_test_images.yaml
