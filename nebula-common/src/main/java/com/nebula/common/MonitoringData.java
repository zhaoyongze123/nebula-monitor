package com.nebula.common;

import java.io.Serializable;
import java.util.UUID;

/**
 * 监控数据传输对象 (DTO)
 * 用于在 Agent 和 Server 之间传递方法耗时信息
 * 
 * 符合 ES_DATA_SCHEMA.md 文档规范的字段定义：
 * - traceId: 全链路追踪 ID
 * - spanId: span 的唯一标识
 * - parentSpanId: 父 span ID (root 为 null)
 * - serviceName: 服务名称
 * - operationName: 操作名/方法名
 * - status: 执行状态 (OK, ERROR, SKIPPED, TIMEOUT)
 * - duration: 执行耗时 (毫秒)
 * - timestamp: 时间戳 (epoch_millis)
 * - exceptionStack: 异常堆栈
 */
public class MonitoringData implements Serializable {

    // 序列化版本号，确保发送端和接收端版本一致
    private static final long serialVersionUID = 2L;

    // === 必需字段 ===
    private String traceId;         // 全链路追踪 ID
    private String spanId;          // span 的唯一标识
    private String parentSpanId;    // 父 span ID (root 为 null)
    private String serviceName;     // 服务名称
    private String operationName;   // 操作名（新字段，替代 methodName 的规范名称）
    private String methodName;      // 被拦截的方法名（保留向后兼容）
    private String status = "OK";   // 执行状态 (OK, ERROR, SKIPPED, TIMEOUT)
    private long duration;          // 方法执行耗时 (ms)
    private long timestamp;         // 数据产生的时间戳 (epoch_millis)
    
    // === 可选字段 ===
    private String exceptionStack;  // 异常堆栈信息
    private Integer httpStatus;     // HTTP 状态码
    private String errorType;       // 错误类型分类
    private String errorCode;       // 错误代码
    private String environment;     // 部署环境 (dev, staging, prod)
    private String region;          // 地域信息
    private String cluster;         // 集群标识
    private String instanceId;      // 实例 ID
    
    // === 采样标记 ===
    private boolean sampled = true; // 是否被采样通过（默认 true）

    public MonitoringData() {
        // 自动生成 spanId
        this.spanId = generateSpanId();
    }

    // 原始构造器（向后兼容）
    public MonitoringData(String methodName, long duration, long timestamp, String serviceName) {
        this.spanId = generateSpanId();
        this.methodName = methodName;
        this.operationName = methodName; // 使用 methodName 作为 operationName
        this.duration = duration;
        this.timestamp = timestamp;
        this.serviceName = serviceName;
    }

    // 新增构造器（包含 traceId）
    public MonitoringData(String traceId, String methodName, long duration, long timestamp, String serviceName) {
        this.spanId = generateSpanId();
        this.traceId = traceId;
        this.methodName = methodName;
        this.operationName = methodName;
        this.duration = duration;
        this.timestamp = timestamp;
        this.serviceName = serviceName;
    }
    
    // 完整构造器（包含所有必需字段）
    public MonitoringData(String traceId, String spanId, String parentSpanId, 
                          String serviceName, String operationName, String status,
                          long duration, long timestamp) {
        this.traceId = traceId;
        this.spanId = spanId != null ? spanId : generateSpanId();
        this.parentSpanId = parentSpanId;
        this.serviceName = serviceName;
        this.operationName = operationName;
        this.methodName = operationName; // 保持兼容
        this.status = status;
        this.duration = duration;
        this.timestamp = timestamp;
    }
    
    /**
     * 生成 8 位十六进制 Span ID
     */
    private static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // === Getters and Setters ===
    
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    
    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    
    public String getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { 
        this.methodName = methodName;
        // 同步更新 operationName（如果未单独设置）
        if (this.operationName == null) {
            this.operationName = methodName;
        }
    }
    
    public String getOperationName() { 
        return operationName != null ? operationName : methodName; 
    }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    
    public String getExceptionStack() { return exceptionStack; }
    public void setExceptionStack(String exceptionStack) { 
        this.exceptionStack = exceptionStack;
        // 有异常时自动设置状态为 ERROR
        if (exceptionStack != null && !exceptionStack.isEmpty() && "OK".equals(this.status)) {
            this.status = "ERROR";
        }
    }
    
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getCluster() { return cluster; }
    public void setCluster(String cluster) { this.cluster = cluster; }
    
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    
    public boolean isSampled() { return sampled; }
    public void setSampled(boolean sampled) { this.sampled = sampled; }

    @Override
    public String toString() {
        return "MonitoringData{" +
                "traceId='" + traceId + '\'' +
                ", spanId='" + spanId + '\'' +
                ", parentSpanId='" + parentSpanId + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", operationName='" + getOperationName() + '\'' +
                ", status='" + status + '\'' +
                ", duration=" + duration +
                ", timestamp=" + timestamp +
                ", sampled=" + sampled +
                (exceptionStack != null ? ", hasException=true" : "") +
                '}';
    }
}
