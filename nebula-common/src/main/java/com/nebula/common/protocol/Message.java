package com.nebula.common.protocol;

import java.io.Serializable;

/**
 * 通用消息基类
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息类型
     */
    private MessageType messageType;
    
    /**
     * 消息时间戳（毫秒）
     */
    private long timestamp;
    
    /**
     * 消息体（JSON 序列化的具体内容）
     */
    private String body;
    
    public Message() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public Message(MessageType messageType, String body) {
        this.messageType = messageType;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
    }
    
    public MessageType getMessageType() {
        return messageType;
    }
    
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "messageType=" + messageType +
                ", timestamp=" + timestamp +
                ", body='" + body + '\'' +
                '}';
    }
}
