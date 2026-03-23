package com.nebula.server;

import com.nebula.common.protocol.ControlCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server 端采样率管理器
 * 维护各应用的采样率配置，支持灰度下发
 */
public class SamplingRateManager {
    
    /**
     * 应用采样率映射: Key=appName, Value=samplingRate(0.0-1.0)
     */
    private static final ConcurrentHashMap<String, Float> appSamplingRates = new ConcurrentHashMap<>();
    
    /**
     * 设置指定应用的采样率
     * 
     * @param appName 应用名称
     * @param rate 采样率 (0.0 - 1.0)
     */
    public static void setSamplingRate(String appName, float rate) {
        if (rate < 0.0f || rate > 1.0f) {
            System.err.println("❌ 采样率必须在 0.0 - 1.0 之间，当前值: " + rate);
            return;
        }
        
        appSamplingRates.put(appName, rate);
        System.out.println("✅ 应用 [" + appName + "] 采样率已设置: " + rate);
    }
    
    /**
     * 获取指定应用的采样率
     * 
     * @param appName 应用名称
     * @return 采样率，若不存在返回 1.0
     */
    public static float getSamplingRate(String appName) {
        return appSamplingRates.getOrDefault(appName, 1.0f);
    }
    
    /**
     * 向指定应用下发采样率命令
     * 支持灰度：只有列在 appNames 中的应用才会收到命令
     * 
     * @param appNames 目标应用名称列表
     * @param rate 采样率 (0.0 - 1.0)
     */
    public static void broadcastToApps(List<String> appNames, float rate) {
        // 更新本地配置
        for (String appName : appNames) {
            setSamplingRate(appName, rate);
        }
        
        // 构造控制命令
        ControlCommand cmd = new ControlCommand(
            ControlCommand.CommandAction.SET_SAMPLING,
            appNames,
            rate
        );
        
        // 广播给所有在线的 Agent
        ServerHandler.broadcastCommand(cmd);
    }
    
    /**
     * 向所有应用下发采样率命令（全量下发）
     * 
     * @param rate 采样率 (0.0 - 1.0)
     */
    public static void broadcastToAll(float rate) {
        // 构造控制命令（不指定 targetApps，表示所有 Agent 都应执行）
        ControlCommand cmd = new ControlCommand(
            ControlCommand.CommandAction.SET_SAMPLING,
            new ArrayList<>(),  // 空列表表示全量
            rate
        );
        
        // 广播给所有在线的 Agent
        ServerHandler.broadcastCommand(cmd);
    }
    
    /**
     * 降低采样率（用于系统负载过高）
     * 
     * @param factor 降低因子 (0.0 - 1.0)，如 0.5 表示降低为原来的 50%
     */
    public static void reduceAllSamplingRates(float factor) {
        if (factor <= 0.0f || factor > 1.0f) {
            System.err.println("❌ 降低因子必须在 0.0 ~ 1.0 之间，当前值: " + factor);
            return;
        }
        
        float newRate = Math.max(0.01f, factor);  // 至少保留 1% 采样
        System.out.println("📉 系统降频触发，采样率调整为: " + newRate);
        
        broadcastToAll(newRate);
    }
    
    /**
     * 恢复采样率（用于系统负载恢复正常）
     */
    public static void restoreFullSampling() {
        System.out.println("📈 系统负载恢复，采样率调整为: 1.0");
        broadcastToAll(1.0f);
    }
    
    /**
     * 获取所有应用的采样率配置（用于监控面板）
     */
    public static ConcurrentHashMap<String, Float> getAllSamplingRates() {
        return new ConcurrentHashMap<>(appSamplingRates);
    }
    
    /**
     * 清空所有采样率配置
     */
    public static void clearAllRates() {
        appSamplingRates.clear();
        System.out.println("✅ 已清空所有采样率配置");
    }
    
    /**
     * 输出当前采样率配置状态
     */
    public static void printStatus() {
        System.out.println("\n=== Nebula Server 采样率配置状态 ===");
        if (appSamplingRates.isEmpty()) {
            System.out.println("✓ 暂无应用级配置（将使用全局默认 1.0）");
        } else {
            System.out.println("✓ 应用级采样率配置:");
            appSamplingRates.forEach((app, rate) -> 
                System.out.println("  - " + app + ": " + rate)
            );
        }
        System.out.println("✓ 当前在线 Agent 数: " + ServerHandler.getOnlineAgentCount());
        System.out.println("=====================================\n");
    }
}
