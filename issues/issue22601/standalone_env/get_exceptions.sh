#!/bin/bash
. ./common.sh
cat $PULSAR_HOME/logs/pulsar-broker-$(hostname).log | ../extract_exceptions.py