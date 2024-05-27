#!/bin/bash
. ./common.sh
tail -f $PULSAR_HOME/logs/pulsar-broker-$(hostname).log | ../extract_exceptions.py