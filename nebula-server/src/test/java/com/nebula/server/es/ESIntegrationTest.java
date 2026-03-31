package com.nebula.server.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.nebula.common.MonitoringData;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Elasticsearch 集成测试
 * 验证数据写入和查询是否符合 ES_DATA_SCHEMA.md 规范
 * 
 * 注意：此测试需要运行 Elasticsearch 实例
 * 使用 docker-compose up elasticsearch 启动
 */
@DisplayName("Elasticsearch 集成测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ESIntegrationTest {
    
    private ElasticsearchClient esClient;
    private String testIndex;
    private boolean esAvailable = false;
    
    // 测试数据
    private final String testTraceId = "test-trace-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<String> createdSpanIds = new ArrayList<>();
    
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
              "cluster": {"type": "keyword"},
              "instanceId": {"type": "keyword"},
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
        // 使用测试专用索引
        testIndex = "nebula_metrics_test_" + System.currentTimeMillis();
        
        try {
            // 直接创建 ES 客户端（不使用 ESClientConfig 以避免单例问题）
            RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http")
            ).build();
            
            esClient = new ElasticsearchClient(
                new RestClientTransport(restClient, new JacksonJsonpMapper())
            );
            
            // 测试连接
            esClient.info();
            esAvailable = true;
            
            // 创建测试索引
            esClient.indices().create(CreateIndexRequest.of(c -> c
                .index(testIndex)
                .withJson(new StringReader(INDEX_MAPPING))
            ));
            
            System.out.println("✅ Elasticsearch 连接成功，测试索引已创建: " + testIndex);
        } catch (Exception e) {
            System.err.println("⚠️  无法连接 Elasticsearch，跳过集成测试: " + e.getMessage());
            System.err.println("   💡 启动 ES: docker-compose up -d elasticsearch");
        }
    }
    
    @AfterAll
    void tearDown() {
        if (esAvailable && esClient != null) {
            try {
                // 删除测试索引
                esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(testIndex)));
                System.out.println("✅ 测试索引已删除: " + testIndex);
            } catch (Exception e) {
                System.err.println("⚠️  删除测试索引失败: " + e.getMessage());
            }
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("应该成功写入符合规范的 span 文档")
    void testWriteSpanDocument() {
        Assumptions.assumeTrue(esAvailable, "跳过：Elasticsearch 不可用");
        
        // Arrange
        MonitoringData data = new MonitoringData();
        data.setTraceId(testTraceId);
        data.setSpanId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        data.setParentSpanId(null);  // root span
        data.setServiceName("gateway-service");
        data.setOperationName("HTTP GET /api/v1/order/confirm");
        data.setStatus("OK");
        data.setDuration(29);
        data.setTimestamp(System.currentTimeMillis());
        data.setSampled(true);
        
        ESSpanDocument esDoc = ESSpanDocument.fromMonitoringData(data);
        createdSpanIds.add(esDoc.getSpanId());
        
        // Act
        assertDoesNotThrow(() -> {
            IndexResponse response = esClient.index(i -> i
                    .index(testIndex)
                    .id(esDoc.getSpanId())
                    .document(esDoc)
            );
            
            // Assert
            assertNotNull(response.id());
            System.out.println("✅ 文档写入成功，ID: " + response.id());
        });
    }
    
    @Test
    @Order(2)
    @DisplayName("应该成功批量写入多个 span 文档")
    void testBulkWriteSpanDocuments() {
        Assumptions.assumeTrue(esAvailable, "跳过：Elasticsearch 不可用");
        
        // Arrange - 创建多个关联的 span
        List<ESSpanDocument> docs = new ArrayList<>();
        
        // Root span
        String rootSpanId = generateSpanId();
        docs.add(ESSpanDocument.builder()
                .traceId(testTraceId)
                .spanId(rootSpanId)
                .parentSpanId(null)
                .serviceName("trace-root")
                .operationName("trace:" + testTraceId)
                .status("OK")
                .durationMs(1128)
                .timestamp(System.currentTimeMillis())
                .sampled(true)
                .build());
        
        // Child span 1
        String childSpanId1 = generateSpanId();
        docs.add(ESSpanDocument.builder()
                .traceId(testTraceId)
                .spanId(childSpanId1)
                .parentSpanId(rootSpanId)
                .serviceName("order-service")
                .operationName("OrderService.createOrder")
                .status("OK")
                .durationMs(500)
                .timestamp(System.currentTimeMillis())
                .environment("prod")
                .region("us-east-1")
                .sampled(true)
                .build());
        
        // Child span 2 (with error)
        String childSpanId2 = generateSpanId();
        docs.add(ESSpanDocument.builder()
                .traceId(testTraceId)
                .spanId(childSpanId2)
                .parentSpanId(rootSpanId)
                .serviceName("payment-service")
                .operationName("PaymentService.processPayment")
                .status("ERROR")
                .durationMs(200)
                .timestamp(System.currentTimeMillis())
                .exceptionStack("java.lang.RuntimeException: Payment failed\n\tat com.example.PaymentService.process(PaymentService.java:42)")
                .errorType("PaymentError")
                .errorCode("PAY_001")
                .httpStatus(500)
                .sampled(true)
                .build());
        
        docs.forEach(d -> createdSpanIds.add(d.getSpanId()));
        
        // Act
        assertDoesNotThrow(() -> {
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (ESSpanDocument doc : docs) {
                br.operations(op -> op
                        .index(idx -> idx
                                .index(testIndex)
                                .id(doc.getSpanId())
                                .document(doc)
                        )
                );
            }
            
            BulkResponse response = esClient.bulk(br.build());
            
            // Assert
            assertFalse(response.errors(), "批量写入不应有错误");
            assertEquals(3, response.items().size());
            System.out.println("✅ 批量写入成功，写入 " + response.items().size() + " 条文档");
        });
        
        // 等待 ES 刷新
        try {
            esClient.indices().refresh(r -> r.index(testIndex));
        } catch (Exception e) {
            // ignore
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("应该能按 trace_id 查询所有关联 span")
    void testQueryByTraceId() throws Exception {
        Assumptions.assumeTrue(esAvailable, "跳过：Elasticsearch 不可用");
        
        // 等待索引刷新
        Thread.sleep(1000);
        esClient.indices().refresh(r -> r.index(testIndex));
        
        // Act
        SearchResponse<ESSpanDocument> response = esClient.search(s -> s
                        .index(testIndex)
                        .query(q -> q
                                .term(t -> t
                                        .field("trace_id")
                                        .value(testTraceId)
                                )
                        )
                        .size(100),
                ESSpanDocument.class
        );
        
        // Assert
        assertTrue(response.hits().total().value() > 0, "应该找到至少一条记录");
        System.out.println("✅ 按 trace_id 查询成功，找到 " + response.hits().total().value() + " 条记录");
        
        // 验证返回的文档字段
        for (Hit<ESSpanDocument> hit : response.hits().hits()) {
            ESSpanDocument doc = hit.source();
            assertNotNull(doc);
            assertEquals(testTraceId, doc.getTraceId());
            assertNotNull(doc.getSpanId());
            assertNotNull(doc.getServiceName());
            assertNotNull(doc.getOperationName());
            assertNotNull(doc.getStatus());
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("应该能按时间范围查询（使用 epoch_millis 格式）")
    void testQueryByTimestampRange() throws Exception {
        Assumptions.assumeTrue(esAvailable, "跳过：Elasticsearch 不可用");
        
        // Arrange
        long fromTs = System.currentTimeMillis() - 60000;  // 1 分钟前
        long toTs = System.currentTimeMillis() + 60000;    // 1 分钟后
        
        // Act
        SearchResponse<ESSpanDocument> response = esClient.search(s -> s
                        .index(testIndex)
                        .query(q -> q
                                .range(r -> r
                                        .field("timestamp")
                                        .gte(co.elastic.clients.json.JsonData.of(fromTs))
                                        .lte(co.elastic.clients.json.JsonData.of(toTs))
                                )
                        )
                        .size(100),
                ESSpanDocument.class
        );
        
        // Assert
        assertTrue(response.hits().total().value() > 0, "应该找到时间范围内的记录");
        System.out.println("✅ 时间范围查询成功 (epoch_millis)，找到 " + response.hits().total().value() + " 条记录");
    }
    
    @Test
    @Order(5)
    @DisplayName("应该能按 status=ERROR 查询错误 span")
    void testQueryErrorSpans() throws Exception {
        Assumptions.assumeTrue(esAvailable, "跳过：Elasticsearch 不可用");
        
        // Act
        SearchResponse<ESSpanDocument> response = esClient.search(s -> s
                        .index(testIndex)
                        .query(q -> q
                                .bool(b -> b
                                        .must(m -> m.term(t -> t.field("trace_id").value(testTraceId)))
                                        .must(m -> m.term(t -> t.field("status").value("ERROR")))
                                )
                        )
                        .size(100),
                ESSpanDocument.class
        );
        
        // Assert
        assertTrue(response.hits().total().value() > 0, "应该找到 ERROR 状态的 span");
        
        for (Hit<ESSpanDocument> hit : response.hits().hits()) {
            ESSpanDocument doc = hit.source();
            assertEquals("ERROR", doc.getStatus());
            assertNotNull(doc.getExceptionStack(), "ERROR span 应该有异常堆栈");
            System.out.println("✅ 错误 span 查询成功: " + doc.getOperationName());
        }
    }
    
    @Test
    @Order(6)
    @DisplayName("应该能按 service_name 聚合统计")
    void testAggregateByServiceName() throws Exception {
        Assumptions.assumeTrue(esAvailable, "跳过：Elasticsearch 不可用");
        
        // Act
        SearchResponse<Void> response = esClient.search(s -> s
                        .index(testIndex)
                        .query(q -> q.term(t -> t.field("trace_id").value(testTraceId)))
                        .size(0)
                        .aggregations("services", a -> a
                                .terms(t -> t.field("service_name"))
                        ),
                Void.class
        );
        
        // Assert
        assertNotNull(response.aggregations());
        assertTrue(response.aggregations().containsKey("services"));
        
        var buckets = response.aggregations().get("services").sterms().buckets().array();
        assertTrue(buckets.size() > 0, "应该有服务聚合结果");
        
        System.out.println("✅ 服务聚合统计成功:");
        for (var bucket : buckets) {
            System.out.println("   - " + bucket.key().stringValue() + ": " + bucket.docCount() + " spans");
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("验证文档字段符合 ES_DATA_SCHEMA 规范")
    @SuppressWarnings("unchecked")
    void testDocumentFormatCompliance() throws Exception {
        Assumptions.assumeTrue(esAvailable, "跳过：Elasticsearch 不可用");
        Assumptions.assumeFalse(createdSpanIds.isEmpty(), "没有创建的测试文档");
        
        // 获取一个测试文档
        GetResponse<Map> response = esClient.get(g -> g
                        .index(testIndex)
                        .id(createdSpanIds.get(0)),
                Map.class
        );
        
        assertTrue(response.found(), "应该能找到测试文档");
        Map<String, Object> source = response.source();
        
        // 验证必需字段存在
        assertTrue(source.containsKey("trace_id"), "应该包含 trace_id 字段");
        assertTrue(source.containsKey("span_id"), "应该包含 span_id 字段");
        assertTrue(source.containsKey("service_name"), "应该包含 service_name 字段");
        assertTrue(source.containsKey("operation_name"), "应该包含 operation_name 字段");
        assertTrue(source.containsKey("status"), "应该包含 status 字段");
        assertTrue(source.containsKey("duration_ms"), "应该包含 duration_ms 字段");
        assertTrue(source.containsKey("timestamp"), "应该包含 timestamp 字段");
        
        // 验证时间戳格式 (epoch_millis)
        Object timestamp = source.get("timestamp");
        assertTrue(timestamp instanceof Number, "timestamp 应该是数字类型");
        long tsValue = ((Number) timestamp).longValue();
        assertTrue(tsValue > 1000000000000L, "timestamp 应该是毫秒级时间戳");
        
        // 验证 status 值
        String status = (String) source.get("status");
        assertTrue(
                status.equals("OK") || status.equals("ERROR") || 
                status.equals("SKIPPED") || status.equals("TIMEOUT"),
                "status 应该是标准值"
        );
        
        System.out.println("✅ 文档格式符合 ES_DATA_SCHEMA 规范");
        System.out.println("   - trace_id: " + source.get("trace_id"));
        System.out.println("   - span_id: " + source.get("span_id"));
        System.out.println("   - timestamp: " + source.get("timestamp") + " (epoch_millis)");
        System.out.println("   - status: " + source.get("status"));
    }
    
    @Test
    @Order(8)
    @DisplayName("验证 camelCase 备选字段也能查询")
    void testCamelCaseFieldsQueryable() throws Exception {
        Assumptions.assumeTrue(esAvailable, "跳过：Elasticsearch 不可用");
        
        // 使用 camelCase 字段名查询
        SearchResponse<ESSpanDocument> response = esClient.search(s -> s
                        .index(testIndex)
                        .query(q -> q.term(t -> t.field("traceId").value(testTraceId)))
                        .size(10),
                ESSpanDocument.class
        );
        
        // 应该也能查到（因为我们同时写入了 snake_case 和 camelCase 字段）
        assertTrue(response.hits().total().value() > 0, "camelCase 字段也应该可查询");
        System.out.println("✅ camelCase 字段查询验证通过");
    }
    
    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
