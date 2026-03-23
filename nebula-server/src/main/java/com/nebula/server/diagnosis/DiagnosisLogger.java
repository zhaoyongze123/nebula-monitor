package com.nebula.server.diagnosis;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DiagnosisLogger - Records audit logs for diagnosis activities
 * Tracks successful and failed diagnosis attempts
 */
public class DiagnosisLogger {
    private static final Logger logger = Logger.getLogger(DiagnosisLogger.class.getName());
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DIAGNOSIS_LOG_PREFIX = "[DIAGNOSIS]";

    /**
     * Log a successful diagnosis
     */
    public static void logSuccessfulDiagnosis(DiagnosisResult result, long taskDurationMs) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format(
                "%s %s SUCCESS | TraceID=%s | Service=%s | Method=%s | Duration=%dms | CPU=%.1f%% | Memory=%.1f%% | TaskTime=%dms",
                DIAGNOSIS_LOG_PREFIX,
                timestamp,
                result.getTraceId(),
                result.getServiceName(),
                result.getMethodName(),
                result.getDuration(),
                result.getSystemCpuLoad(),
                result.getSystemMemoryUsage(),
                taskDurationMs
        );

        logger.log(Level.INFO, logEntry);

        if (result.getAiDiagnosis() != null) {
            String diagnosisSummary = result.getAiDiagnosis();
            if (diagnosisSummary.length() > 200) {
                diagnosisSummary = diagnosisSummary.substring(0, 200) + "...";
            }
            logger.finest("Diagnosis Result: " + diagnosisSummary);
        }
    }

    /**
     * Log a failed diagnosis
     */
    public static void logFailedDiagnosis(DiagnosisResult result, String reason) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format(
                "%s %s FAILED | TraceID=%s | Service=%s | Method=%s | Duration=%dms | Reason=%s",
                DIAGNOSIS_LOG_PREFIX,
                timestamp,
                result.getTraceId(),
                result.getServiceName(),
                result.getMethodName(),
                result.getDuration(),
                reason
        );

        logger.log(Level.WARNING, logEntry);
    }

    /**
     * Log diagnosis task submission
     */
    public static void logTaskSubmitted(String traceId, String serviceName, String methodName, long duration) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format(
                "%s %s TASK_SUBMITTED | TraceID=%s | Service=%s | Method=%s | Duration=%dms",
                DIAGNOSIS_LOG_PREFIX,
                timestamp,
                traceId,
                serviceName,
                methodName,
                duration
        );

        logger.fine(logEntry);
    }

    /**
     * Log diagnosis task rejection (queue full, etc.)
     */
    public static void logTaskRejected(String traceId, String reason) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format(
                "%s %s TASK_REJECTED | TraceID=%s | Reason=%s",
                DIAGNOSIS_LOG_PREFIX,
                timestamp,
                traceId,
                reason
        );

        logger.warning(logEntry);
    }

    /**
     * Log AI configuration issues
     */
    public static void logConfigurationIssue(String issue) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format(
                "%s %s CONFIG_ERROR | %s",
                DIAGNOSIS_LOG_PREFIX,
                timestamp,
                issue
        );

        logger.severe(logEntry);
    }

    /**
     * Log thread pool statistics
     */
    public static void logThreadPoolStats() {
        try {
            String stats = DiagnosisTaskExecutor.getPoolStatistics();
            String timestamp = LocalDateTime.now().format(formatter);
            String logEntry = String.format(
                    "%s %s THREADPOOL_STATS | %s",
                    DIAGNOSIS_LOG_PREFIX,
                    timestamp,
                    stats
            );

            logger.info(logEntry);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to log thread pool stats", e);
        }
    }

    /**
     * Format a diagnosis summary for reporting
     */
    public static String formatDiagnosisSummary(DiagnosisResult result) {
        return String.format(
                "Trace %s: %s.%s took %dms (CPU: %.1f%%, Memory: %.1f%%)\n" +
                "Diagnosis: %s",
                result.getTraceId(),
                result.getServiceName(),
                result.getMethodName(),
                result.getDuration(),
                result.getSystemCpuLoad(),
                result.getSystemMemoryUsage(),
                result.getAiDiagnosis() != null ? result.getAiDiagnosis() : "(No diagnosis)"
        );
    }
}
