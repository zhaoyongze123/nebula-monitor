package com.nebula.server.diagnosis;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diagnosis Configuration - Loads and manages diagnosis settings from application.properties
 */
public class DiagnosisConfig {
    private static final Logger logger = Logger.getLogger(DiagnosisConfig.class.getName());
    private static final DiagnosisConfig INSTANCE = new DiagnosisConfig();
    private final Properties properties;

    private DiagnosisConfig() {
        this.properties = loadProperties();
    }

    /**
     * Load properties from application.properties
     */
    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                props.load(input);
                logger.info("Loaded application.properties successfully");
            } else {
                logger.warning("application.properties not found in classpath");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load application.properties", e);
        }
        return props;
    }

    /**
     * Get singleton instance
     */
    public static DiagnosisConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Get string property with default fallback
     */
    private String getProperty(String key, String defaultValue) {
        String value = System.getenv().get(key);
        if (value != null) {
            return value;
        }
        value = properties.getProperty(key);
        // Support environment variable substitution like ${VAR_NAME}
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String envVar = value.substring(2, value.length() - 1);
            String envValue = System.getenv(envVar);
            return envValue != null ? envValue : defaultValue;
        }
        return value != null ? value : defaultValue;
    }

    /**
     * Get integer property with default fallback
     */
    private int getIntProperty(String key, int defaultValue) {
        try {
            String value = getProperty(key, null);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid integer property: " + key, e);
            return defaultValue;
        }
    }

    /**
     * Get long property with default fallback
     */
    private long getLongProperty(String key, long defaultValue) {
        try {
            String value = getProperty(key, null);
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid long property: " + key, e);
            return defaultValue;
        }
    }

    /**
     * Get boolean property with default fallback
     */
    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key, null);
        if (value == null) {
            return defaultValue;
        }
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes");
    }

    // ============ AI Configuration Getters ============

    public boolean isAiDiagnosisEnabled() {
        return getBooleanProperty("nebula.ai.enabled", true);
    }

    public String getAiBaseUrl() {
        return getProperty("nebula.ai.baseurl", "https://models.inference.ai.azure.com");
    }

    public String getAiApiKey() {
        String key = getProperty("nebula.ai.api-key", null);
        if (key == null) {
            logger.warning("AI API key not configured! Set NEBULA_AI_KEY environment variable.");
        }
        return key;
    }

    public String getAiModel() {
        return getProperty("nebula.ai.model", "gpt-4o-mini");
    }

    public long getSlowTraceThreshold() {
        return getLongProperty("nebula.ai.threshold", 1000L); // Default: 1000ms
    }

    public long getAiCallTimeout() {
        return getLongProperty("nebula.ai.timeout", 10000L); // Default: 10s
    }

    public int getAiRetryCount() {
        return getIntProperty("nebula.ai.retry-count", 3);
    }

    public long getAiRetryInterval() {
        return getLongProperty("nebula.ai.retry-interval", 500L); // Default: 500ms
    }

    // ============ Thread Pool Configuration Getters ============

    public int getThreadPoolCoreSize() {
        return getIntProperty("nebula.diagnosis.thread-pool-core", 10);
    }

    public int getThreadPoolMaxSize() {
        return getIntProperty("nebula.diagnosis.thread-pool-max", 50);
    }

    public int getThreadPoolQueueCapacity() {
        return getIntProperty("nebula.diagnosis.thread-pool-queue", 1000);
    }

    // ============ Metrics Collection Configuration Getters ============

    public long getMetricsCollectionInterval() {
        return getLongProperty("nebula.diagnosis.metrics-collection-interval", 1000L);
    }

    public long getComparativeWindow() {
        return getLongProperty("nebula.diagnosis.comparative-window", 300000L); // Default: 5 minutes
    }

    public int getComparativeSamples() {
        return getIntProperty("nebula.diagnosis.comparative-samples", 10);
    }

    // ============ Redis Configuration Getters ============

    public String getRedisHost() {
        return getProperty("nebula.redis.host", "localhost");
    }

    public int getRedisPort() {
        return getIntProperty("nebula.redis.port", 6379);
    }

    public int getRedisTimeout() {
        return getIntProperty("nebula.redis.timeout", 2000);
    }

    public int getRedisPoolSize() {
        return getIntProperty("nebula.redis.pool-size", 30);
    }

    // ============ Elasticsearch Configuration Getters ============

    public String getElasticsearchEndpoints() {
        return getProperty("nebula.elasticsearch.endpoints", "http://localhost:9200");
    }

    public int getElasticsearchTimeout() {
        return getIntProperty("nebula.elasticsearch.timeout", 5000);
    }

    @Override
    public String toString() {
        return "DiagnosisConfig{" +
                "aiEnabled=" + isAiDiagnosisEnabled() +
                ", aiBaseUrl='" + getAiBaseUrl() + '\'' +
                ", aiModel='" + getAiModel() + '\'' +
                ", slowTraceThreshold=" + getSlowTraceThreshold() + "ms" +
                ", aiCallTimeout=" + getAiCallTimeout() + "ms" +
                ", threadPoolCore=" + getThreadPoolCoreSize() +
                ", threadPoolMax=" + getThreadPoolMaxSize() +
                '}';
    }
}
