#!/bin/bash
docker build --platform linux/amd64,linux/arm64 -t lhotari/pulsar-all:4.0.0-preview.1.openssl .
docker push lhotari/pulsar-all:4.0.0-preview.1.openssl