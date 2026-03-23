package com.nebula.server.diagnosis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * 诊断结果模型 - 存储 AI 诊断的结果
 * 用于在 Redis 队列中序列化/反序列化
 */
public class DiagnosisResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("service_name")
    private String serviceName;

    @JsonProperty("method_name")
    private String methodName;

    @JsonProperty("duration")
    private long duration; // 链路耗时，单位 ms

    @JsonProperty("system_cpu")
    private double systemCpuLoad; // 系统 CPU 利用率

    @JsonProperty("system_memory")
    private double systemMemoryUsage; // 系统内存占用率

    @JsonProperty("gc_count")
    private long gcCount; // 诊断发生时的 GC 次数

    @JsonProperty("comparative_avg")
    private long comparativeAverage; // 同服务其他链路的平均耗时，单位 ms

    @JsonProperty("ai_diagnosis")
    private String aiDiagnosis; // AI 诊断结果（分析文本）

    @JsonProperty("diagnosis_timestamp")
    private long diagnosisTimestamp; // 诊断执行的时间戳

    @JsonProperty("status")
    private String status; // SUCCESS, FAILED, TIMEOUT

    @JsonProperty("error_message")
    private String errorMessage; // 诊断失败时的错误信息

    // 构造器
    public DiagnosisResult() {}

    public DiagnosisResult(String traceId, String serviceName, String methodName, long duration) {
        this.traceId = traceId;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.duration = duration;
        this.diagnosisTimestamp = System.currentTimeMillis();
        this.status = "PENDING";
    }

    // Getters and Setters
    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public double getSystemCpuLoad() {
        return systemCpuLoad;
    }

    public void setSystemCpuLoad(double systemCpuLoad) {
        this.systemCpuLoad = systemCpuLoad;
    }

    public double getSystemMemoryUsage() {
        return systemMemoryUsage;
    }

    public void setSystemMemoryUsage(double systemMemoryUsage) {
        this.systemMemoryUsage = systemMemoryUsage;
    }

    public long getGcCount() {
        return gcCount;
    }

    public void setGcCount(long gcCount) {
        this.gcCount = gcCount;
    }

    public long getComparativeAverage() {
        return comparativeAverage;
    }

    public void setComparativeAverage(long comparativeAverage) {
        this.comparativeAverage = comparativeAverage;
    }

    public String getAiDiagnosis() {
        return aiDiagnosis;
    }

    public void setAiDiagnosis(String aiDiagnosis) {
        this.aiDiagnosis = aiDiagnosis;
    }

    public long getDiagnosisTimestamp() {
        return diagnosisTimestamp;
    }

    public void setDiagnosisTimestamp(long diagnosisTimestamp) {
        this.diagnosisTimestamp = diagnosisTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "DiagnosisResult{" +
                "traceId='" + traceId + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", duration=" + duration +
                ", systemCpuLoad=" + systemCpuLoad +
                ", systemMemoryUsage=" + systemMemoryUsage +
                ", gcCount=" + gcCount +
                ", comparativeAverage=" + comparativeAverage +
                ", aiDiagnosis='" + aiDiagnosis + '\'' +
                ", diagnosisTimestamp=" + diagnosisTimestamp +
                ", status='" + status + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
