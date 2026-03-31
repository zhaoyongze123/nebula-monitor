package com.nebula.server.es;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nebula.common.MonitoringData;

/**
 * Elasticsearch Span 文档 DTO
 * 符合 ES_DATA_SCHEMA.md 规范的数据格式
 * 
 * 使用 snake_case 字段命名（ES 标准格式）：
 * - trace_id, span_id, parent_span_id
 * - service_name, operation_name
 * - duration_ms, timestamp
 * - exception_stack
 * 
 * 同时保留 camelCase 备选（通过 Jackson 注解）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // 不序列化 null 值
public class ESSpanDocument {

    // === 必需字段 ===
    
    @JsonProperty("trace_id")
    private String traceId;
    
    @JsonProperty("span_id")
    private String spanId;
    
    @JsonProperty("parent_span_id")
    private String parentSpanId;
    
    @JsonProperty("service_name")
    private String serviceName;
    
    @JsonProperty("operation_name")
    private String operationName;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("duration_ms")
    private long durationMs;
    
    @JsonProperty("timestamp")
    private long timestamp;  // epoch_millis 格式
    
    // === 可选字段 ===
    
    @JsonProperty("exception_stack")
    private String exceptionStack;
    
    @JsonProperty("httpStatus")
    private Integer httpStatus;
    
    @JsonProperty("error_type")
    private String errorType;
    
    @JsonProperty("error_code")
    private String errorCode;
    
    @JsonProperty("environment")
    private String environment;
    
    @JsonProperty("region")
    private String region;
    
    @JsonProperty("cluster")
    private String cluster;
    
    @JsonProperty("instanceId")
    private String instanceId;
    
    // === 采样标记 ===
    @JsonProperty("sampled")
    private boolean sampled;
    
    // === 保留 camelCase 备选字段（读取兼容） ===
    @JsonProperty("traceId")
    private String traceIdCamel;
    
    @JsonProperty("spanId") 
    private String spanIdCamel;
    
    @JsonProperty("parentSpanId")
    private String parentSpanIdCamel;
    
    @JsonProperty("serviceName")
    private String serviceNameCamel;
    
    @JsonProperty("operationName")
    private String operationNameCamel;
    
    @JsonProperty("durationMs")
    private Long durationMsCamel;
    
    @JsonProperty("exceptionStack")
    private String exceptionStackCamel;
    
    /**
     * 默认构造器
     */
    public ESSpanDocument() {}
    
    /**
     * 从 MonitoringData 转换
     * 自动处理字段映射和时间戳格式
     */
    public static ESSpanDocument fromMonitoringData(MonitoringData data) {
        ESSpanDocument doc = new ESSpanDocument();
        
        // 必需字段
        doc.traceId = data.getTraceId();
        doc.spanId = data.getSpanId();
        doc.parentSpanId = data.getParentSpanId();
        doc.serviceName = data.getServiceName();
        doc.operationName = data.getOperationName();
        doc.status = data.getStatus() != null ? data.getStatus() : "OK";
        doc.durationMs = data.getDuration();
        doc.timestamp = data.getTimestamp();  // 已经是 epoch_millis 格式
        
        // 可选字段
        doc.exceptionStack = data.getExceptionStack();
        doc.httpStatus = data.getHttpStatus();
        doc.errorType = data.getErrorType();
        doc.errorCode = data.getErrorCode();
        doc.environment = data.getEnvironment();
        doc.region = data.getRegion();
        doc.cluster = data.getCluster();
        doc.instanceId = data.getInstanceId();
        doc.sampled = data.isSampled();
        
        // 设置 camelCase 备选字段（双写）
        doc.traceIdCamel = doc.traceId;
        doc.spanIdCamel = doc.spanId;
        doc.parentSpanIdCamel = doc.parentSpanId;
        doc.serviceNameCamel = doc.serviceName;
        doc.operationNameCamel = doc.operationName;
        doc.durationMsCamel = doc.durationMs;
        doc.exceptionStackCamel = doc.exceptionStack;
        
        return doc;
    }
    
    /**
     * 构建器模式入口
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // === Getters ===
    
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getServiceName() { return serviceName; }
    public String getOperationName() { return operationName; }
    public String getStatus() { return status; }
    public long getDurationMs() { return durationMs; }
    public long getTimestamp() { return timestamp; }
    public String getExceptionStack() { return exceptionStack; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getErrorType() { return errorType; }
    public String getErrorCode() { return errorCode; }
    public String getEnvironment() { return environment; }
    public String getRegion() { return region; }
    public String getCluster() { return cluster; }
    public String getInstanceId() { return instanceId; }
    public boolean isSampled() { return sampled; }
    
    // === Setters ===
    
    public void setTraceId(String traceId) { 
        this.traceId = traceId;
        this.traceIdCamel = traceId;
    }
    public void setSpanId(String spanId) { 
        this.spanId = spanId;
        this.spanIdCamel = spanId;
    }
    public void setParentSpanId(String parentSpanId) { 
        this.parentSpanId = parentSpanId;
        this.parentSpanIdCamel = parentSpanId;
    }
    public void setServiceName(String serviceName) { 
        this.serviceName = serviceName;
        this.serviceNameCamel = serviceName;
    }
    public void setOperationName(String operationName) { 
        this.operationName = operationName;
        this.operationNameCamel = operationName;
    }
    public void setStatus(String status) { this.status = status; }
    public void setDurationMs(long durationMs) { 
        this.durationMs = durationMs;
        this.durationMsCamel = durationMs;
    }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setExceptionStack(String exceptionStack) { 
        this.exceptionStack = exceptionStack;
        this.exceptionStackCamel = exceptionStack;
    }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public void setRegion(String region) { this.region = region; }
    public void setCluster(String cluster) { this.cluster = cluster; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public void setSampled(boolean sampled) { this.sampled = sampled; }
    
    @Override
    public String toString() {
        return "ESSpanDocument{" +
                "trace_id='" + traceId + '\'' +
                ", span_id='" + spanId + '\'' +
                ", parent_span_id='" + parentSpanId + '\'' +
                ", service_name='" + serviceName + '\'' +
                ", operation_name='" + operationName + '\'' +
                ", status='" + status + '\'' +
                ", duration_ms=" + durationMs +
                ", timestamp=" + timestamp +
                (exceptionStack != null ? ", has_exception=true" : "") +
                '}';
    }
    
    /**
     * Builder 内部类
     */
    public static class Builder {
        private final ESSpanDocument doc = new ESSpanDocument();
        
        public Builder traceId(String traceId) { doc.setTraceId(traceId); return this; }
        public Builder spanId(String spanId) { doc.setSpanId(spanId); return this; }
        public Builder parentSpanId(String parentSpanId) { doc.setParentSpanId(parentSpanId); return this; }
        public Builder serviceName(String serviceName) { doc.setServiceName(serviceName); return this; }
        public Builder operationName(String operationName) { doc.setOperationName(operationName); return this; }
        public Builder status(String status) { doc.setStatus(status); return this; }
        public Builder durationMs(long durationMs) { doc.setDurationMs(durationMs); return this; }
        public Builder timestamp(long timestamp) { doc.setTimestamp(timestamp); return this; }
        public Builder exceptionStack(String exceptionStack) { doc.setExceptionStack(exceptionStack); return this; }
        public Builder httpStatus(Integer httpStatus) { doc.setHttpStatus(httpStatus); return this; }
        public Builder errorType(String errorType) { doc.setErrorType(errorType); return this; }
        public Builder errorCode(String errorCode) { doc.setErrorCode(errorCode); return this; }
        public Builder environment(String environment) { doc.setEnvironment(environment); return this; }
        public Builder region(String region) { doc.setRegion(region); return this; }
        public Builder cluster(String cluster) { doc.setCluster(cluster); return this; }
        public Builder instanceId(String instanceId) { doc.setInstanceId(instanceId); return this; }
        public Builder sampled(boolean sampled) { doc.setSampled(sampled); return this; }
        
        public ESSpanDocument build() { return doc; }
    }
}
