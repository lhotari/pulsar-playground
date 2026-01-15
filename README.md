# pulsar-playground
Playground for Pulsar core development with instructions for setting up a k8s test env on your laptop

## Running local Pulsar standalone in docker

For many tests, it's sufficient to run a Pulsar standalone in docker. Here's an example command to run a Pulsar standalone in docker:
```bash
```
docker run --rm -it -e PULSAR_STANDALONE_USE_ZOOKEEPER=1 -p 8080:8080 -p 6650:6650 apachepulsar/pulsar:latest /pulsar/bin/pulsar standalone -nss -nfw
```

## Test scenarios

Building a single fat jar `build/libs/pulsar-playground-all.jar`
```bash
./gradlew shadowJar
```

### TestScenarioCreateTopics

Getting help:
```bash
java -cp build/libs/pulsar-playground-all.jar com.github.lhotari.pulsar.playground.TestScenarioCreateTopics -h
```

Running:
```bash
java -cp build/libs/pulsar-playground-all.jar com.github.lhotari.pulsar.playground.TestScenarioCreateTopics
```
