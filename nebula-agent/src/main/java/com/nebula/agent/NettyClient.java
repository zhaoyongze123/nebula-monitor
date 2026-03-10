package com.nebula.agent;

import com.nebula.common.MonitoringData;
import com.nebula.common.protocol.Message;
import com.nebula.common.protocol.MessageType;
import com.nebula.agent.codec.BinaryMessageDecoder;
import com.nebula.agent.codec.BinaryMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.codec.serialization.ClassResolvers;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NettyClient {
    private static Channel channel;
    private static EventLoopGroup group;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("deprecation")
    public static void connect(String host, int port) {
        group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group)
         .channel(NioSocketChannel.class)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 // 接收端：收取 Server 的控制命令（使用新的二进制编解码器）
                 ch.pipeline().addLast(new BinaryMessageDecoder());
                 
                 // 处理控制消息
                 ch.pipeline().addLast(new ControlMessageHandler());
                 
                 // 向后兼容旧的 ObjectDecoder（用于 Server 端的向后兼容）
                 ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
                 
                 // 发送端：编码 MonitoringData 和新的 Message 对象
                 // 新的二进制编码器
                 ch.pipeline().addLast(new BinaryMessageEncoder());
                 // 旧的对象序列化编码器（向后兼容）
                 ch.pipeline().addLast(new ObjectEncoder());
             }
         });

        try {
            // 异步连接，sync() 保证连接完成后再继续执行
            channel = b.connect(host, port).sync().channel();
            System.out.println("✅ Agent 已连接至监控服务端: " + host + ":" + port);
        } catch (Exception e) {
            System.err.println("⚠️  监控服务端连接失败: " + e.getMessage());
        }
    }

    public static void send(MonitoringData data) {
        if (channel == null) {
            System.err.println("❌ [NettyClient] Channel 为 null，未连接到服务端");
            return;
        }
        
        if (!channel.isActive()) {
            System.err.println("❌ [NettyClient] Channel 已关闭，无法发送数据");
            return;
        }
        
        try {
            // 将 MonitoringData 序列化为 JSON 并包装为 Message
            String dataJson = objectMapper.writeValueAsString(data);
            Message message = new Message(MessageType.DATA, dataJson);
            
            // writeAndFlush 会将数据推向网络通道
            channel.writeAndFlush(message).addListener(future -> {
                if (!future.isSuccess()) {
                    System.err.println("❌ [NettyClient] 数据发送失败: " + future.cause().getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("❌ [NettyClient] 发送数据异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 发送 Message 对象（用于新的二进制协议）
     */
    public static void sendMessage(Message message) {
        if (channel == null) {
            System.err.println("❌ [NettyClient] Channel 为 null，未连接到服务端");
            return;
        }
        
        if (!channel.isActive()) {
            System.err.println("❌ [NettyClient] Channel 已关闭，无法发送数据");
            return;
        }
        
        try {
            channel.writeAndFlush(message).addListener(future -> {
                if (!future.isSuccess()) {
                    System.err.println("❌ [NettyClient] 消息发送失败: " + future.cause().getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("❌ [NettyClient] 发送消息异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void shutdown() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}