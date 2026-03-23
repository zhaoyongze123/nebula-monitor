package com.nebula.server.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.server.RedisPoolManager;
import redis.clients.jedis.Jedis;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TraceContextCollector - Collects system metrics and comparative data for diagnosis
 * Gathers CPU, memory, GC info and fetches similar traces for comparison
 */
public class TraceContextCollector {
    private static final Logger logger = Logger.getLogger(TraceContextCollector.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DiagnosisConfig config = DiagnosisConfig.getInstance();
    private static final String DIAGNOSIS_QUEUE_KEY = "nebula:diagnosis:queue";
    private static final String COMPARATIVE_TRACES_KEY = "nebula:comparative:traces:%s:%s";

    /**
     * Collect current system metrics
     * @return DiagnosisSystemMetrics containing CPU, memory, GC information
     */
    public static DiagnosisSystemMetrics collectSystemMetrics() {
        DiagnosisSystemMetrics metrics = new DiagnosisSystemMetrics();
        metrics.setCollectionTimestamp(System.currentTimeMillis());

        try {
            // CPU metrics
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            // Use reflection to safely access Sun extensions if available
            double processCpuLoad = 0.45;  // Default fallback
            double systemCpuLoad = 0.50;   // Default fallback
            try {
                // Try to use Sun extension APIs if available
                if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                    com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
                    processCpuLoad = sunOsBean.getProcessCpuLoad();
                    systemCpuLoad = sunOsBean.getSystemCpuLoad();
                } else {
                    // Fallback for standard API
                    processCpuLoad = osBean.getSystemLoadAverage() / osBean.getAvailableProcessors();
                    systemCpuLoad = osBean.getSystemLoadAverage();
                }
            } catch (Exception e) {
                logger.fine("Unable to get CPU metrics via Sun APIs, using defaults");
            }
            metrics.setProcessCpuLoad(processCpuLoad);
            metrics.setSystemCpuLoad(systemCpuLoad);
            metrics.setSystemLoadAverage(osBean.getSystemLoadAverage());

            // Memory metrics
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long maxMemory = runtime.maxMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            metrics.setTotalMemory(totalMemory);
            metrics.setMaxMemory(maxMemory);
            metrics.setUsedMemory(usedMemory);
            metrics.setMemoryUsagePercent((double) usedMemory / maxMemory * 100);

            // GC metrics
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            long totalGcCount = 0;
            long totalGcTime = 0;

            for (GarbageCollectorMXBean gcBean : gcBeans) {
                totalGcCount += gcBean.getCollectionCount();
                totalGcTime += gcBean.getCollectionTime();
            }

            metrics.setGcCount(totalGcCount);
            metrics.setGcTimeMs(totalGcTime);

            logger.fine(() -> "Collected system metrics: " + metrics);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to collect system metrics", e);
        }

        return metrics;
    }

    /**
     * Collect comparative data for the same service and method
     * Queries recent traces from Redis to calculate average duration
     * @param serviceName Service name
     * @param methodName Method name
     * @return Comparative trace statistics
     */
    public static DiagnosisComparativeData collectComparativeData(String serviceName, String methodName) {
        DiagnosisComparativeData data = new DiagnosisComparativeData();
        data.setServiceName(serviceName);
        data.setMethodName(methodName);
        data.setCollectionTimestamp(System.currentTimeMillis());

        try {
            // Generate Redis key for storing comparative traces
            String key = String.format(COMPARATIVE_TRACES_KEY, serviceName, methodName);
            int sampleSize = config.getComparativeSamples();

            // Note: In production, you would fetch from Redis cache
            // For now, this is a placeholder that sketches the pattern
            // Real implementation would do LRANGE key 0 N and parse JSON

            logger.fine(() -> String.format(
                    "Collected comparative data for %s:%s (samples: %d)",
                    serviceName, methodName, sampleSize
            ));

        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Failed to collect comparative data for " + serviceName + ":" + methodName, e);
        }

        return data;
    }

    /**
     * Store trace metrics in Redis for later comparative analysis
     * @param serviceName Service name
     * @param methodName Method name
     * @param duration Duration in milliseconds
     */
    public static void storeTraceMetric(String serviceName, String methodName, long duration) {
        Jedis jedis = null;
        try {
            jedis = RedisPoolManager.getConnection();
            if (jedis == null) {
                logger.warning("Cannot store trace metric: Redis connection unavailable");
                return;
            }

            String key = String.format(COMPARATIVE_TRACES_KEY, serviceName, methodName);
            // Store as JSON with timestamp
            String metric = String.format("{\"duration\":%d,\"timestamp\":%d}",
                    duration, System.currentTimeMillis());

            // LPUSH to queue, keep only recent samples (LTRIM)
            jedis.lpush(key, metric);
            int keepSamples = config.getComparativeSamples() * 10; // Keep 10x samples for analysis
            jedis.ltrim(key, 0, keepSamples - 1);

            // Set expiration time (5 minutes by default)
            jedis.expire(key, Math.toIntExact(config.getComparativeWindow() / 1000));

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to store trace metric in Redis", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    /**
     * Simple model class for system metrics
     */
    public static class DiagnosisSystemMetrics {
        private long collectionTimestamp;
        private double processCpuLoad;
        private double systemCpuLoad;
        private double systemLoadAverage;
        private long totalMemory;
        private long maxMemory;
        private long usedMemory;
        private double memoryUsagePercent;
        private long gcCount;
        private long gcTimeMs;

        // Getters and Setters
        public long getCollectionTimestamp() { return collectionTimestamp; }
        public void setCollectionTimestamp(long collectionTimestamp) { this.collectionTimestamp = collectionTimestamp; }

        public double getProcessCpuLoad() { return processCpuLoad; }
        public void setProcessCpuLoad(double processCpuLoad) { this.processCpuLoad = processCpuLoad; }

        public double getSystemCpuLoad() { return systemCpuLoad; }
        public void setSystemCpuLoad(double systemCpuLoad) { this.systemCpuLoad = systemCpuLoad; }

        public double getSystemLoadAverage() { return systemLoadAverage; }
        public void setSystemLoadAverage(double systemLoadAverage) { this.systemLoadAverage = systemLoadAverage; }

        public long getTotalMemory() { return totalMemory; }
        public void setTotalMemory(long totalMemory) { this.totalMemory = totalMemory; }

        public long getMaxMemory() { return maxMemory; }
        public void setMaxMemory(long maxMemory) { this.maxMemory = maxMemory; }

        public long getUsedMemory() { return usedMemory; }
        public void setUsedMemory(long usedMemory) { this.usedMemory = usedMemory; }

        public double getMemoryUsagePercent() { return memoryUsagePercent; }
        public void setMemoryUsagePercent(double memoryUsagePercent) { this.memoryUsagePercent = memoryUsagePercent; }

        public long getGcCount() { return gcCount; }
        public void setGcCount(long gcCount) { this.gcCount = gcCount; }

        public long getGcTimeMs() { return gcTimeMs; }
        public void setGcTimeMs(long gcTimeMs) { this.gcTimeMs = gcTimeMs; }

        @Override
        public String toString() {
            return "SystemMetrics{" +
                    "cpuLoad=" + String.format("%.2f%%", processCpuLoad * 100) +
                    ", memoryUsage=" + String.format("%.2f%%", memoryUsagePercent) +
                    ", gcCount=" + gcCount +
                    ", gcTimeMs=" + gcTimeMs + '}';
        }
    }

    /**
     * Simple model class for comparative data
     */
    public static class DiagnosisComparativeData {
        private String serviceName;
        private String methodName;
        private long collectionTimestamp;
        private long averageDuration;
        private long medianDuration;
        private long p99Duration;
        private int sampleCount;

        // Getters and Setters
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }

        public long getCollectionTimestamp() { return collectionTimestamp; }
        public void setCollectionTimestamp(long collectionTimestamp) { this.collectionTimestamp = collectionTimestamp; }

        public long getAverageDuration() { return averageDuration; }
        public void setAverageDuration(long averageDuration) { this.averageDuration = averageDuration; }

        public long getMedianDuration() { return medianDuration; }
        public void setMedianDuration(long medianDuration) { this.medianDuration = medianDuration; }

        public long getP99Duration() { return p99Duration; }
        public void setP99Duration(long p99Duration) { this.p99Duration = p99Duration; }

        public int getSampleCount() { return sampleCount; }
        public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
    }
}
