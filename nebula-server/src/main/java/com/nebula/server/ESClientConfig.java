package com.nebula.server;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

/**
 * Elasticsearch 客户端配置类
 * 采用单例模式，确保全局只有一个 ES 连接
 */
public class ESClientConfig {
    private static ElasticsearchClient client;

    public static synchronized ElasticsearchClient getClient() {
        if (client == null) {
            // 创建 RestClient（低级客户端）
            RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http")
            ).build();

            // 创建传输层（JSON 映射）
            ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
            );

            // 创建高级客户端
            client = new ElasticsearchClient(transport);
            System.out.println("✅ Elasticsearch 客户端初始化完成");
        }
        return client;
    }
}
