#!/bin/bash
. ./common.sh
if [[ -d "${PULSAR_HOME}" ]]; then
    rm -rf "${PULSAR_HOME}/data"
fi
