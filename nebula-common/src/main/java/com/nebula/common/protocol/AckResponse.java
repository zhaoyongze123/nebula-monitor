package com.nebula.common.protocol;

import java.io.Serializable;

/**
 * ACK 响应 - Agent 收到控制命令后返回的确认
 */
public class AckResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 关联的命令 ID
     */
    private String commandId;
    
    /**
     * Agent ID（唯一标识）
     */
    private String agentId;
    
    /**
     * 执行是否成功
     */
    private boolean success;
    
    /**
     * 错误信息（若成功为 null）
     */
    private String errorMessage;
    
    /**
     * 响应时间戳
     */
    private long timestamp;
    
    public AckResponse() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public AckResponse(String commandId, String agentId, boolean success) {
        this.commandId = commandId;
        this.agentId = agentId;
        this.success = success;
        this.timestamp = System.currentTimeMillis();
    }
    
    public AckResponse(String commandId, String agentId, boolean success, String errorMessage) {
        this.commandId = commandId;
        this.agentId = agentId;
        this.success = success;
        this.errorMessage = errorMessage;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getCommandId() {
        return commandId;
    }
    
    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "AckResponse{" +
                "commandId='" + commandId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
