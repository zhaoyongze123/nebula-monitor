package com.nebula.server;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.MonitoringData;
import com.nebula.server.diagnosis.DiagnosisLogger;
import com.nebula.server.diagnosis.DiagnosisTaskExecutor;
import com.nebula.server.diagnosis.SlowTraceDetector;
import com.nebula.server.diagnosis.TraceContextCollector;
import com.nebula.server.es.ESSpanDocument;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Elasticsearch 同步工作线程
 * 负责从 Redis 队列中取出监控数据，批量写入 Elasticsearch
 * 同时异步触发慢链路诊断任务
 * 
 * 符合 ES_DATA_SCHEMA.md 规范：
 * - 使用 ESSpanDocument 格式化文档
 * - timestamp 使用 epoch_millis 格式
 * - 支持 snake_case 和 camelCase 字段
 */
public class ESSyncWorker implements Runnable {
    private static final Logger logger = Logger.getLogger(ESSyncWorker.class.getName());
    private static final int BATCH_SIZE = 50;  // 批量写入阈值
    private static final long IDLE_SLEEP_MS = 1000;  // 队列空时的休眠时间
    private static final long RETRY_SLEEP_MS = 5000;  // 连接失败时的重试间隔

    private JedisPool jedisPool;
    private final ElasticsearchClient esClient;
    private final ObjectMapper mapper;
    private final String indexName;

    public ESSyncWorker() {
        this.esClient = ESClientConfig.getClient();
        this.mapper = new ObjectMapper();
        this.indexName = ESClientConfig.getIndexName();
        this.jedisPool = initRedisPool();  // 延迟初始化，这样可以重试
    }

    /**
     * 初始化 Redis 连接池，带重试机制
     * 
     * 说明：Server 进程运行在主机上（不在 Docker 容器内），所以需要用 localhost:6379
     *      Redis 容器已通过 docker-compose 将 6379 端口映射到主机
     */
    private JedisPool initRedisPool() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(10);
        config.setMaxIdle(5);
        config.setTestOnBorrow(true);
        config.setMinIdle(2);
        config.setMaxWaitMillis(5000);
        
        // 支持通过环境变量配置 Redis 地址（默认 localhost:6379）
        String redisHost = System.getenv("REDIS_HOST") != null ? 
                          System.getenv("REDIS_HOST") : "localhost";
        int redisPort = System.getenv("REDIS_PORT") != null ? 
                       Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;
        
        try {
            JedisPool pool = new JedisPool(config, redisHost, redisPort, 5000);
            System.out.println("✅ Redis 连接池初始化成功 (host=" + redisHost + 
                             ", port=" + redisPort + ")");
            return pool;
        } catch (Exception e) {
            System.err.println("⚠️  Redis 连接池初始化失败，将在运行时重试: " + e.getMessage());
            System.err.println("   💡 检查方案: docker-compose 是否启动了 nebula-redis?");
            System.err.println("   💡 命令: docker ps | grep nebula-redis");
            return null;
        }
    }

    @Override
    public void run() {
        System.out.println("📦 ES 同步线程已启动 (index=" + indexName + ")...");
        
        // 等待 Redis 就绪
        while (jedisPool == null) {
            System.out.println("⏳ 等待 Redis 就绪，5 秒后重试...");
            try {
                Thread.sleep(RETRY_SLEEP_MS);
                jedisPool = initRedisPool();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        List<MonitoringData> batch = new ArrayList<>();

        while (true) {
            try {
                // 1. 优先从 Redis 取数据（如果可用）
                MonitoringData data = null;
                if (jedisPool != null) {
                    try (Jedis jedis = jedisPool.getResource()) {
                        String json = jedis.rpop("nebula:monitor:queue");
                        if (json != null) {
                            data = mapper.readValue(json, MonitoringData.class);
                        }
                    } catch (Exception e) {
                        // Redis 失败，改用内存队列
                        System.err.println("⚠️  从 Redis 读取失败，使用内存队列: " + e.getMessage());
                        jedisPool = null;  // 标记 Redis 为不可用
                    }
                }
                
                // 2. 如果 Redis 失败，从内存队列取数据
                if (data == null) {
                    data = MonitoringDataQueue.poll();
                }
                
                if (data != null) {
                    batch.add(data);
                    
                    // 🔍 【新增】异步诊断：检查是否需要触发 AI 诊断
                    // 独立异步执行，不阻塞批处理流程
                    if (SlowTraceDetector.shouldDiagnose(data)) {
                        try {
                            DiagnosisTaskExecutor.submitDiagnosisTask(data);
                            DiagnosisLogger.logTaskSubmitted(
                                    data.getTraceId(),
                                    data.getServiceName(),
                                    data.getMethodName(),
                                    data.getDuration()
                            );
                            // 同时存储该链路的指标用于后续对比分析
                            TraceContextCollector.storeTraceMetric(
                                    data.getServiceName(),
                                    data.getMethodName(),
                                    data.getDuration()
                            );
                        } catch (Exception e) {
                            logger.warning("Failed to submit diagnosis task: " + e.getMessage());
                            DiagnosisLogger.logTaskRejected(data.getTraceId(), e.getMessage());
                        }
                    }
                }

                // 3. 批量写入策略：凑够 BATCH_SIZE 条，或者队列空了就写
                if (!batch.isEmpty() && (batch.size() >= BATCH_SIZE || data == null)) {
                    sendToES(batch);
                    batch.clear();
                }

                // 4. 如果没有数据，歇一会儿，防止 CPU 空转
                if (data == null) {
                    Thread.sleep(IDLE_SLEEP_MS);
                }
            } catch (Exception e) {
                System.err.println("❌ ES 同步出错: " + e.getMessage());
                try {
                    Thread.sleep(IDLE_SLEEP_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 批量发送数据到 Elasticsearch
     * 使用 ESSpanDocument 格式化文档，确保符合 ES_DATA_SCHEMA.md 规范
     */
    private void sendToES(List<MonitoringData> dataList) {
        try {
            BulkRequest.Builder br = new BulkRequest.Builder();

            for (MonitoringData data : dataList) {
                // 转换为 ES 文档格式
                ESSpanDocument esDoc = ESSpanDocument.fromMonitoringData(data);
                
                br.operations(op -> op
                    .index(idx -> idx
                        .index(indexName)  // 使用配置的索引名称
                        .id(esDoc.getSpanId())  // 使用 span_id 作为文档 ID
                        .document(esDoc)
                    )
                );
            }

            BulkResponse result = esClient.bulk(br.build());

            if (result.errors()) {
                System.err.println("⚠️  ES 批量写入有错误");
                result.items().forEach(item -> {
                    if (item.error() != null) {
                        System.err.println("  - " + item.error().reason());
                    }
                });
            } else {
                System.out.println("✅ 成功同步 " + dataList.size() + " 条数据至 Elasticsearch (index=" + indexName + ")");
            }
        } catch (Exception e) {
            System.err.println("❌ 发送数据到 ES 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
