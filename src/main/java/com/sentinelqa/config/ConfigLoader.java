package com.sentinelqa.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * Resolves framework configuration with a predictable precedence chain:
 *
 * <pre>
 *   -D system property  >  OS environment variable  >  config/&lt;env&gt;.properties  >  config/framework.properties
 * </pre>
 *
 * Keeping the precedence explicit is what lets the same binary run unchanged on a
 * developer laptop, a Jenkins agent and a GitHub Actions runner.
 */
public final class ConfigLoader {

    private static final String BASE_FILE = "config/framework.properties";
    private static final Properties BASE = new Properties();
    private static final Properties ENV_SPECIFIC = new Properties();

    static {
        load(BASE_FILE, BASE);
        String env = System.getProperty("env", System.getenv().getOrDefault("TEST_ENV", "qa"));
        load("config/" + env + ".properties", ENV_SPECIFIC);
    }

    private ConfigLoader() {
    }

    private static void load(String resource, Properties target) {
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Configuration resource not found on classpath: " + resource);
            }
            target.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read configuration: " + resource, e);
        }
    }

    public static String get(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        if (value == null) {
            value = ENV_SPECIFIC.getProperty(key);
        }
        if (value == null) {
            value = BASE.getProperty(key);
        }
        if (value == null) {
            throw new IllegalStateException("Missing configuration key: " + key);
        }
        return value.trim();
    }

    public static String get(String key, String fallback) {
        try {
            return get(key);
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public static Duration getSeconds(String key) {
        return Duration.ofSeconds(getInt(key));
    }

    public static String activeEnvironment() {
        return System.getProperty("env", System.getenv().getOrDefault("TEST_ENV", "qa"));
    }
}
