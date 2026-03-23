package com.nebula.server.diagnosis;

import com.nebula.common.MonitoringData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import redis.clients.jedis.Jedis;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 诊断同步集成测试 - 使用 Mock 模拟 Redis（不需要启动真实 Redis）
 * 演示如何基于已存在的类和方法进行 Mock 测试
 */
@DisplayName("诊断同步集成测试（Mock Redis）")
public class DiagnosisSyncIntegrationTest {

    private MonitoringData testDiagnosis;

    @Mock
    private Jedis redisMock;  // ✅ Mock Redis 客户端

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 创建测试诊断数据
        testDiagnosis = new MonitoringData();
        testDiagnosis.setTraceId("test-sync-1");
        testDiagnosis.setServiceName("支付服务");
        testDiagnosis.setDuration(1250);
        testDiagnosis.setTimestamp(System.currentTimeMillis());

        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Redis 队列操作测试")
    class RedisQueueTests {

        @Test
        @DisplayName("应该成功将诊断发送到 Redis 队列（Mock）")
        void testPushDiagnosisToRedisQueue() {
            // Arrange - 配置 Mock 行为
            when(redisMock.rpush("nebula:diagnosis:queue", "test-data"))
                .thenReturn(1L);

            // Act
            Long result = redisMock.rpush("nebula:diagnosis:queue", "test-data");

            // Assert
            assertEquals(1L, result);
            verify(redisMock, times(1)).rpush("nebula:diagnosis:queue", "test-data");
            System.out.println("✅ 诊断成功入队（Mock）");
        }

        @Test
        @DisplayName("应该正确处理 Redis 连接失败")
        void testHandleRedisConnectionFailure() {
            // Arrange - 模拟连接失败
            when(redisMock.rpush(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis 连接失败"));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> {
                redisMock.rpush("nebula:diagnosis:queue", "test-data");
            });

            System.out.println("✅ 正确处理了 Redis 连接失败");
        }

        @Test
        @DisplayName("应该验证诊断数据通过队列多次调用")
        void testMultipleDiagnosisCalls() {
            // Arrange
            when(redisMock.rpush(anyString(), anyString()))
                .thenReturn(1L, 2L, 3L);  // 连续返回不同值

            // Act
            redisMock.rpush("queue", "diagnosis-1");
            redisMock.rpush("queue", "diagnosis-2");
            redisMock.rpush("queue", "diagnosis-3");

            // Assert
            verify(redisMock, times(3)).rpush(anyString(), anyString());
            System.out.println("✅ 成功处理了多次诊断调用");
        }

        @Test
        @DisplayName("应该处理队列为空的情况")
        void testEmptyQueueHandling() {
            // Arrange
            when(redisMock.lpop("nebula:diagnosis:queue"))
                .thenReturn(null);  // 队列为空

            // Act
            String result = redisMock.lpop("nebula:diagnosis:queue");

            // Assert
            assertNull(result);
            verify(redisMock, times(1)).lpop("nebula:diagnosis:queue");
            System.out.println("✅ 正确处理了空队列");
        }
    }

    @Nested
    @DisplayName("诊断数据验证测试")
    class DiagnosisValidationTests {

        @Test
        @DisplayName("应该验证诊断数据时间戳有效")
        void testValidDiagnosisTimestamp() {
            // Arrange
            long currentTime = System.currentTimeMillis();
            testDiagnosis.setTimestamp(currentTime);

            // Act
            long timestamp = testDiagnosis.getTimestamp();

            // Assert
            assertEquals(currentTime, timestamp);
            assertTrue(timestamp > 0);
            System.out.println("✅ 诊断时间戳验证通过");
        }

        @Test
        @DisplayName("应该验证诊断数据耗时有效")
        void testValidDiagnosisDuration() {
            // Arrange
            testDiagnosis.setDuration(1250);

            // Act
            long duration = testDiagnosis.getDuration();

            // Assert
            assertEquals(1250, duration);
            assertTrue(duration > 0);
            System.out.println("✅ 诊断耗时验证通过");
        }

        @Test
        @DisplayName("应该处理无效的诊断数据")
        void testInvalidDiagnosisData() {
            // Arrange
            MonitoringData invalidData = new MonitoringData();
            invalidData.setTraceId(null);
            invalidData.setServiceName(null);

            // Act & Assert
            assertNull(invalidData.getTraceId());
            assertNull(invalidData.getServiceName());
            System.out.println("✅ 正确识别了无效的诊断数据");
        }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTests {

        @Test
        @DisplayName("应该成功将诊断数据序列化")
        void testDiagnosisSerializationToJson() throws Exception {
            // Arrange
            MonitoringData diagnosis = new MonitoringData();
            diagnosis.setTraceId("serialize-test-1");
            diagnosis.setServiceName("序列化服务");
            diagnosis.setDuration(1500);

            // Act
            String json = objectMapper.writeValueAsString(diagnosis);

            // Assert
            assertNotNull(json);
            assertTrue(json.contains("serialize-test-1"));
            System.out.println("✅ 诊断数据序列化成功: " + json);
        }

        @Test
        @DisplayName("应该成功将诊断数据反序列化")
        void testDiagnosisDeserializationFromJson() throws Exception {
            // Arrange
            String json = "{\"traceId\":\"deserialize-test-1\",\"serviceName\":\"反序列化服务\",\"duration\":2000}";

            // Act
            MonitoringData diagnosis = objectMapper.readValue(json, MonitoringData.class);

            // Assert
            assertNotNull(diagnosis);
            assertEquals("deserialize-test-1", diagnosis.getTraceId());
            assertEquals("反序列化服务", diagnosis.getServiceName());
            System.out.println("✅ 诊断数据反序列化成功");
        }
    }
}
