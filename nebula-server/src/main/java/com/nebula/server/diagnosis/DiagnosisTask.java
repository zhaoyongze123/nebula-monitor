package com.nebula.server.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.MonitoringData;
import com.nebula.server.RedisPoolManager;
import redis.clients.jedis.Jedis;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DiagnosisTask - Runnable task that executes the complete diagnosis workflow
 * Collects context, invokes AI, and stores results in Redis
 */
public class DiagnosisTask implements Runnable {
    private static final Logger logger = Logger.getLogger(DiagnosisTask.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String DIAGNOSIS_QUEUE_KEY = "nebula:diagnosis:queue";
    private static final String DIAGNOSIS_FAILED_KEY = "nebula:diagnosis:failed";

    private final MonitoringData monitoringData;
    private final long taskStartTime;

    public DiagnosisTask(MonitoringData data) {
        this.monitoringData = data;
        this.taskStartTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        Thread.currentThread().setName("DiagnosisTask-" + monitoringData.getTraceId());

        try {
            logger.fine(() -> "Starting diagnosis for trace: " + monitoringData.getTraceId());

            // Step 1: Create diagnosis result object
            DiagnosisResult result = new DiagnosisResult(
                    monitoringData.getTraceId(),
                    monitoringData.getServiceName(),
                    monitoringData.getMethodName(),
                    monitoringData.getDuration()
            );

            // Step 2: Collect system metrics
            logger.fine("Collecting system metrics...");
            TraceContextCollector.DiagnosisSystemMetrics metrics =
                    TraceContextCollector.collectSystemMetrics();
            result.setSystemCpuLoad(metrics.getProcessCpuLoad() * 100);
            result.setSystemMemoryUsage(metrics.getMemoryUsagePercent());
            result.setGcCount(metrics.getGcCount());

            // Step 3: Collect comparative data
            logger.fine("Collecting comparative data...");
            TraceContextCollector.DiagnosisComparativeData comparative =
                    TraceContextCollector.collectComparativeData(
                            monitoringData.getServiceName(),
                            monitoringData.getMethodName()
                    );
            if (comparative.getSampleCount() > 0) {
                result.setComparativeAverage(comparative.getAverageDuration());
            }

            // Step 4: Build diagnosis prompt
            logger.fine("Building diagnosis prompt...");
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(
                    monitoringData,
                    metrics,
                    comparative
            );

            // Step 5: Call AI service
            logger.fine("Invoking AI diagnosis...");
            String aiDiagnosis = DiagnosisService.diagnoseWithAI(prompt);

            if (aiDiagnosis != null && !aiDiagnosis.isEmpty()) {
                // Success
                result.setAiDiagnosis(aiDiagnosis);
                result.setStatus("SUCCESS");
                storeSuccessfulDiagnosis(result);

                long taskDuration = System.currentTimeMillis() - taskStartTime;
                logger.info(() -> String.format(
                        "Diagnosis completed successfully for trace %s (duration: %dms)",
                        monitoringData.getTraceId(), taskDuration
                ));

                // Audit log
                DiagnosisLogger.logSuccessfulDiagnosis(result, taskDuration);

            } else {
                // AI diagnosis failed
                result.setStatus("FAILED");
                result.setErrorMessage("AI service returned empty diagnosis");
                storeFailedDiagnosis(result);

                logger.warning("AI diagnosis returned empty result for trace: " +
                        monitoringData.getTraceId());
                DiagnosisLogger.logFailedDiagnosis(result, "AI returned empty");
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error executing diagnosis task", e);

            DiagnosisResult failureResult = new DiagnosisResult(
                    monitoringData.getTraceId(),
                    monitoringData.getServiceName(),
                    monitoringData.getMethodName(),
                    monitoringData.getDuration()
            );
            failureResult.setStatus("FAILED");
            failureResult.setErrorMessage(e.getMessage());

            storeFailedDiagnosis(failureResult);
            DiagnosisLogger.logFailedDiagnosis(failureResult, e.toString());
        }
    }

    /**
     * Store successful diagnosis result to Redis queue
     */
    private void storeSuccessfulDiagnosis(DiagnosisResult result) {
        Jedis jedis = null;
        try {
            jedis = RedisPoolManager.getConnection();
            if (jedis == null) {
                logger.warning("Redis unavailable, cannot store diagnosis result");
                return;
            }

            String json = mapper.writeValueAsString(result);
            // LPUSH: Push to queue for downstream processing
            jedis.lpush(DIAGNOSIS_QUEUE_KEY, json);

            // Set expiration to prevent queue overflow (24 hours)
            jedis.expire(DIAGNOSIS_QUEUE_KEY, 86400);

            logger.fine("Stored diagnosis result in Redis: " + DIAGNOSIS_QUEUE_KEY);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to store diagnosis result in Redis", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    /**
     * Store failed diagnosis to failure queue
     */
    private void storeFailedDiagnosis(DiagnosisResult result) {
        Jedis jedis = null;
        try {
            jedis = RedisPoolManager.getConnection();
            if (jedis == null) {
                logger.warning("Redis unavailable, cannot store failed diagnosis");
                return;
            }

            String json = mapper.writeValueAsString(result);
            // LPUSH to failed queue for manual review
            jedis.lpush(DIAGNOSIS_FAILED_KEY, json);

            // Set expiration to prevent queue overflow (7 days)
            jedis.expire(DIAGNOSIS_FAILED_KEY, 604800);

            logger.fine("Stored failed diagnosis in Redis: " + DIAGNOSIS_FAILED_KEY);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to store failed diagnosis in Redis", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    /**
     * Get the trace ID being diagnosed
     */
    public String getTraceId() {
        return monitoringData.getTraceId();
    }

    /**
     * Get task start time
     */
    public long getTaskStartTime() {
        return taskStartTime;
    }
}
