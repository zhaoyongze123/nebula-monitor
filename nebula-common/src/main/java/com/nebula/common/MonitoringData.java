package com.nebula.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 监控数据传输对象 (DTO)
 * 用于在 Agent 和 Server 之间传递方法耗时信息
 */
public class MonitoringData implements Serializable {

    // 序列化版本号，确保发送端和接收端版本一致
    private static final long serialVersionUID = 1L;

    private String methodName; // 被拦截的方法名
    private long duration;     // 方法执行耗时 (ms)
    private long timestamp;    // 数据产生的时间戳
    private String serviceName; // 来源服务名称（比如 12306-order）

    public MonitoringData() {}

    public MonitoringData(String methodName, long duration, long timestamp, String serviceName) {
        this.methodName = methodName;
        this.duration = duration;
        this.timestamp = timestamp;
        this.serviceName = serviceName;
    }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    @Override
    public String toString() {
        return "MonitoringData{" +
                "methodName='" + methodName + '\'' +
                ", duration=" + duration +
                ", timestamp=" + timestamp +
                ", serviceName='" + serviceName + '\'' +
                '}';
    }
}
