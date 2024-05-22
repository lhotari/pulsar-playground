#!/bin/bash -xe
cleanup() {
    set +xe
    echo "Terminating all background jobs..."
    kill $(jobs -p)
    wait
    exit 1
}
trap 'cleanup' SIGINT
./consume.sh lari_test &
./consume.sh lari_test2 &
./consume.sh lari_test3 &
wait