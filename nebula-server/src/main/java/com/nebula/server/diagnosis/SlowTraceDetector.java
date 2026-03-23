package com.nebula.server.diagnosis;

import com.nebula.common.MonitoringData;
import java.util.logging.Logger;

/**
 * SlowTraceDetector - Detects whether a monitoring data record qualifies for AI diagnosis
 * Based on duration threshold and system state
 */
public class SlowTraceDetector {
    private static final Logger logger = Logger.getLogger(SlowTraceDetector.class.getName());
    private static final DiagnosisConfig config = DiagnosisConfig.getInstance();

    /**
     * Check if the monitoring data should trigger diagnosis
     * @param data MonitoringData record
     * @return true if diagnosis should be triggered, false otherwise
     */
    public static boolean shouldDiagnose(MonitoringData data) {
        // AI diagnosis feature disabled
        if (!config.isAiDiagnosisEnabled()) {
            return false;
        }

        // Data is null or critical fields are missing
        if (data == null || data.getTraceId() == null) {
            return false;
        }

        // Check if duration exceeds threshold
        long threshold = config.getSlowTraceThreshold();
        if (data.getDuration() < threshold) {
            return false;
        }

        // Trace is not sampled - skip diagnosis
        if (!data.isSampled()) {
            return false;
        }

        logger.fine(() -> String.format(
                "Slow trace detected: traceId=%s, method=%s, duration=%dms (threshold=%dms)",
                data.getTraceId(), data.getMethodName(), data.getDuration(), threshold
        ));

        return true;
    }

    /**
     * Check if all AI configuration is properly set
     * @return true if all required configurations are present, false otherwise
     */
    public static boolean isAiConfigValid() {
        if (!config.isAiDiagnosisEnabled()) {
            logger.warning("AI diagnosis is disabled");
            return false;
        }

        String apiKey = config.getAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.severe("AI API key not configured. Set NEBULA_AI_KEY environment variable.");
            return false;
        }

        String baseUrl = config.getAiBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            logger.severe("AI base URL not configured");
            return false;
        }

        String model = config.getAiModel();
        if (model == null || model.trim().isEmpty()) {
            logger.severe("AI model not configured");
            return false;
        }

        return true;
    }

    /**
     * Get the slow trace threshold in milliseconds
     */
    public static long getThreshold() {
        return config.getSlowTraceThreshold();
    }
}
