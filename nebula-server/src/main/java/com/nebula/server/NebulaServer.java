package com.nebula.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;

public class NebulaServer {
    private final int port;

    public NebulaServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        // 1. 创建两个线程组：Boss 负责接电话，Worker 负责干活
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class) // 指定使用 NIO 模式
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     // 2. 给传送带安装"翻译官"：ObjectDecoder 负责把字节流转回对象
                     ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
                     // 3. 安装"处理员"：这是我们自己写的逻辑类
                     ch.pipeline().addLast(new ServerHandler());
                 }
             });

            System.out.println("✅ 监控服务端已在端口 " + port + " 启动...");
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        new NebulaServer(8888).start();
    }
}
