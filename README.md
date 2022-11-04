# pulsar-playground
Playground for Pulsar core development with instructions for setting up a k8s test env on your laptop


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
