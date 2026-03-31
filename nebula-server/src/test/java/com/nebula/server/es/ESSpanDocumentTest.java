package com.nebula.server.es;

import com.nebula.common.MonitoringData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ESSpanDocument 单元测试
 * 验证 MonitoringData 到 ES 文档的转换是否符合 ES_DATA_SCHEMA.md 规范
 */
@DisplayName("ESSpanDocument 单元测试")
public class ESSpanDocumentTest {

    private MonitoringData testData;
    
    @BeforeEach
    void setUp() {
        testData = new MonitoringData();
        testData.setTraceId("e6d31c87797e4e9c");
        testData.setSpanId("30c1a9e5d20f4709");
        testData.setParentSpanId("a099e70456564698");
        testData.setServiceName("gateway-service");
        testData.setOperationName("HTTP GET /api/v1/order/confirm");
        testData.setStatus("OK");
        testData.setDuration(29);
        testData.setTimestamp(1711900800000L);  // epoch_millis
        testData.setSampled(true);
    }
    
    @Nested
    @DisplayName("基础转换测试")
    class BasicConversionTests {
        
        @Test
        @DisplayName("应该正确转换必需字段")
        void testConvertRequiredFields() {
            // Act
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            
            // Assert
            assertEquals("e6d31c87797e4e9c", doc.getTraceId());
            assertEquals("30c1a9e5d20f4709", doc.getSpanId());
            assertEquals("a099e70456564698", doc.getParentSpanId());
            assertEquals("gateway-service", doc.getServiceName());
            assertEquals("HTTP GET /api/v1/order/confirm", doc.getOperationName());
            assertEquals("OK", doc.getStatus());
            assertEquals(29L, doc.getDurationMs());
            assertEquals(1711900800000L, doc.getTimestamp());
            System.out.println("✅ 必需字段转换验证通过");
        }
        
        @Test
        @DisplayName("应该使用 epoch_millis 格式的时间戳")
        void testTimestampFormat() {
            // Arrange - 使用当前时间
            long currentTimeMs = System.currentTimeMillis();
            testData.setTimestamp(currentTimeMs);
            
            // Act
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            
            // Assert
            assertEquals(currentTimeMs, doc.getTimestamp());
            assertTrue(doc.getTimestamp() > 1000000000000L, "时间戳应该是毫秒级（13位数字）");
            System.out.println("✅ 时间戳格式验证通过 (epoch_millis): " + doc.getTimestamp());
        }
        
        @Test
        @DisplayName("应该正确处理 null parent_span_id (root span)")
        void testRootSpanConversion() {
            // Arrange
            testData.setParentSpanId(null);
            
            // Act
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            
            // Assert
            assertNull(doc.getParentSpanId());
            System.out.println("✅ Root span 转换验证通过 (parent_span_id=null)");
        }
    }
    
    @Nested
    @DisplayName("状态字段测试")
    class StatusFieldTests {
        
        @Test
        @DisplayName("应该支持 OK 状态")
        void testOKStatus() {
            testData.setStatus("OK");
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            assertEquals("OK", doc.getStatus());
        }
        
        @Test
        @DisplayName("应该支持 ERROR 状态")
        void testErrorStatus() {
            testData.setStatus("ERROR");
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            assertEquals("ERROR", doc.getStatus());
        }
        
        @Test
        @DisplayName("应该支持 SKIPPED 状态")
        void testSkippedStatus() {
            testData.setStatus("SKIPPED");
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            assertEquals("SKIPPED", doc.getStatus());
        }
        
        @Test
        @DisplayName("应该支持 TIMEOUT 状态")
        void testTimeoutStatus() {
            testData.setStatus("TIMEOUT");
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            assertEquals("TIMEOUT", doc.getStatus());
        }
        
        @Test
        @DisplayName("应该为 null 状态设置默认值 OK")
        void testDefaultStatusForNull() {
            testData.setStatus(null);
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            assertEquals("OK", doc.getStatus());
            System.out.println("✅ 状态字段默认值验证通过");
        }
    }
    
    @Nested
    @DisplayName("可选字段测试")
    class OptionalFieldTests {
        
        @Test
        @DisplayName("应该正确转换异常堆栈")
        void testExceptionStackConversion() {
            // Arrange
            String exceptionStack = "java.lang.NullPointerException: null\n" +
                    "\tat com.example.Service.process(Service.java:42)";
            testData.setExceptionStack(exceptionStack);
            
            // Act
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            
            // Assert
            assertEquals(exceptionStack, doc.getExceptionStack());
            System.out.println("✅ 异常堆栈转换验证通过");
        }
        
        @Test
        @DisplayName("应该正确转换 HTTP 状态码")
        void testHttpStatusConversion() {
            testData.setHttpStatus(200);
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            assertEquals(Integer.valueOf(200), doc.getHttpStatus());
        }
        
        @Test
        @DisplayName("应该正确转换错误类型和错误码")
        void testErrorTypeAndCodeConversion() {
            testData.setErrorType("Timeout");
            testData.setErrorCode("TIMEOUT_001");
            
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            
            assertEquals("Timeout", doc.getErrorType());
            assertEquals("TIMEOUT_001", doc.getErrorCode());
        }
        
        @Test
        @DisplayName("应该正确转换环境信息")
        void testEnvironmentInfoConversion() {
            testData.setEnvironment("prod");
            testData.setRegion("us-east-1");
            testData.setCluster("cluster-1");
            testData.setInstanceId("i-123456");
            
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            
            assertEquals("prod", doc.getEnvironment());
            assertEquals("us-east-1", doc.getRegion());
            assertEquals("cluster-1", doc.getCluster());
            assertEquals("i-123456", doc.getInstanceId());
            System.out.println("✅ 环境信息转换验证通过");
        }
    }
    
    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTests {
        
        @Test
        @DisplayName("应该通过 Builder 创建完整文档")
        void testBuilderPattern() {
            // Act
            ESSpanDocument doc = ESSpanDocument.builder()
                    .traceId("trace-001")
                    .spanId("span-001")
                    .parentSpanId("parent-001")
                    .serviceName("test-service")
                    .operationName("testMethod")
                    .status("OK")
                    .durationMs(100)
                    .timestamp(System.currentTimeMillis())
                    .environment("dev")
                    .sampled(true)
                    .build();
            
            // Assert
            assertEquals("trace-001", doc.getTraceId());
            assertEquals("span-001", doc.getSpanId());
            assertEquals("parent-001", doc.getParentSpanId());
            assertEquals("test-service", doc.getServiceName());
            assertEquals("testMethod", doc.getOperationName());
            assertEquals("OK", doc.getStatus());
            assertEquals(100L, doc.getDurationMs());
            assertEquals("dev", doc.getEnvironment());
            assertTrue(doc.isSampled());
            System.out.println("✅ Builder 模式验证通过");
        }
    }
    
    @Nested
    @DisplayName("向后兼容测试")
    class BackwardCompatibilityTests {
        
        @Test
        @DisplayName("应该从旧版 MonitoringData 正确转换")
        void testLegacyDataConversion() {
            // Arrange - 使用旧版构造器
            MonitoringData legacyData = new MonitoringData(
                    "trace-legacy",
                    "BusinessService.process",
                    150,
                    System.currentTimeMillis(),
                    "legacy-service"
            );
            
            // Act
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(legacyData);
            
            // Assert
            assertEquals("trace-legacy", doc.getTraceId());
            assertNotNull(doc.getSpanId(), "应该自动生成 spanId");
            assertEquals("legacy-service", doc.getServiceName());
            assertEquals("BusinessService.process", doc.getOperationName());
            assertEquals(150L, doc.getDurationMs());
            assertEquals("OK", doc.getStatus());  // 默认状态
            System.out.println("✅ 向后兼容转换验证通过");
        }
        
        @Test
        @DisplayName("应该自动生成 spanId")
        void testAutoGenerateSpanId() {
            // Arrange
            MonitoringData data = new MonitoringData();
            data.setTraceId("test-trace");
            data.setServiceName("test-service");
            
            // Act
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(data);
            
            // Assert
            assertNotNull(doc.getSpanId());
            assertEquals(16, doc.getSpanId().length(), "spanId 应该是 16 位十六进制字符串");
            System.out.println("✅ 自动生成 spanId 验证通过: " + doc.getSpanId());
        }
    }
    
    @Nested
    @DisplayName("序列化格式测试")
    class SerializationTests {
        
        @Test
        @DisplayName("toString 应该包含关键信息")
        void testToString() {
            ESSpanDocument doc = ESSpanDocument.fromMonitoringData(testData);
            String str = doc.toString();
            
            assertTrue(str.contains("trace_id"));
            assertTrue(str.contains("span_id"));
            assertTrue(str.contains("service_name"));
            assertTrue(str.contains("operation_name"));
            assertTrue(str.contains("status"));
            assertTrue(str.contains("duration_ms"));
            assertTrue(str.contains("timestamp"));
            System.out.println("✅ toString 格式验证通过: " + str);
        }
    }
}
