package com.nebula.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 服务端处理器：接收 Agent 发送的数据
 * 这是 Netty 的"业务逻辑处理层"
 */
public class ServerHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * 当接收到客户端（Agent）的消息时触发
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 打印接收到的数据
        System.out.println("📨 服务端收到数据：" + msg);
        
        // 这里 msg 可以是任何可序列化的对象
        // 比如后面我们会定义的 MonitoringData 类
    }

    /**
     * 当连接建立时触发
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("✅ Agent 已连接：" + ctx.channel().remoteAddress());
    }

    /**
     * 当连接断开时触发
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("❌ Agent 已断开：" + ctx.channel().remoteAddress());
    }

    /**
     * 异常处理
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("⚠️ 异常信息：" + cause.getMessage());
        ctx.close();
    }
}
