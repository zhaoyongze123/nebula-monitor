package com.nebula.server;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.StringReader;

/**
 * Elasticsearch 客户端配置类
 * 采用单例模式，确保全局只有一个 ES 连接
 * 
 * 支持环境变量配置:
 * - ELASTICSEARCH_URL: ES 地址 (默认 http://localhost:9200)
 * - ELASTICSEARCH_INDEX: 索引名称 (默认 nebula_metrics)
 */
public class ESClientConfig {
    
    private static ElasticsearchClient client;
    private static String indexName;
    
    // 默认配置
    private static final String DEFAULT_ES_URL = "http://localhost:9200";
    private static final String DEFAULT_INDEX = "nebula_metrics";
    
    /**
     * 索引映射定义 - 符合 ES_DATA_SCHEMA.md 规范
     * timestamp 使用 epoch_millis 格式
     */
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
              "methodName": {"type": "keyword"},
              "status": {"type": "keyword"},
              "duration_ms": {"type": "long"},
              "durationMs": {"type": "long"},
              "duration": {"type": "long"},
              "timestamp": {
                "type": "date",
                "format": "epoch_millis"
              },
              "exception_stack": {"type": "text"},
              "exceptionStack": {"type": "text"},
              "httpStatus": {"type": "integer"},
              "http_status": {"type": "integer"},
              "error_type": {"type": "keyword"},
              "errorType": {"type": "keyword"},
              "error_code": {"type": "keyword"},
              "errorCode": {"type": "keyword"},
              "environment": {"type": "keyword"},
              "region": {"type": "keyword"},
              "cluster": {"type": "keyword"},
              "instanceId": {"type": "keyword"},
              "instance_id": {"type": "keyword"},
              "sampled": {"type": "boolean"}
            }
          },
          "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "refresh_interval": "5s"
          }
        }
        """;

    /**
     * 获取 ES 客户端实例（单例）
     */
    public static synchronized ElasticsearchClient getClient() {
        if (client == null) {
            initClient();
        }
        return client;
    }
    
    /**
     * 获取索引名称
     */
    public static String getIndexName() {
        if (indexName == null) {
            indexName = getEnvOrDefault("ELASTICSEARCH_INDEX", 
                                       getEnvOrDefault("NEBULA_ES_INDEX", DEFAULT_INDEX));
        }
        return indexName;
    }
    
    /**
     * 初始化 ES 客户端
     */
    private static void initClient() {
        String esUrl = getEnvOrDefault("ELASTICSEARCH_URL", 
                                       getEnvOrDefault("NEBULA_ES_URL", DEFAULT_ES_URL));
        
        try {
            // 解析 URL
            java.net.URL url = new java.net.URL(esUrl);
            String host = url.getHost();
            int port = url.getPort() > 0 ? url.getPort() : 9200;
            String scheme = url.getProtocol();
            
            // 创建 RestClient（低级客户端）
            RestClient restClient = RestClient.builder(
                new HttpHost(host, port, scheme)
            ).build();

            // 创建传输层（JSON 映射）
            ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
            );

            // 创建高级客户端
            client = new ElasticsearchClient(transport);
            System.out.println("✅ Elasticsearch 客户端初始化完成 (endpoint=" + esUrl + ")");
            
            // 确保索引存在
            ensureIndexExists();
            
        } catch (Exception e) {
            System.err.println("❌ Elasticsearch 客户端初始化失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize ES client", e);
        }
    }
    
    /**
     * 确保索引存在，如果不存在则创建
     */
    private static void ensureIndexExists() {
        try {
            String index = getIndexName();
            
            // 检查索引是否存在
            boolean exists = client.indices().exists(
                ExistsRequest.of(e -> e.index(index))
            ).value();
            
            if (!exists) {
                System.out.println("📝 创建索引: " + index);
                
                // 创建索引（带映射）
                client.indices().create(CreateIndexRequest.of(c -> c
                    .index(index)
                    .withJson(new StringReader(INDEX_MAPPING))
                ));
                
                System.out.println("✅ 索引创建成功: " + index);
            } else {
                System.out.println("✅ 索引已存在: " + index);
            }
        } catch (Exception e) {
            System.err.println("⚠️  索引检查/创建失败 (将在写入时自动创建): " + e.getMessage());
        }
    }
    
    /**
     * 从环境变量获取配置，如果不存在则返回默认值
     */
    private static String getEnvOrDefault(String envName, String defaultValue) {
        String value = System.getenv(envName);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * 关闭客户端连接
     */
    public static synchronized void close() {
        if (client != null) {
            try {
                client._transport().close();
                client = null;
                System.out.println("✅ Elasticsearch 客户端已关闭");
            } catch (Exception e) {
                System.err.println("⚠️  关闭 ES 客户端时出错: " + e.getMessage());
            }
        }
    }
}
