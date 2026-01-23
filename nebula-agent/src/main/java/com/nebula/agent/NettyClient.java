package com.nebula.agent;

import com.nebula.common.MonitoringData;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ObjectEncoder;

public class NettyClient {
    private static Channel channel;
    private static EventLoopGroup group;

    public static void connect(String host, int port) {
        group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group)
         .channel(NioSocketChannel.class)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 // ObjectEncoder 负责将 MonitoringData 对象序列化为二进制
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
        if (channel != null && channel.isActive()) {
            // writeAndFlush 会将数据推向网络通道
            channel.writeAndFlush(data);
        }
    }

    public static void shutdown() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}