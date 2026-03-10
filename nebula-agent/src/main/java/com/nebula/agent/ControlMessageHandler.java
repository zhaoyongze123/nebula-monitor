package com.nebula.agent;

import com.nebula.common.protocol.Message;
import com.nebula.common.protocol.MessageType;
import com.nebula.common.protocol.ControlCommand;
import com.nebula.common.protocol.AckResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Agent 端的控制消息处理器 - 处理来自 Server 的控制命令
 */
public class ControlMessageHandler extends SimpleChannelInboundHandler<Message> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        MessageType type = msg.getMessageType();
        
        if (type == MessageType.CONTROL) {
            // 控制命令消息
            try {
                ControlCommand cmd = objectMapper.readValue(msg.getBody(), ControlCommand.class);
                handleControlCommand(ctx, cmd);
            } catch (Exception e) {
                System.err.println("❌ 解析控制命令失败: " + e.getMessage());
                sendAckFailure(ctx, "CMD-UNKNOWN", e.getMessage());
            }
        }
        // 其他消息类型透传
        ctx.fireChannelRead(msg);
    }
    
    /**
     * 处理控制命令
     */
    private void handleControlCommand(ChannelHandlerContext ctx, ControlCommand cmd) {
        String timestamp = LocalDateTime.now().format(formatter);
        String action = cmd.getAction().getName();
        
        try {
            if (cmd.getAction() == ControlCommand.CommandAction.SET_SAMPLING) {
                // 更新采样率
                String appName = NebulaAgent.getApplicationName();
                
                // 检查此命令是否适用于当前应用
                if (cmd.getTargetApps() != null && !cmd.getTargetApps().isEmpty()) {
                    if (!cmd.getTargetApps().contains(appName)) {
                        System.out.println(String.format(
                            "[%s] ⏭️  跳过采样率命令 - 目标应用 %s 不包含当前应用 %s",
                            timestamp, cmd.getTargetApps(), appName
                        ));
                        return;
                    }
                }
                
                // 更新全局采样配置
                float oldRate = GlobalConfig.getSamplingRate(appName);
                GlobalConfig.updateSamplingRate(appName, cmd.getSamplingRate());
                
                System.out.println(String.format(
                    "[%s] ✅ 采样率已更新 - 应用: %s, 旧值: %.2f，新值: %.2f",
                    timestamp, appName, oldRate, cmd.getSamplingRate()
                ));
                
                // 返回 ACK 成功
                sendAckSuccess(ctx, cmd.getCommandId());
                
            } else if (cmd.getAction() == ControlCommand.CommandAction.GET_STATUS) {
                System.out.println(String.format(
                    "[%s] ℹ️  收到状态查询命令",
                    timestamp
                ));
                // 由业务逻辑层处理，这里仅确认收到
                sendAckSuccess(ctx, cmd.getCommandId());
            }
        } catch (Exception e) {
            System.err.println(String.format(
                "[%s] ❌ 处理命令失败 - CommandID: %s, 错误: %s",
                timestamp, cmd.getCommandId(), e.getMessage()
            ));
            e.printStackTrace();
            sendAckFailure(ctx, cmd.getCommandId(), e.getMessage());
        }
    }
    
    /**
     * 发送 ACK 成功响应
     */
    private void sendAckSuccess(ChannelHandlerContext ctx, String commandId) {
        AckResponse ack = new AckResponse(
            commandId,
            NebulaAgent.getAgentId(),
            true
        );
        sendAck(ctx, ack);
    }
    
    /**
     * 发送 ACK 失败响应
     */
    private void sendAckFailure(ChannelHandlerContext ctx, String commandId, String errorMsg) {
        AckResponse ack = new AckResponse(
            commandId,
            NebulaAgent.getAgentId(),
            false,
            errorMsg
        );
        sendAck(ctx, ack);
    }
    
    /**
     * 发送 ACK 响应给 Server
     */
    private void sendAck(ChannelHandlerContext ctx, AckResponse ack) {
        try {
            String ackBody = objectMapper.writeValueAsString(ack);
            Message ackMsg = new Message(MessageType.ACK, ackBody);
            ctx.writeAndFlush(ackMsg);
        } catch (Exception e) {
            System.err.println("❌ 发送 ACK 响应失败: " + e.getMessage());
        }
    }
}
