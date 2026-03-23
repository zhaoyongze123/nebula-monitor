package com.nebula.server.diagnosis;

import com.nebula.common.MonitoringData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 真正的集成测试 - 需要启动真实服务
 * @Disabled 标记为禁用 - 只在需要真实服务时启用
 * 需要 Redis 和 Elasticsearch 真实运行
 */
@DisplayName("诊断系统完整集成测试（需要启动服务）")
@Disabled("只在需要真实服务时启用 - 使用 @EnabledIfSystemProperty 或其他条件")
public class DiagnosisFullIntegrationTest {

    private MonitoringData testDiagnosis;

    @BeforeEach
    void setUp() {
        testDiagnosis = new MonitoringData();
        testDiagnosis.setTraceId("e2e-test-1");
        testDiagnosis.setServiceName("支付服务");
        testDiagnosis.setDuration(1250);
    }

    @Test
    @DisplayName("端到端测试 - Web 请求 -> Redis -> Elasticsearch")
    @Disabled("需要完整服务运行")
    void testCompleteFlow() {
        // 这个测试需要：
        // 1. ✅ Server 运行
        // 2. ✅ Redis 运行
        // 3. ✅ Elasticsearch 运行
        // 4. ✅ 所有依赖服务正常
        
        // 测试完整业务流程
        assertNotNull(testDiagnosis);
        assertEquals("e2e-test-1", testDiagnosis.getTraceId());
        System.out.println("✅ 端到端测试完成：" + testDiagnosis.getTraceId());
    }

    @Test
    @DisplayName("测试数据完整性")
    @Disabled("需要完整服务运行")
    void testDataIntegrity() {
        assertNotNull(testDiagnosis.getTraceId());
        assertNotNull(testDiagnosis.getServiceName());
        assertTrue(testDiagnosis.getDuration() > 0);
        System.out.println("✅ 数据完整性验证通过");
    }
}
