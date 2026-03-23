package com.nebula.server;

import com.nebula.common.protocol.Message;
import com.nebula.common.protocol.MessageType;
import com.nebula.common.protocol.AckResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 控制命令处理器 - 处理来自 Agent 的 ACK 响应
 */
public class ControlCommandHandler extends SimpleChannelInboundHandler<Message> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        MessageType type = msg.getMessageType();
        
        if (type == MessageType.DATA) {
            // 数据消息 - 转发给 ServerHandler 处理
            ctx.fireChannelRead(msg);
        } else if (type == MessageType.ACK) {
            // ACK 响应消息
            try {
                AckResponse ack = objectMapper.readValue(msg.getBody(), AckResponse.class);
                handleAckResponse(ack);
            } catch (Exception e) {
                System.err.println("❌ 解析 ACK 响应失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理 ACK 响应
     */
    private void handleAckResponse(AckResponse ack) {
        String timestamp = LocalDateTime.now().format(formatter);
        if (ack.isSuccess()) {
            System.out.println(String.format(
                "[%s] ✅ ACK 成功 - CommandID: %s, AgentID: %s",
                timestamp, ack.getCommandId(), ack.getAgentId()
            ));
        } else {
            System.err.println(String.format(
                "[%s] ❌ ACK 失败 - CommandID: %s, AgentID: %s, 错误: %s",
                timestamp, ack.getCommandId(), ack.getAgentId(), ack.getErrorMessage()
            ));
        }
    }
}
