package com.github.lhotari.pulsar.playground;

/**
 * Test environment configuration.
 * Override the default values by setting the corresponding environment variables.
 */
public class TestEnvironment {
    /**
     * Pass the PULSAR_HOST environment variable to use default port 6650 and 8080 to connect to Pulsar
     */
    public static final String PULSAR_HOST = System.getenv().getOrDefault("PULSAR_HOST",
            "localhost");
    /**
     * Use the PULSAR_BROKER_URL environment variable to override the default Pulsar broker URL
     */
    public static final String PULSAR_BROKER_URL =
            System.getenv().getOrDefault("PULSAR_BROKER_URL", "pulsar://" + PULSAR_HOST + ":6650/");
    /**
     * Use the PULSAR_SERVICE_URL environment variable to override the default Pulsar service URL for Admin API HTTP
     * requests.
     */
    public static final String PULSAR_SERVICE_URL =
            System.getenv().getOrDefault("PULSAR_SERVICE_URL", "http://" + PULSAR_HOST + ":8080/");
}
