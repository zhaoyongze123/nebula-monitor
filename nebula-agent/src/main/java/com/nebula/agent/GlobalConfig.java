package com.nebula.agent;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 全局配置容器
 * 存储并管理采样率和其他动态配置，支持业务线/应用级别的不同采样率
 */
public class GlobalConfig {
    
    /**
     * 全局采样率（初始 100%）
     * volatile 确保可见性，所有线程能够立即看到变更
     */
    private static volatile float globalSamplingRate = 1.0f;
    
    /**
     * 按应用名分组的采样率映射
     * Key: applicationName, Value: samplingRate (0.0 - 1.0)
     */
    private static final ConcurrentHashMap<String, Float> appSamplingRates = new ConcurrentHashMap<>();
    
    /**
     * 控制开关 - 是否启用采样率控制
     */
    private static volatile boolean controlEnabled = true;
    
    /**
     * 最后一次收到的控制命令参数（用于诊断）
     */
    private static volatile String lastCommandTimestamp = "N/A";
    private static volatile float lastCommandRate = 1.0f;
    
    /**
     * 获取指定应用的采样率
     * 优先级：应用级采样率 > 全局采样率
     * 
     * @param appName 应用名称
     * @return 采样率 (0.0 - 1.0)
     */
    public static float getSamplingRate(String appName) {
        if (!controlEnabled) {
            return 1.0f;  // 控制功能关闭时返回 100%
        }
        
        if (appName != null && appSamplingRates.containsKey(appName)) {
            return appSamplingRates.get(appName);
        }
        return globalSamplingRate;
    }
    
    /**
     * 更新指定应用的采样率
     * 
     * @param appName 应用名称
     * @param rate 采样率 (0.0 - 1.0)
     */
    public static void updateSamplingRate(String appName, float rate) {
        if (rate < 0.0f || rate > 1.0f) {
            System.err.println("❌ 采样率必须在 0.0 - 1.0 之间，当前值: " + rate);
            return;
        }
        
        if (appName == null || appName.isEmpty()) {
            // 更新全局采样率
            globalSamplingRate = rate;
            System.out.println("📝 全局采样率已更新: " + rate);
        } else {
            // 更新应用级采样率
            appSamplingRates.put(appName, rate);
            System.out.println("📝 应用 [" + appName + "] 采样率已更新: " + rate);
        }
        
        // 记录命令参数用于诊断
        lastCommandTimestamp = System.currentTimeMillis() + "";
        lastCommandRate = rate;
    }
    
    /**
     * 更新全局采样率
     * 
     * @param rate 采样率 (0.0 - 1.0)
     */
    public static void setGlobalSamplingRate(float rate) {
        updateSamplingRate(null, rate);
    }
    
    /**
     * 获取全局采样率
     */
    public static float getGlobalSamplingRate() {
        return globalSamplingRate;
    }
    
    /**
     * 获取所有应用的采样率映射（用于监控）
     */
    public static ConcurrentHashMap<String, Float> getAllAppSamplingRates() {
        return new ConcurrentHashMap<>(appSamplingRates);
    }
    
    /**
     * 清空所有应用级采样率（回到全局采样率）
     */
    public static void clearAppSamplingRates() {
        appSamplingRates.clear();
        System.out.println("📝 已清空所有应用级采样率");
    }
    
    /**
     * 启用/禁用采样率控制
     */
    public static void setControlEnabled(boolean enabled) {
        controlEnabled = enabled;
        System.out.println("📝 采样率控制已" + (enabled ? "启用" : "禁用"));
    }
    
    /**
     * 获取采样率控制是否启用
     */
    public static boolean isControlEnabled() {
        return controlEnabled;
    }
    
    /**
     * 获取最后一次命令的时间戳
     */
    public static String getLastCommandTimestamp() {
        return lastCommandTimestamp;
    }
    
    /**
     * 获取最后一次命令的采样率
     */
    public static float getLastCommandRate() {
        return lastCommandRate;
    }
    
    /**
     * 输出当前配置状态（用于调试）
     */
    public static void printStatus() {
        System.out.println("\n=== Nebula Agent 采样配置状态 ===");
        System.out.println("✓ 全局采样率: " + globalSamplingRate);
        System.out.println("✓ 控制开关: " + (controlEnabled ? "启用" : "禁用"));
        System.out.println("✓ 应用级配置数: " + appSamplingRates.size());
        if (!appSamplingRates.isEmpty()) {
            appSamplingRates.forEach((app, rate) -> 
                System.out.println("  - " + app + ": " + rate)
            );
        }
        System.out.println("✓ 最后命令时间: " + lastCommandTimestamp);
        System.out.println("✓ 最后命令采样率: " + lastCommandRate);
        System.out.println("==================================\n");
    }
}
