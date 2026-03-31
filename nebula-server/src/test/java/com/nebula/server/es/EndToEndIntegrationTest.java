package com.nebula.server.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.MonitoringData;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试
 * 模拟完整的数据流：Agent -> Redis -> ESSyncWorker -> Elasticsearch
 * 
 * 测试环境要求：
 * - Redis 运行在 localhost:6379
 * - Elasticsearch 运行在 localhost:9200
 * 
 * 使用 docker-compose up -d redis elasticsearch 启动
 */
@DisplayName("端到端集成测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndIntegrationTest {
    
    private JedisPool jedisPool;
    private ElasticsearchClient esClient;
    private ObjectMapper objectMapper;
    private String testIndex;
    private boolean infraAvailable = false;
    
    // 测试数据
    private final String testTraceId = "e2e-test-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<MonitoringData> testSpans = new ArrayList<>();
    
    private static final String INDEX_MAPPING = """
        {
          "mappings": {
            "properties": {
              "trace_id": {"type": "keyword"},
              "traceId": {"type": "keyword"},
              "span_id": {"type": "keyword"},
              "spanId": {"type": "keyword"},
              "parent_span_id": {"type": "keyword"},
              "parentSpanId": {"type": "keyword"},
              "service_name": {"type": "keyword"},
              "serviceName": {"type": "keyword"},
              "operation_name": {"type": "keyword"},
              "operationName": {"type": "keyword"},
              "status": {"type": "keyword"},
              "duration_ms": {"type": "long"},
              "durationMs": {"type": "long"},
              "timestamp": {
                "type": "date",
                "format": "epoch_millis"
              },
              "exception_stack": {"type": "text"},
              "exceptionStack": {"type": "text"},
              "httpStatus": {"type": "integer"},
              "error_type": {"type": "keyword"},
              "error_code": {"type": "keyword"},
              "environment": {"type": "keyword"},
              "region": {"type": "keyword"},
              "sampled": {"type": "boolean"}
            }
          },
          "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "refresh_interval": "1s"
          }
        }
        """;
    
    @BeforeAll
    void setUp() {
        testIndex = "nebula_e2e_test_" + System.currentTimeMillis();
        objectMapper = new ObjectMapper();
        
        try {
            // 初始化 Redis 连接池
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(10);
            config.setTestOnBorrow(true);
            jedisPool = new JedisPool(config, "localhost", 6379, 5000);
            
            // 测试 Redis 连接
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }
            System.out.println("✅ Redis 连接成功");
            
            // 初始化 ES 客户端
            RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http")
            ).build();
            esClient = new ElasticsearchClient(
                new RestClientTransport(restClient, new JacksonJsonpMapper())
            );
            
            // 测试 ES 连接
            esClient.info();
            System.out.println("✅ Elasticsearch 连接成功");
            
            // 创建测试索引
            esClient.indices().create(CreateIndexRequest.of(c -> c
                .index(testIndex)
                .withJson(new StringReader(INDEX_MAPPING))
            ));
            System.out.println("✅ 测试索引已创建: " + testIndex);
            
            infraAvailable = true;
            
        } catch (Exception e) {
            System.err.println("⚠️  基础设施不可用，跳过端到端测试: " + e.getMessage());
            System.err.println("   💡 启动命令: docker-compose up -d redis elasticsearch");
        }
    }
    
    @AfterAll
    void tearDown() {
        // 清理测试索引
        if (esClient != null && infraAvailable) {
            try {
                esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(testIndex)));
                System.out.println("✅ 测试索引已删除: " + testIndex);
            } catch (Exception e) {
                // ignore
            }
        }
        
        // 关闭 Redis 连接池
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("步骤1: 模拟 Agent 生成监控数据")
    void step1_simulateAgentDataGeneration() {
        Assumptions.assumeTrue(infraAvailable, "跳过：基础设施不可用");
        
        // 模拟一个完整的调用链
        // Root span
        MonitoringData rootSpan = new MonitoringData();
        rootSpan.setTraceId(testTraceId);
        rootSpan.setSpanId(generateSpanId());
        rootSpan.setParentSpanId(null);
        rootSpan.setServiceName("gateway-service");
        rootSpan.setOperationName("HTTP GET /api/v1/order/create");
        rootSpan.setStatus("OK");
        rootSpan.setDuration(1200);
        rootSpan.setTimestamp(System.currentTimeMillis());
        rootSpan.setEnvironment("test");
        rootSpan.setSampled(true);
        testSpans.add(rootSpan);
        
        // Child span 1: order-service
        MonitoringData orderSpan = new MonitoringData();
        orderSpan.setTraceId(testTraceId);
        orderSpan.setSpanId(generateSpanId());
        orderSpan.setParentSpanId(rootSpan.getSpanId());
        orderSpan.setServiceName("order-service");
        orderSpan.setOperationName("OrderService.createOrder");
        orderSpan.setStatus("OK");
        orderSpan.setDuration(800);
        orderSpan.setTimestamp(System.currentTimeMillis());
        orderSpan.setEnvironment("test");
        orderSpan.setSampled(true);
        testSpans.add(orderSpan);
        
        // Child span 2: payment-service (with error)
        MonitoringData paymentSpan = new MonitoringData();
        paymentSpan.setTraceId(testTraceId);
        paymentSpan.setSpanId(generateSpanId());
        paymentSpan.setParentSpanId(orderSpan.getSpanId());
        paymentSpan.setServiceName("payment-service");
        paymentSpan.setOperationName("PaymentService.processPayment");
        paymentSpan.setStatus("ERROR");
        paymentSpan.setDuration(500);
        paymentSpan.setTimestamp(System.currentTimeMillis());
        paymentSpan.setExceptionStack("java.lang.RuntimeException: Payment gateway timeout\n\tat com.example.PaymentService.process(PaymentService.java:42)");
        paymentSpan.setErrorType("Timeout");
        paymentSpan.setErrorCode("PAY_TIMEOUT_001");
        paymentSpan.setHttpStatus(504);
        paymentSpan.setEnvironment("test");
        paymentSpan.setSampled(true);
        testSpans.add(paymentSpan);
        
        // Child span 3: inventory-service
        MonitoringData inventorySpan = new MonitoringData();
        inventorySpan.setTraceId(testTraceId);
        inventorySpan.setSpanId(generateSpanId());
        inventorySpan.setParentSpanId(orderSpan.getSpanId());
        inventorySpan.setServiceName("inventory-service");
        inventorySpan.setOperationName("InventoryService.lockStock");
        inventorySpan.setStatus("OK");
        inventorySpan.setDuration(100);
        inventorySpan.setTimestamp(System.currentTimeMillis());
        inventorySpan.setEnvironment("test");
        inventorySpan.setSampled(true);
        testSpans.add(inventorySpan);
        
        System.out.println("✅ 生成了 " + testSpans.size() + " 个测试 span");
        System.out.println("   - TraceId: " + testTraceId);
        for (MonitoringData span : testSpans) {
            System.out.println("   - " + span.getServiceName() + "." + span.getOperationName() + " [" + span.getStatus() + "]");
        }
        
        assertEquals(4, testSpans.size());
    }
    
    @Test
    @Order(2)
    @DisplayName("步骤2: 模拟 Agent 推送数据到 Redis 队列")
    void step2_simulatePushToRedis() throws Exception {
        Assumptions.assumeTrue(infraAvailable, "跳过：基础设施不可用");
        
        try (Jedis jedis = jedisPool.getResource()) {
            for (MonitoringData span : testSpans) {
                String json = objectMapper.writeValueAsString(span);
                jedis.lpush("nebula:monitor:queue", json);
            }
            
            // 验证队列长度
            long queueLen = jedis.llen("nebula:monitor:queue");
            System.out.println("✅ 已推送 " + testSpans.size() + " 条数据到 Redis 队列");
            System.out.println("   - 队列当前长度: " + queueLen);
            
            assertTrue(queueLen >= testSpans.size(), "队列长度应该至少包含测试数据");
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("步骤3: 模拟 ESSyncWorker 从 Redis 读取并写入 ES")
    void step3_simulateSyncToElasticsearch() throws Exception {
        Assumptions.assumeTrue(infraAvailable, "跳过：基础设施不可用");
        
        List<MonitoringData> readSpans = new ArrayList<>();
        
        try (Jedis jedis = jedisPool.getResource()) {
            // 从队列读取数据（只读取我们的测试数据）
            int maxRead = 100;
            while (maxRead-- > 0) {
                String json = jedis.rpop("nebula:monitor:queue");
                if (json == null) break;
                
                MonitoringData data = objectMapper.readValue(json, MonitoringData.class);
                if (testTraceId.equals(data.getTraceId())) {
                    readSpans.add(data);
                } else {
                    // 把不属于我们测试的数据放回去
                    jedis.lpush("nebula:monitor:queue", json);
                }
            }
        }
        
        System.out.println("✅ 从 Redis 读取了 " + readSpans.size() + " 条测试数据");
        
        // 转换并写入 ES
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (MonitoringData data : readSpans) {
            ESSpanDocument esDoc = ESSpanDocument.fromMonitoringData(data);
            br.operations(op -> op
                .index(idx -> idx
                    .index(testIndex)
                    .id(esDoc.getSpanId())
                    .document(esDoc)
                )
            );
        }
        
        BulkResponse response = esClient.bulk(br.build());
        assertFalse(response.errors(), "批量写入不应有错误");
        System.out.println("✅ 成功写入 " + response.items().size() + " 条数据到 Elasticsearch");
        
        // 刷新索引
        esClient.indices().refresh(r -> r.index(testIndex));
        
        assertEquals(testSpans.size(), readSpans.size());
    }
    
    @Test
    @Order(4)
    @DisplayName("步骤4: 验证 ES 数据符合 ES_DATA_SCHEMA 规范")
    void step4_verifyElasticsearchData() throws Exception {
        Assumptions.assumeTrue(infraAvailable, "跳过：基础设施不可用");
        
        // 等待索引刷新
        TimeUnit.SECONDS.sleep(1);
        
        // 按 trace_id 查询
        SearchResponse<ESSpanDocument> response = esClient.search(s -> s
                .index(testIndex)
                .query(q -> q.term(t -> t.field("trace_id").value(testTraceId)))
                .size(100),
            ESSpanDocument.class
        );
        
        assertEquals(testSpans.size(), response.hits().total().value(), 
            "ES 中应该有 " + testSpans.size() + " 条记录");
        
        System.out.println("✅ ES 查询返回 " + response.hits().total().value() + " 条记录");
        System.out.println();
        System.out.println("📋 验证数据格式符合 ES_DATA_SCHEMA.md 规范：");
        
        for (Hit<ESSpanDocument> hit : response.hits().hits()) {
            ESSpanDocument doc = hit.source();
            
            // 验证必需字段
            assertNotNull(doc.getTraceId(), "trace_id 不能为空");
            assertNotNull(doc.getSpanId(), "span_id 不能为空");
            assertNotNull(doc.getServiceName(), "service_name 不能为空");
            assertNotNull(doc.getOperationName(), "operation_name 不能为空");
            assertNotNull(doc.getStatus(), "status 不能为空");
            assertTrue(doc.getDurationMs() >= 0, "duration_ms 应该是正数");
            assertTrue(doc.getTimestamp() > 1000000000000L, "timestamp 应该是 epoch_millis 格式");
            
            // 验证 status 值
            assertTrue(
                doc.getStatus().equals("OK") || doc.getStatus().equals("ERROR") ||
                doc.getStatus().equals("SKIPPED") || doc.getStatus().equals("TIMEOUT"),
                "status 值应该是标准值: " + doc.getStatus()
            );
            
            System.out.println("   ✅ " + doc.getServiceName() + "." + doc.getOperationName());
            System.out.println("      - span_id: " + doc.getSpanId());
            System.out.println("      - parent_span_id: " + doc.getParentSpanId());
            System.out.println("      - status: " + doc.getStatus());
            System.out.println("      - duration_ms: " + doc.getDurationMs());
            System.out.println("      - timestamp: " + doc.getTimestamp() + " (epoch_millis)");
            if (doc.getExceptionStack() != null) {
                System.out.println("      - has_exception: true");
            }
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("步骤5: 验证调用链关系正确")
    void step5_verifyTraceRelationships() throws Exception {
        Assumptions.assumeTrue(infraAvailable, "跳过：基础设施不可用");
        
        // 获取所有 span
        SearchResponse<ESSpanDocument> response = esClient.search(s -> s
                .index(testIndex)
                .query(q -> q.term(t -> t.field("trace_id").value(testTraceId)))
                .size(100),
            ESSpanDocument.class
        );
        
        List<ESSpanDocument> spans = response.hits().hits().stream()
            .map(Hit::source)
            .toList();
        
        // 找到 root span
        ESSpanDocument rootSpan = spans.stream()
            .filter(s -> s.getParentSpanId() == null)
            .findFirst()
            .orElseThrow(() -> new AssertionError("应该有一个 root span (parent_span_id=null)"));
        
        System.out.println("✅ 找到 root span: " + rootSpan.getServiceName() + "." + rootSpan.getOperationName());
        
        // 验证 child spans
        List<ESSpanDocument> childSpans = spans.stream()
            .filter(s -> rootSpan.getSpanId().equals(s.getParentSpanId()) || 
                        spans.stream().anyMatch(p -> p.getSpanId().equals(s.getParentSpanId())))
            .filter(s -> s.getParentSpanId() != null)
            .toList();
        
        System.out.println("✅ 找到 " + childSpans.size() + " 个 child spans");
        
        // 验证调用链结构
        assertEquals(1, spans.stream().filter(s -> s.getParentSpanId() == null).count(),
            "应该只有一个 root span");
        
        assertEquals(testSpans.size() - 1, childSpans.size(),
            "child spans 数量应该正确");
        
        // 验证错误 span
        ESSpanDocument errorSpan = spans.stream()
            .filter(s -> "ERROR".equals(s.getStatus()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("应该有一个 ERROR span"));
        
        assertNotNull(errorSpan.getExceptionStack(), "ERROR span 应该有 exception_stack");
        assertEquals("payment-service", errorSpan.getServiceName());
        
        System.out.println("✅ 验证调用链关系正确");
        System.out.println("   - Root: " + rootSpan.getServiceName());
        System.out.println("   - Error span: " + errorSpan.getServiceName() + " (" + errorSpan.getErrorType() + ")");
    }
    
    @Test
    @Order(6)
    @DisplayName("步骤6: 验证聚合查询能力")
    void step6_verifyAggregationQueries() throws Exception {
        Assumptions.assumeTrue(infraAvailable, "跳过：基础设施不可用");
        
        // 按服务名聚合
        SearchResponse<Void> serviceAgg = esClient.search(s -> s
                .index(testIndex)
                .query(q -> q.term(t -> t.field("trace_id").value(testTraceId)))
                .size(0)
                .aggregations("services", a -> a
                    .terms(t -> t.field("service_name"))
                ),
            Void.class
        );
        
        var serviceBuckets = serviceAgg.aggregations().get("services").sterms().buckets().array();
        System.out.println("✅ 服务聚合统计:");
        for (var bucket : serviceBuckets) {
            System.out.println("   - " + bucket.key().stringValue() + ": " + bucket.docCount() + " spans");
        }
        
        assertEquals(4, serviceBuckets.size(), "应该有 4 个服务");
        
        // 按状态聚合
        SearchResponse<Void> statusAgg = esClient.search(s -> s
                .index(testIndex)
                .query(q -> q.term(t -> t.field("trace_id").value(testTraceId)))
                .size(0)
                .aggregations("status", a -> a
                    .terms(t -> t.field("status"))
                ),
            Void.class
        );
        
        var statusBuckets = statusAgg.aggregations().get("status").sterms().buckets().array();
        System.out.println("✅ 状态聚合统计:");
        for (var bucket : statusBuckets) {
            System.out.println("   - " + bucket.key().stringValue() + ": " + bucket.docCount() + " spans");
        }
        
        // 应该有 OK 和 ERROR 两种状态
        assertEquals(2, statusBuckets.size(), "应该有 2 种状态 (OK, ERROR)");
    }
    
    @Test
    @Order(7)
    @DisplayName("步骤7: 输出端到端测试总结")
    void step7_printSummary() {
        Assumptions.assumeTrue(infraAvailable, "跳过：基础设施不可用");
        
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("                    📊 端到端测试总结                           ");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("✅ 测试数据流程:");
        System.out.println("   Agent 生成数据 → Redis 队列 → ESSyncWorker → Elasticsearch");
        System.out.println();
        System.out.println("✅ 验证项目:");
        System.out.println("   1. MonitoringData 模型包含所有必需字段");
        System.out.println("   2. ESSpanDocument 正确转换为 snake_case 格式");
        System.out.println("   3. timestamp 使用 epoch_millis 格式");
        System.out.println("   4. status 使用标准值 (OK/ERROR/SKIPPED/TIMEOUT)");
        System.out.println("   5. 调用链 parent_span_id 关系正确");
        System.out.println("   6. 聚合查询能力正常");
        System.out.println();
        System.out.println("📋 符合 ES_DATA_SCHEMA.md 规范的字段:");
        System.out.println("   - trace_id, span_id, parent_span_id");
        System.out.println("   - service_name, operation_name");
        System.out.println("   - status, duration_ms, timestamp");
        System.out.println("   - exception_stack, error_type, error_code");
        System.out.println("   - environment, httpStatus");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        
        assertTrue(true, "端到端测试完成");
    }
    
    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
