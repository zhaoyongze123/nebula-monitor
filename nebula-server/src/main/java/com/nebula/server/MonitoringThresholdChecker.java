package com.nebula.server;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.LinkedList;
import java.util.Queue;
import com.sun.management.OperatingSystemMXBean;

/**
 * 混合策略监控阈值检查器
 * 检查系统是否超过负载阈值，包括：内存、CPU、队列堆积、ES 延迟
 */
public class MonitoringThresholdChecker {
    
    // 阈值配置
    private static final float HEAP_THRESHOLD = 0.8f;        // 堆内存使用 > 80%
    private static final float CPU_THRESHOLD = 0.7f;         // CPU 利用 > 70%
    private static final int QUEUE_SIZE_THRESHOLD = 10000;   // 队列堆积 > 10000 条
    private static final long ES_LATENCY_THRESHOLD = 500;    // ES 写入延迟 > 500ms
    
    // 用于计算 ES 延迟的队列（最近 100 条）
    private static final Queue<Long> esLatencies = new LinkedList<>();
    private static final int LATENCY_WINDOW_SIZE = 100;
    
    // MXBean 实例
    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private static final com.sun.management.OperatingSystemMXBean osMXBean = 
        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    
    /**
     * 检查 JVM 堆内存使用率是否超过阈值
     * @return true 如果超过 80%
     */
    public static boolean checkHeapMemory() {
        try {
            long used = memoryMXBean.getHeapMemoryUsage().getUsed();
            long max = memoryMXBean.getHeapMemoryUsage().getMax();
            float ratio = (float) used / max;
            
            if (ratio > HEAP_THRESHOLD) {
                System.out.println(String.format(
                    "⚠️  堆内存使用率过高: %.1f%% (阈值: %.0f%%)",
                    ratio * 100, HEAP_THRESHOLD * 100
                ));
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("❌ 获取堆内存信息失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查 CPU 使用率是否超过阈值
     * @return true 如果超过 70%
     */
    public static boolean checkCpuLoad() {
        try {
            double cpuLoad = osMXBean.getProcessCpuLoad();
            
            if (cpuLoad > CPU_THRESHOLD) {
                System.out.println(String.format(
                    "⚠️  CPU 利用率过高: %.1f%% (阈值: %.0f%%)",
                    cpuLoad * 100, CPU_THRESHOLD * 100
                ));
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("❌ 获取 CPU 信息失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查监控数据队列堆积是否超过阈值
     * @return true 如果堆积 > 10000 条
     */
    public static boolean checkQueueSize() {
        try {
            int queueSize = MonitoringDataQueue.size();
            
            if (queueSize > QUEUE_SIZE_THRESHOLD) {
                System.out.println(String.format(
                    "⚠️  监控队列堆积过多: %d 条 (阈值: %d)",
                    queueSize, QUEUE_SIZE_THRESHOLD
                ));
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("❌ 获取队列大小失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查 Elasticsearch 写入延迟是否超过阈值
     * @return true 如果平均延迟 > 500ms
     */
    public static boolean checkEsLatency() {
        try {
            if (esLatencies.isEmpty()) {
                return false;  // 暂无数据
            }
            
            long sum = esLatencies.stream().mapToLong(Long::longValue).sum();
            long average = sum / esLatencies.size();
            
            if (average > ES_LATENCY_THRESHOLD) {
                System.out.println(String.format(
                    "⚠️  ES 写入延迟过高: %dms (阈值: %dms)",
                    average, ES_LATENCY_THRESHOLD
                ));
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("❌ 计算 ES 延迟失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 记录 ES 写入延迟（用于计算平均值）
     * @param latencyMs 延迟（毫秒）
     */
    public static void recordEsLatency(long latencyMs) {
        synchronized (esLatencies) {
            esLatencies.add(latencyMs);
            
            // 只保留最近 100 条
            if (esLatencies.size() > LATENCY_WINDOW_SIZE) {
                esLatencies.poll();
            }
        }
    }
    
    /**
     * 混合策略检查 - 检查是否需要触发降频
     * 返回 true 表示系统负载过高，需要降频
     * 
     * @return true 如果任意指标超过阈值
     */
    public static boolean checkSystemLoad() {
        return checkHeapMemory() || checkCpuLoad() || checkQueueSize() || checkEsLatency();
    }
    
    /**
     * 获取当前系统负载状态（用于诊断）
     */
    public static String getSystemLoadStatus() {
        try {
            float heapRatio = (float) memoryMXBean.getHeapMemoryUsage().getUsed() 
                            / memoryMXBean.getHeapMemoryUsage().getMax();
            double cpuLoad = osMXBean.getProcessCpuLoad();
            int queueSize = MonitoringDataQueue.size();
            
            long avgLatency = 0;
            if (!esLatencies.isEmpty()) {
                synchronized (esLatencies) {
                    avgLatency = esLatencies.stream().mapToLong(Long::longValue).sum() 
                               / esLatencies.size();
                }
            }
            
            return String.format(
                "Heap: %.1f%% | CPU: %.1f%% | Queue: %d | ES Latency: %dms",
                heapRatio * 100, cpuLoad * 100, queueSize, avgLatency
            );
        } catch (Exception e) {
            return "Error getting system status: " + e.getMessage();
        }
    }
}
