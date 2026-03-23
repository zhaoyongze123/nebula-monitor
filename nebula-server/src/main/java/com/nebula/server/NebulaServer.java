package com.nebula.server;

import com.nebula.server.codec.BinaryMessageDecoder;
import com.nebula.server.codec.BinaryMessageEncoder;
import com.nebula.server.diagnosis.DiagnosisConfig;
import com.nebula.server.diagnosis.DiagnosisLogger;
import com.nebula.server.diagnosis.DiagnosisTaskExecutor;
import com.nebula.server.diagnosis.SlowTraceDetector;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import java.util.logging.Logger;

public class NebulaServer {
    private final int port;
    private static final Logger logger = Logger.getLogger(NebulaServer.class.getName());

    public NebulaServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        // 初始化 Redis 连接池（全局）
        RedisPoolManager.init();
        System.out.println("✅ Elasticsearch 客户端初始化完成");
        
        // 【新增】初始化诊断模块
        try {
            logger.info("Initializing AI diagnosis module...");
            DiagnosisConfig config = DiagnosisConfig.getInstance();
            logger.info("DiagnosisConfig: " + config);
            
            // 检查 AI 配置是否有效
            if (!SlowTraceDetector.isAiConfigValid()) {
                logger.warning("AI diagnosis configuration is incomplete - diagnosis will be disabled");
                DiagnosisLogger.logConfigurationIssue("AI API key or configuration missing");
            } else {
                // 初始化诊断任务执行器（线程池）
                DiagnosisTaskExecutor.initialize();
                logger.info("AI diagnosis module initialized successfully");
            }
        } catch (Exception e) {
            logger.severe("Failed to initialize diagnosis module: " + e.getMessage());
            // Continue startup anyway - diagnosis is optional
        }
        
        // 启动异步同步线程（在启动 Netty 服务之前）
        Thread syncWorkerThread = new Thread(new ESSyncWorker(), "ES-Sync-Worker");
        syncWorkerThread.setDaemon(false);
        syncWorkerThread.start();
        
        // 【新增】启动监控阈值检查线程
        ThresholdThread thresholdThread = new ThresholdThread();
        Thread monitorThread = new Thread(thresholdThread, "Threshold-Monitor");
        monitorThread.setDaemon(false);
        monitorThread.start();
        
        // 1. 创建两个线程组： Boss 线程负责快速接入，Worker 线程池负责非阻塞地处理采集到的耗时数据
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class) // 指定使用 NIO 模式
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     // 支持新的二进制协议（自定义编解码器）
                     ch.pipeline().addLast(new BinaryMessageDecoder());
                     ch.pipeline().addLast(new BinaryMessageEncoder());
                     
                     // 向后兼容旧的 ObjectDecoder（用于处理未升级的 Agent）
                     // 注意：需要在自定义编解码器之后添加
                     ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
                     
                     // 处理 ACK 响应和其他控制消息
                     ch.pipeline().addLast(new ControlCommandHandler());
                     
                     // 业务处理器
                     ch.pipeline().addLast(new ServerHandler());
                 }
             });

            System.out.println("✅ 监控服务端已在端口 " + port + " 启动...");
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            // 关闭 Redis 连接池
            RedisPoolManager.close();
        }
    }

    public static void main(String[] args) throws Exception {
        new NebulaServer(8888).start();
    }
}
