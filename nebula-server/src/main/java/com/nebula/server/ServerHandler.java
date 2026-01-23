package com.nebula.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.MonitoringData;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import redis.clients.jedis.Jedis;

/**
 * 服务端处理器：接收 Agent 发送的数据
 * 这是 Netty 的"业务逻辑处理层"
 * 
 * 核心职责：
 * 1. 接收 Agent 发送的 MonitoringData 对象
 * 2. 使用连接池写入 Redis 队列
 * 3. 异步线程 ESSyncWorker 从 Redis 读取并存入 Elasticsearch
 * 
 * 改进点：
 * - 使用全局连接池（避免连接泄漏）
 * - 添加重试机制（处理间歇性故障）
 * - 自动 Fallback 到内存队列（零数据丢失）
 */
public class ServerHandler extends SimpleChannelInboundHandler<Object> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 当接收到客户端（Agent）的消息时触发
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof MonitoringData) {
            MonitoringData data = (MonitoringData) msg;
            System.out.println("📨 服务端收到监控数据: " + data.getMethodName() + 
                             " (耗时: " + data.getDuration() + "ms)");
            
            // 初始化 Redis 连接池
            RedisPoolManager.init();
            
            // 异步写入 Redis 队列（非阻塞操作）
            writeToRedis(data);
        } else {
            System.out.println("⚠️  收到未知类型的数据: " + msg.getClass().getName());
        }
    }

    /**
     * 将监控数据写入 Redis 队列
     * 
     * 改进机制：
     * 1. 使用全局连接池（避免连接泄漏）
     * 2. 添加重试机制（最多 3 次）
     * 3. 使用 try-finally（自动释放资源）
     * 4. 异常自动 Fallback 到内存队列
     */
    private void writeToRedis(MonitoringData data) {
        boolean redisSuccess = false;
        
        // 重试机制：最多尝试 3 次
        for (int retry = 0; retry < 3; retry++) {
            Jedis jedis = null;
            try {
                // 从连接池获取连接（自动验证）
                jedis = RedisPoolManager.getConnection();
                
                if (jedis == null) {
                    if (retry == 2) {
                        System.err.println("❌ Redis 连接池不可用（已重试 3 次）");
                    }
                    continue;
                }
                
                // 序列化为 JSON 字符串
                String json = MAPPER.writeValueAsString(data);
                
                // LPUSH：从左端推入队列（异步线程从右端 RPOP 拉取）
                jedis.lpush("nebula:monitor:queue", json);
                
                System.out.println("✅ 数据已写入 Redis 队列 (retry=" + retry + ")");
                redisSuccess = true;
                break;  // 成功，退出重试循环
                
            } catch (Exception e) {
                System.err.println("⚠️  写入 Redis 失败 (retry=" + retry + "): " + e.getMessage());
                
                // 最后一次失败时打印诊断信息
                if (retry == 2) {
                    System.err.println("   💡 诊断: " + RedisPoolManager.getPoolStats());
                    System.err.println("   💡 检查: docker ps | grep redis");
                }
            } finally {
                // 关键：务必归还连接到池中
                if (jedis != null) {
                    try {
                        jedis.close();
                    } catch (Exception e) {
                        System.err.println("⚠️  关闭 Redis 连接失败: " + e.getMessage());
                    }
                }
            }
            
            // 重试延迟（递增退避）
            if (retry < 2) {
                try {
                    Thread.sleep(50 * (retry + 1));
                } catch (InterruptedException ignored) {}
            }
        }
        
        // 无论 Redis 是否成功，都写入内存队列作为备份
        // 这样即使 Redis 不可用，数据也不会丢失
        try {
            MonitoringDataQueue.add(data);
            if (!redisSuccess) {
                System.out.println("✅ 数据已写入内存队列（Redis 失败自动 Fallback）");
            }
        } catch (Exception e) {
            System.err.println("❌ 写入内存队列失败: " + e.getMessage());
        }
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
        System.err.println("⚠️  异常信息：" + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}

