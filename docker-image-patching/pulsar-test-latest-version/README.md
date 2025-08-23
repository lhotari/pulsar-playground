# Dockerfile for patching apachepulsar/pulsar-test-latest-version

This is useful after downloading pulsar-test-latest-version-image.zst from a CI build and then loaded to local docker

```
cat pulsar-test-latest-version-image.zst.zip | zstd -d | docker load
# tag it to find the original later
docker tag apachepulsar/pulsar-test-latest-version apachepulsar/pulsar-test-latest-version:original-$(date -I)
```

After patching a .nar file you can update the image and re-run the test locally.