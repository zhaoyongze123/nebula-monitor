package com.nebula.common.protocol;

import java.io.Serializable;
import java.util.List;

/**
 * 控制命令 - Server 下发给 Agent 的控制指令
 */
public class ControlCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 命令 ID（用于追踪）
     */
    private String commandId;
    
    /**
     * 行为类型
     */
    private CommandAction action;
    
    /**
     * 目标应用列表（目标应用将收到该命令）
     * 若为空或包含当前应用，则该 Agent 需要执行
     */
    private List<String> targetApps;
    
    /**
     * 采样率（0.0 - 1.0）
     */
    private float samplingRate;
    
    /**
     * 命令生成时间戳
     */
    private long timestamp;
    
    public ControlCommand() {
        this.timestamp = System.currentTimeMillis();
        this.commandId = generateCommandId();
    }
    
    public ControlCommand(CommandAction action, List<String> targetApps, float samplingRate) {
        this.action = action;
        this.targetApps = targetApps;
        this.samplingRate = samplingRate;
        this.timestamp = System.currentTimeMillis();
        this.commandId = generateCommandId();
    }
    
    private String generateCommandId() {
        return "CMD-" + System.currentTimeMillis() + "-" + System.nanoTime();
    }
    
    public String getCommandId() {
        return commandId;
    }
    
    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }
    
    public CommandAction getAction() {
        return action;
    }
    
    public void setAction(CommandAction action) {
        this.action = action;
    }
    
    public List<String> getTargetApps() {
        return targetApps;
    }
    
    public void setTargetApps(List<String> targetApps) {
        this.targetApps = targetApps;
    }
    
    public float getSamplingRate() {
        return samplingRate;
    }
    
    public void setSamplingRate(float samplingRate) {
        this.samplingRate = samplingRate;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "ControlCommand{" +
                "commandId='" + commandId + '\'' +
                ", action=" + action +
                ", targetApps=" + targetApps +
                ", samplingRate=" + samplingRate +
                ", timestamp=" + timestamp +
                '}';
    }
    
    /**
     * 命令行为枚举
     */
    public enum CommandAction {
        /**
         * 设置采样率
         */
        SET_SAMPLING("SET_SAMPLING"),
        
        /**
         * 获取 Agent 状态
         */
        GET_STATUS("GET_STATUS");
        
        private final String name;
        
        CommandAction(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        public static CommandAction fromName(String name) {
            for (CommandAction action : CommandAction.values()) {
                if (action.name.equals(name)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("Unknown action: " + name);
        }
    }
}
