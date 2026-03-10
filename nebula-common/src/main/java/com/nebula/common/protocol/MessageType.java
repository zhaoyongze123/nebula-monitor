package com.nebula.common.protocol;

/**
 * 消息类型枚举
 */
public enum MessageType {
    /**
     * 监控数据消息
     */
    DATA((byte) 0x01),
    
    /**
     * 控制命令消息
     */
    CONTROL((byte) 0x02),
    
    /**
     * 确认响应消息
     */
    ACK((byte) 0x03);
    
    private final byte value;
    
    MessageType(byte value) {
        this.value = value;
    }
    
    public byte getValue() {
        return value;
    }
    
    public static MessageType fromValue(byte value) {
        for (MessageType type : MessageType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + value);
    }
}
