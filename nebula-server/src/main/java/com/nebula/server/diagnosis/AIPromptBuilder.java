package com.nebula.server.diagnosis;

import com.nebula.common.MonitoringData;
import java.util.logging.Logger;

/**
 * AIPromptBuilder - Constructs well-structured prompts for AI diagnosis
 * Combines trace information, system metrics, and comparative data
 */
public class AIPromptBuilder {
    private static final Logger logger = Logger.getLogger(AIPromptBuilder.class.getName());

    /**
     * Build a comprehensive diagnosis prompt
     * @param data Monitoring data of the slow trace
     * @param metrics System metrics at the time of the trace
     * @param comparative Comparative data for the same method
     * @return Formatted prompt string for AI analysis
     */
    public static String buildDiagnosisPrompt(
            MonitoringData data,
            TraceContextCollector.DiagnosisSystemMetrics metrics,
            TraceContextCollector.DiagnosisComparativeData comparative) {

        StringBuilder prompt = new StringBuilder();

        // System message - 中文版本
        prompt.append("你是一名分布式系统性能诊断专家。请分析以下慢链路数据，提供根本原因分析和可行的优化建议。\n")
              .append("所有回答必须**用中文**输出。\n\n");

        // Trace Information Section - 中文标题
        prompt.append("=== 慢链路信息 ===\n");
        prompt.append(String.format("链路ID: %s\n", data.getTraceId()));
        prompt.append(String.format("服务名: %s\n", data.getServiceName()));
        prompt.append(String.format("方法名: %s\n", data.getMethodName()));
        prompt.append(String.format("耗时: %d 毫秒\n", data.getDuration()));
        prompt.append(String.format("时间戳: %d\n", data.getTimestamp()));

        // System Metrics Section - 中文标题
        prompt.append("\n=== 链路执行时的系统指标 ===\n");
        if (metrics != null) {
            prompt.append(String.format("进程CPU负载: %.2f%%\n", metrics.getProcessCpuLoad() * 100));
            prompt.append(String.format("系统CPU负载: %.2f%%\n", metrics.getSystemCpuLoad() * 100));
            prompt.append(String.format("系统平均负载: %.2f\n", metrics.getSystemLoadAverage()));
            prompt.append(String.format("内存使用率: %.2f%% (%d MB / %d MB)\n",
                    metrics.getMemoryUsagePercent(),
                    metrics.getUsedMemory() / (1024 * 1024),
                    metrics.getMaxMemory() / (1024 * 1024)
            ));
            prompt.append(String.format("GC次数: %d\n", metrics.getGcCount()));
            prompt.append(String.format("GC耗时: %d 毫秒\n", metrics.getGcTimeMs()));
        } else {
            prompt.append("(系统指标暂不可用)\n");
        }

        // Comparative Analysis Section - 中文标题
        prompt.append("\n=== 对比分析 ===\n");
        if (comparative != null && comparative.getSampleCount() > 0) {
            prompt.append(String.format("同方法历史基线 (最近%d个样本):\n", comparative.getSampleCount()));
            prompt.append(String.format("  平均耗时: %d 毫秒\n", comparative.getAverageDuration()));
            prompt.append(String.format("  中位数耗时: %d 毫秒\n", comparative.getMedianDuration()));
            prompt.append(String.format("  P99耗时: %d 毫秒\n", comparative.getP99Duration()));
            long difference = data.getDuration() - comparative.getAverageDuration();
            double percentDiff = ((double) difference / comparative.getAverageDuration()) * 100;
            prompt.append(String.format("  当前链路比平均值慢 %.1f%% (差值: %d 毫秒)\n",
                    percentDiff, difference));
        } else {
            prompt.append("没有可用的对比数据(首次出现或样本不足)\n");
        }

        // Analysis Request - 中文版本
        prompt.append("\n=== 诊断请求 ===\n");
        prompt.append("请基于上述信息进行诊断, **用中文完整回答**以下几点:\n");
        prompt.append("1. 分析该性能问题最可能的根本原因\n");
        prompt.append("2. 将问题分类 (CPU密集型、IO密集型、内存压力、网络延迟、其他)\n");
        prompt.append("3. 提供具体可行的优化建议\n");
        prompt.append("4. 建议开发团队应该采取的监控或调试步骤\n\n");
        prompt.append("请用3-4个自然段中文形式回答。\n");

        logger.fine("Built diagnosis prompt: " + prompt.length() + " characters");
        return prompt.toString();
    }

    /**
     * Build a minimal prompt when comparative data is unavailable
     * Used as fallback when system cannot collect full context
     */
    public static String buildMinimalPrompt(MonitoringData data) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是微服务性能诊断专家。请分析这条慢链路数据并建议可能的根本原因。\n")
              .append("**所有回答必须用中文输出**\n\n");

        prompt.append("链路详情:\n");
        prompt.append(String.format("- 服务: %s, 方法: %s\n", data.getServiceName(), data.getMethodName()));
        prompt.append(String.format("- 耗时: %d 毫秒 (超过1000毫秒阈值)\n", data.getDuration()));
        prompt.append(String.format("- 链路ID: %s\n", data.getTraceId()));

        prompt.append("\n请用中文列出3-4个最可能的原因和快速调试建议。\n");

        return prompt.toString();
    }

    /**
     * Validate that the prompt is well-formed before sending to AI
     * @param prompt Prompt string to validate
     * @return true if prompt is valid, false otherwise
     */
    public static boolean validatePrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            logger.warning("Prompt is null or empty");
            return false;
        }

        if (prompt.length() > 4096) {
            logger.warning("Prompt exceeds maximum length (4096 characters): " + prompt.length());
            return false;
        }

        // Check for required sections - 查找中文标题
        if (!prompt.contains("慢链路信息")) {
            logger.warning("Prompt missing required sections");
            return false;
        }

        return true;
    }

    /**
     * Extract key information from prompt for logging
     */
    public static String extractPromptSummary(String prompt) {
        if (prompt == null) return "(null)";

        // Try to extract trace ID
        String[] lines = prompt.split("\n");
        for (String line : lines) {
            if (line.startsWith("链路ID:")) {
                return line.substring("链路ID:".length()).trim();
            }
        }
        return "(无法提取)";
    }
}
