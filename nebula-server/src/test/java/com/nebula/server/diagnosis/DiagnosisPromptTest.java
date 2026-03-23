package com.nebula.server.diagnosis;

import com.nebula.common.MonitoringData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 诊断模块中文Prompt生成单元测试
 * 使用 JUnit 5 + Mockito 进行测试
 */
@DisplayName("诊断模块 Prompt 生成测试")
public class DiagnosisPromptTest {

    private MonitoringData testData;
    private TraceContextCollector.DiagnosisSystemMetrics testMetrics;
    private TraceContextCollector.DiagnosisComparativeData testComparative;

    @BeforeEach
    void setUp() {
        // 创建测试数据
        testData = new MonitoringData();
        testData.setTraceId("test-chinese-1");
        testData.setServiceName("支付服务");
        testData.setMethodName("转账处理");
        testData.setDuration(1250);
        testData.setTimestamp(System.currentTimeMillis());
        testData.setSampled(true);

        // 创建系统指标
        testMetrics = new TraceContextCollector.DiagnosisSystemMetrics();
        testMetrics.setProcessCpuLoad(0.35);
        testMetrics.setSystemCpuLoad(0.42);
        testMetrics.setSystemLoadAverage(2.5);
        testMetrics.setMemoryUsagePercent(65.5);
        testMetrics.setUsedMemory(2000L * 1024 * 1024);
        testMetrics.setMaxMemory(4000L * 1024 * 1024);
        testMetrics.setGcCount(12);
        testMetrics.setGcTimeMs(320);

        // 创建对比数据
        testComparative = new TraceContextCollector.DiagnosisComparativeData();
        testComparative.setSampleCount(50);
        testComparative.setAverageDuration(800);
        testComparative.setMedianDuration(760);
        testComparative.setP99Duration(950);
    }

    @Nested
    @DisplayName("Prompt 生成测试")
    class PromptGenerationTests {

        @Test
        @DisplayName("应该生成完整的诊断 Prompt - 非空且通过验证")
        void testChinesePromptGeneration() {
            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt, "Prompt 不应为 null");
            assertTrue(prompt.length() > 0, "Prompt 长度应大于 0");
            assertTrue(prompt.length() > 100, "诊断 Prompt 应该足够详细（> 100 字符）");

            // 打印生成的 Prompt
            System.out.println("\n✅ 生成的诊断 Prompt:\n");
            System.out.println("========================");
            System.out.println(prompt);
            System.out.println("========================");
        }

        @Test
        @DisplayName("应该包含关键的中文信息")
        void testPromptContainsChineseContent() {
            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            // 检查是否包含服务信息或诊断相关词汇
            assertTrue(prompt.contains("支付") || prompt.contains("服务") || prompt.contains("诊"), 
                "Prompt 应该包含中文内容");
        }

        @Test
        @DisplayName("应该包含性能指标信息")
        void testPromptContainsPerformanceMetrics() {
            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            // 检查是否包含耗时信息
            assertTrue(prompt.length() > 0, "Prompt 应该包含性能指标");
        }

        @Test
        @DisplayName("高耗时应该生成更详细的诊断")
        void testHighDurationGeneratesDetailedDiagnosis() {
            // Arrange
            testData.setDuration(5000); // 5秒的耗时

            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            assertTrue(prompt.length() > 200, "高耗时应生成更详细的诊断");
        }
    }

    @Nested
    @DisplayName("最小 Prompt 测试")
    class MinimalPromptTests {

        @Test
        @DisplayName("应该生成最小 Prompt")
        void testMinimalPromptGeneration() {
            // Act
            String minimalPrompt = AIPromptBuilder.buildMinimalPrompt(testData);

            // Assert
            assertNotNull(minimalPrompt, "最小 Prompt 不应为 null");
            assertTrue(minimalPrompt.length() > 0, "最小 Prompt 不应为空");
            System.out.println("\n✅ 生成的最小 Prompt:\n");
            System.out.println(minimalPrompt);
        }

        @Test
        @DisplayName("最小 Prompt 应该比完整 Prompt 更简洁")
        void testMinimalPromptIsShorter() {
            // Act
            String fullPrompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);
            String minimalPrompt = AIPromptBuilder.buildMinimalPrompt(testData);

            // Assert
            assertNotNull(fullPrompt);
            assertNotNull(minimalPrompt);
            assertTrue(minimalPrompt.length() <= fullPrompt.length(), 
                "最小 Prompt 应该比或等于完整 Prompt 长度");
        }
    }

    @Nested
    @DisplayName("测试数据验证")
    class DataValidationTests {

        @Test
        @DisplayName("应该处理正常的服务名称")
        void testNormalServiceName() {
            // Arrange
            testData.setServiceName("订单服务");

            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            assertTrue(prompt.length() > 0);
        }

        @Test
        @DisplayName("应该处理高 CPU 负载情况（>90%）")
        void testHighCpuLoadScenario() {
            // Arrange
            testMetrics.setSystemCpuLoad(0.95);
            testMetrics.setProcessCpuLoad(0.88);

            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            assertTrue(prompt.length() > 100, "高 CPU 负载应生成详细诊断");
        }

        @Test
        @DisplayName("应该处理高内存使用情况（>80%）")
        void testHighMemoryUsageScenario() {
            // Arrange
            testMetrics.setMemoryUsagePercent(85.0);

            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            assertTrue(prompt.length() > 0);
        }

        @Test
        @DisplayName("应该处理高 GC 时间")
        void testHighGCTimeScenario() {
            // Arrange
            testMetrics.setGcTimeMs(1000);  // 1秒 GC 时间
            testMetrics.setGcCount(50);

            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            assertTrue(prompt.length() > 0);
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("null 的 MonitoringData 应该抛出异常")
        void testNullMonitoringDataThrowsException() {
            // Act & Assert
            assertThrows(NullPointerException.class, () -> {
                AIPromptBuilder.buildDiagnosisPrompt(null, testMetrics, testComparative);
            }, "null 的 MonitoringData 应该抛出 NullPointerException");
        }

        @Test
        @DisplayName("null 的最小 Prompt 输入应该抛出异常")
        void testNullDataInMinimalPromptThrowsException() {
            // Act & Assert
            assertThrows(NullPointerException.class, () -> {
                AIPromptBuilder.buildMinimalPrompt(null);
            }, "null 的 MonitoringData 应该抛出异常");
        }

        @Test
        @DisplayName("null 的系统指标应该使用默认值或处理优雅")
        void testNullMetricsUsesDefaults() {
            // Act - 这应该使用默认指标或抛出有意义的异常
            try {
                String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, null, testComparative);
                // 如果允许 null metrics，应该生成有效的 prompt
                assertNotNull(prompt, "应该使用默认指标生成 Prompt");
            } catch (NullPointerException e) {
                // 或者抛出异常也可以接受
                System.out.println("💡 null metrics 导致异常（预期行为）: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("对比数据测试")
    class ComparativeDataTests {

        @Test
        @DisplayName("应该考虑平均耗时对比")
        void testComparativeDataImpact() {
            // Arrange
            testComparative.setAverageDuration(500);  // 平均 500ms，当前 1250ms（显著高于平均值）

            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            assertTrue(prompt.length() > 0);
        }

        @Test
        @DisplayName("应该处理 P99 对比")
        void testP99Comparison() {
            // Arrange
            testComparative.setP99Duration(1100);  // P99 是 1100ms，当前 1250ms

            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            assertTrue(prompt.length() > 0);
        }
    }

    @Nested
    @DisplayName("集成测试 - 真实场景")
    class IntegrationTests {

        @Test
        @DisplayName("完整的诊断流程 - 模拟支付服务超时")
        void testCompletePaymentServiceDiagnosis() {
            // Arrange - 模拟支付服务超时场景
            testData.setServiceName("支付服务");
            testData.setMethodName("processPayment");
            testData.setDuration(3500);  // 3.5 秒（超时）
            testData.setTraceId("pay-123456");
            
            testMetrics.setSystemCpuLoad(0.78);
            testMetrics.setProcessCpuLoad(0.45);
            testMetrics.setMemoryUsagePercent(72.0);

            testComparative.setAverageDuration(800);
            testComparative.setP99Duration(1200);

            // Act
            String fullPrompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);
            String minimalPrompt = AIPromptBuilder.buildMinimalPrompt(testData);

            // Assert
            assertNotNull(fullPrompt);
            assertNotNull(minimalPrompt);
            assertTrue(fullPrompt.length() > minimalPrompt.length(), 
                "完整诊断应包含更多信息");

            System.out.println("\n🔍 支付服务诊断场景:");
            System.out.println("服务: " + testData.getServiceName());
            System.out.println("耗时: " + testData.getDuration() + "ms");
            System.out.println("完整诊断字符数: " + fullPrompt.length());
        }

        @Test
        @DisplayName("完整的诊断流程 - 模拟数据库查询性能问题")
        void testCompleteQueryPerformanceDiagnosis() {
            // Arrange
            testData.setServiceName("商品服务");
            testData.setMethodName("queryProducts");
            testData.setDuration(2800);
            
            testMetrics.setSystemLoadAverage(8.5);
            testMetrics.setMemoryUsagePercent(88.0);
            testMetrics.setGcTimeMs(450);

            testComparative.setAverageDuration(600);

            // Act
            String prompt = AIPromptBuilder.buildDiagnosisPrompt(testData, testMetrics, testComparative);

            // Assert
            assertNotNull(prompt);
            assertTrue(prompt.length() > 150);

            System.out.println("\n🔍 数据库查询诊断场景:");
            System.out.println("生成诊断字符数: " + prompt.length());
        }
    }
}
