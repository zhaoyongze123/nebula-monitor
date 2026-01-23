package com.nebula.server;

import com.nebula.common.MonitoringData;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 全局的监控数据队列（线程安全）
 * 
 * 用途：
 * 1. ServerHandler 将接收到的数据写入此队列
 * 2. ESSyncWorker 从此队列读取数据并存入 Elasticsearch
 * 3. 当 Redis 失败时，此队列充当缓冲
 */
public class MonitoringDataQueue {
    // 使用 BlockingQueue 自动处理线程同步和阻塞等待
    private static final BlockingQueue<MonitoringData> queue = new LinkedBlockingQueue<>(10000);
    
    /**
     * 将监控数据添加到队列
     */
    public static void add(MonitoringData data) {
        try {
            queue.put(data);
        } catch (InterruptedException e) {
            System.err.println("⚠️  添加数据到内存队列失败: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 从队列中取出一条数据（非阻塞，立即返回）
     * 
     * @return 数据对象，如果队列为空则返回 null
     */
    public static MonitoringData poll() {
        return queue.poll();
    }
    
    /**
     * 获取队列中的当前元素数量
     */
    public static int size() {
        return queue.size();
    }
}
