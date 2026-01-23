package com.nebula.server;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis 连接池单例管理器
 * 
 * 作用：
 * 1. 全局唯一的 Redis 连接池
 * 2. 确保高并发场景下连接复用
 * 3. 防止连接泄漏和耗尽
 * 
 * 为什么需要：
 * ServerHandler 中每次都创建新 Jedis，会导致：
 * - 连接泄漏（异常时关闭失败）
 * - 连接耗尽（高并发导致太多连接）
 * - "Unexpected end of stream" 错误
 */
public class RedisPoolManager {
    private static JedisPool jedisPool;
    private static final Object lock = new Object();
    
    /**
     * 初始化 Redis 连接池（双检查锁模式）
     */
    public static void init() {
        if (jedisPool == null) {
            synchronized (lock) {
                if (jedisPool == null) {
                    jedisPool = createPool();
                }
            }
        }
    }
    
    /**
     * 创建连接池
     */
    private static JedisPool createPool() {
        String redisHost = System.getenv("REDIS_HOST") != null ? 
                          System.getenv("REDIS_HOST") : "localhost";
        int redisPort = System.getenv("REDIS_PORT") != null ? 
                       Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;
        
        JedisPoolConfig config = new JedisPoolConfig();
        // 核心配置
        config.setMaxTotal(30);           // 最大连接数（支持并发）
        config.setMaxIdle(20);            // 最大空闲连接
        config.setMinIdle(5);             // 最小空闲连接（预热）
        config.setMaxWaitMillis(10000);   // 获取连接最长等待 10 秒
        
        // 连接验证
        config.setTestOnBorrow(true);     // 取出时验证连接
        config.setTestOnReturn(true);     // 归还时验证连接
        config.setTestWhileIdle(true);    // 空闲时也验证
        config.setMinEvictableIdleTimeMillis(60000);  // 空闲 60s 后清理
        config.setTimeBetweenEvictionRunsMillis(30000); // 每 30s 检查一次
        
        try {
            JedisPool pool = new JedisPool(config, redisHost, redisPort, 5000);
            System.out.println("✅ Redis 连接池创建成功 (host=" + redisHost + 
                             ", port=" + redisPort + 
                             ", maxTotal=" + config.getMaxTotal() + ")");
            return pool;
        } catch (Exception e) {
            System.err.println("❌ Redis 连接池创建失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取连接
     */
    public static Jedis getConnection() {
        if (jedisPool == null) {
            init();
        }
        if (jedisPool != null) {
            try {
                return jedisPool.getResource();
            } catch (Exception e) {
                System.err.println("⚠️  无法获取 Redis 连接: " + e.getMessage());
                return null;
            }
        }
        return null;
    }
    
    /**
     * 获取连接（带重试）
     */
    public static Jedis getConnectionWithRetry(int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            Jedis jedis = getConnection();
            if (jedis != null) {
                try {
                    jedis.ping();  // 测试连接是否有效
                    return jedis;
                } catch (Exception e) {
                    if (jedis != null) {
                        try {
                            jedis.close();
                        } catch (Exception ignore) {}
                    }
                }
            }
            
            // 重试前等待
            if (i < maxRetries - 1) {
                try {
                    Thread.sleep(100 * (i + 1));  // 递增退避
                } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }
    
    /**
     * 关闭连接池
     */
    public static void close() {
        if (jedisPool != null) {
            try {
                jedisPool.close();
                System.out.println("✅ Redis 连接池已关闭");
            } catch (Exception e) {
                System.err.println("⚠️  关闭 Redis 连接池失败: " + e.getMessage());
            }
            jedisPool = null;
        }
    }
    
    /**
     * 获取连接池状态
     */
    public static String getPoolStats() {
        if (jedisPool != null) {
            return "Redis 连接池 - 活跃:" + jedisPool.getNumActive() + 
                   ", 空闲:" + jedisPool.getNumIdle() + 
                   ", 等待:" + jedisPool.getNumWaiters();
        }
        return "Redis 连接池未初始化";
    }
}
