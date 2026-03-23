package com.nebula.server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 监控阈值检查线程
 * 定期检查系统负载，自动触发降频或恢复采样率
 */
public class ThresholdThread implements Runnable {
    
    private volatile boolean running = true;
    private static final long CHECK_INTERVAL = 5000;  // 每 5 秒检查一次
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * 标记系统是否处于降频状态
     */
    private static volatile boolean isInReducedMode = false;
    
    @Override
    public void run() {
        System.out.println("🔍 [ThresholdThread] 已启动，将定期检查系统负载");
        
        while (running) {
            try {
                // 等待 CHECK_INTERVAL 毫秒后进行下一次检查
                Thread.sleep(CHECK_INTERVAL);
                
                // 检查系统负载
                boolean overloaded = MonitoringThresholdChecker.checkSystemLoad();
                
                String timestamp = LocalDateTime.now().format(formatter);
                String status = MonitoringThresholdChecker.getSystemLoadStatus();
                
                if (overloaded) {
                    // 系统负载高
                    if (!isInReducedMode) {
                        System.out.println(String.format(
                            "[%s] 🚨 系统负载过高，触发降频 - %s",
                            timestamp, status
                        ));
                        
                        // 触发降频：采样率降至 50%
                        SamplingRateManager.reduceAllSamplingRates(0.5f);
                        isInReducedMode = true;
                        
                        logThresholdEvent(timestamp, "REDUCE", 0.5f, status);
                    }
                } else {
                    // 系统负载正常
                    if (isInReducedMode) {
                        System.out.println(String.format(
                            "[%s] ✅ 系统负载恢复正常，取消降频 - %s",
                            timestamp, status
                        ));
                        
                        // 恢复采样率：采样率恢复至 100%
                        SamplingRateManager.restoreFullSampling();
                        isInReducedMode = false;
                        
                        logThresholdEvent(timestamp, "RESTORE", 1.0f, status);
                    }
                }
                
            } catch (InterruptedException e) {
                System.out.println("🛑 [ThresholdThread] 被中断");
                break;
            } catch (Exception e) {
                System.err.println("❌ [ThresholdThread] 异常: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("🛑 [ThresholdThread] 已停止");
    }
    
    /**
     * 记录阈值事件到日志
     */
    private void logThresholdEvent(String timestamp, String event, float samplingRate, String status) {
        System.out.println(String.format(
            "[%s] 📝 EVENT[%s] 采样率: %.2f | %s",
            timestamp, event, samplingRate, status
        ));
    }
    
    /**
     * 停止监控线程
     */
    public void stop() {
        running = false;
    }
    
    /**
     * 获取当前是否处于降频模式
     */
    public static boolean isInReducedMode() {
        return isInReducedMode;
    }
}
