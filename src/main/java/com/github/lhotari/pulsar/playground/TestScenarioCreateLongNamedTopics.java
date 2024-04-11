package com.github.lhotari.pulsar.playground;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

@Slf4j
public class TestScenarioCreateLongNamedTopics extends TestScenarioCreateTopics {
    public static void main(String[] args) {
        try {
            TestScenarioCreateLongNamedTopics scenario = new TestScenarioCreateLongNamedTopics();
            scenario.numberOfTenants = 10;
            scenario.numberOfNamespaces = 10;
            scenario.numberOfTopics = 10;
            scenario.tenantPrefix = RandomStringUtils.randomAlphabetic(1000);
            scenario.namespacePrefix = RandomStringUtils.randomAlphabetic(1000);
            scenario.topicPrefix = RandomStringUtils.randomAlphabetic(1000);
            scenario.run();
            System.exit(0);
        } catch (Exception e) {
            log.error("Exception in running", e);
            System.exit(1);
        }
    }
}
