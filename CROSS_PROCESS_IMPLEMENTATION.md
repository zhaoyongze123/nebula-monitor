# 跨进程 Trace ID 传播实现总结

## 实现清单

### ✅ 已完成的组件

#### 1. TraceHolder（上下文管理器）
- **位置**: `nebula-agent/src/main/java/com/nebula/agent/TraceHolder.java`
- **功能**:
  - `get()`: 获取或生成 Trace ID（8位UUID）
  - `set(traceId)`: 继承上游的 Trace ID（用于微服务调用）
  - `remove()`: 清理 ThreadLocal 防止内存泄漏
- **原理**: ThreadLocal 线程本地存储，支持自动生成和手动设置

#### 2. HttpClientInterceptor（HTTP 请求注入器）
- **位置**: `nebula-agent/src/main/java/com/nebula/agent/HttpClientInterceptor.java`
- **功能**:
  - 拦截 `java.net.HttpURLConnection.setRequestProperty()`
  - 自动注入 `X-Nebula-Trace-Id` 请求头
  - 在 `connect()` 时确保 Header 已设置
- **支持的工具**:
  - java.net.HttpURLConnection (JDK 内置)
  - org.apache.http.client.HttpClient (需反射)
  - okhttp3.OkHttpClient (需反射)

#### 3. ServletInterceptor（HTTP 请求提取器）
- **位置**: `nebula-agent/src/main/java/com/nebula/agent/ServletInterceptor.java`
- **功能**:
  - 拦截 Servlet 的 doGet/doPost/doPut/doDelete 方法
  - 从请求头提取 `X-Nebula-Trace-Id`
  - 调用 `TraceHolder.set()` 继承上游 ID
- **支持的框架**:
  - javax.servlet.http.HttpServlet
  - org.springframework.web.servlet.FrameworkServlet
  - 所有基于标准 Servlet 的框架

#### 4. LogInterceptor（增强）
- **位置**: `nebula-agent/src/main/java/com/nebula/agent/LogInterceptor.java`
- **改进**:
  - 已支持从 TraceHolder 获取（无论是生成还是继承的）Trace ID
  - 将 Trace ID 包含在 MonitoringData 中发送

#### 5. NebulaAgent（字节码植入主控）
- **位置**: `nebula-agent/src/main/java/com/nebula/agent/NebulaAgent.java`
- **改进**:
  - 增加了 HttpURLConnection 拦截配置
  - 增加了 HttpServlet 拦截配置
  - 保留了原有的业务方法拦截配置

## 工作流程

### 单服务内调用（已有能力）
```
main() → queryTicket() → payOrder()
   ↓
TraceHolder.get() 生成 "abc123"
   ↓
所有方法都使用同一个 ID
   ↓
数据发送到 Elasticsearch，traceId="abc123"
```

### 跨服务调用（新增能力）
```
Service A: main() 
  ├─ TraceHolder.get() → "abc123"
  ├─ 调用 HttpClient.request()
  │  └─ HttpClientInterceptor 注入
  │     request.addHeader("X-Nebula-Trace-Id", "abc123")
  └─ 发送请求到 Service B
              │
              v HTTP Request (with Header: X-Nebula-Trace-Id: abc123)
Service B: Servlet.doGet(request)
  ├─ ServletInterceptor 拦截
  │  └─ TraceHolder.set("abc123")  ← 继承上游 ID
  ├─ 业务方法 queryTicket()
  │  └─ TraceHolder.get() → "abc123"  ← 使用继承的 ID
  └─ 发送数据到 Elasticsearch，traceId="abc123"
```

## 数据流完整链路

```
┌──────────────────────────────────────┐
│    User Request (e.g., /order)       │
└──────────────────────────────────────┘
             │
             v
┌──────────────────────────────────────────┐
│  Service A (Servlet Controller)          │
│  ├─ ServletInterceptor 检查 Header       │
│  │  └─ 无 X-Nebula-Trace-Id → 生成新 ID  │
│  ├─ TraceHolder.set("f1a2b3")           │
│  ├─ OrderService.process()              │
│  │  └─ LogInterceptor 拦截              │
│  │     └─ MonitoringData(traceId="f1a2b3")
│  ├─ HttpClient.post(/payment/process)   │
│  │  └─ HttpClientInterceptor 注入        │
│  │     Header: X-Nebula-Trace-Id: f1a2b3│
│  └─ NettyClient.send(data)              │
└──────────────────────────────────────────┘
             │
             │ HTTP POST /payment/process
             │ Header: X-Nebula-Trace-Id: f1a2b3
             v
┌──────────────────────────────────────────┐
│  Service B (Servlet)                    │
│  ├─ ServletInterceptor 读取 Header       │
│  │  └─ getHeader("X-Nebula-Trace-Id")   │
│  ├─ TraceHolder.set("f1a2b3") ← 继承   │
│  ├─ PaymentService.process()            │
│  │  └─ LogInterceptor 拦截              │
│  │     └─ MonitoringData(traceId="f1a2b3")
│  └─ NettyClient.send(data)              │
└──────────────────────────────────────────┘
             │
             v
┌──────────────────────────────────────────┐
│  Elasticsearch (nebula_metrics)          │
│  Record 1: traceId=f1a2b3, OrderService  │
│  Record 2: traceId=f1a2b3, PaymentService│
│                                          │
│  在 Kibana/Grafana 中查询 traceId=f1a2b3 │
│  ✓ 显示完整的跨服务链路                   │
└──────────────────────────────────────────┘
```

## 字节码插桩配置

### AgentBuilder 配置

```java
// 1. 业务方法拦截
.type(ElementMatchers.nameStartsWith("com.nebula.test"))
.transform(LogInterceptor)

// 2. HTTP 客户端拦截
.type(ElementMatchers.named("java.net.HttpURLConnection"))
.transform(HttpClientInterceptor)

// 3. Servlet 入口拦截
.type(ElementMatchers.named("javax.servlet.http.HttpServlet")
        .or(ElementMatchers.named("org.springframework.web.servlet.FrameworkServlet")))
.transform(ServletInterceptor)
```

### 拦截点

| 组件 | 拦截方法 | 目的 |
|-----|---------|------|
| LogInterceptor | 所有方法 | 收集监控数据 |
| HttpClientInterceptor | setRequestProperty, connect | 注入 Trace ID 到请求头 |
| ServletInterceptor | doGet, doPost, doPut, doDelete | 从请求头提取 Trace ID |

## 编译和打包

```bash
# 编译（所有模块）
mvn clean compile -DskipTests

# 打包生成 JAR
mvn clean package -DskipTests

# 生成的 JAR 文件位置
nebula-agent/target/nebula-agent-0.0.1-SNAPSHOT.jar
nebula-server/target/nebula-server.jar
nebula-test/target/nebula-test-0.0.1-SNAPSHOT.jar
```

## 启动命令

### 启动 Elasticsearch、Redis、Grafana 等基础设施
```bash
cd /Users/mac/项目/project/nebula-monitor
docker-compose up -d
```

### 启动监控服务端
```bash
java -jar nebula-server/target/nebula-server.jar
```

### 启动被监控的应用（使用 Agent）
```bash
java -javaagent:nebula-agent/target/nebula-agent-0.0.1-SNAPSHOT.jar \
     -jar nebula-test/target/nebula-test-0.0.1-SNAPSHOT.jar
```

## 验证和测试

### 1. 检查 Elasticsearch 数据

```bash
curl -s http://localhost:9200/nebula_metrics/_search | jq '.hits.hits[] | {_source: ._source}' | head -50
```

预期看到：
```json
{
  "_source": {
    "traceId": "f1a2b3",
    "methodName": "queryTicket",
    "duration": 150,
    "timestamp": 1674534523000,
    "serviceName": "nebula-test-service"
  }
}
```

### 2. 在 Kibana 中查询

访问 http://localhost:5601

搜索某个 traceId，应该能看到多个服务的数据。

### 3. 在 Grafana 中可视化

访问 http://localhost:3000

查看 "Nebula 分布式链路追踪仪表盘"，确认 Trace ID 数据出现。

## 常见问题排查

### 问题1: Header 没有被注入
**症状**: 在下游服务看不到 X-Nebula-Trace-Id Header
**原因**: HttpClientInterceptor 可能没有被加载
**解决**:
1. 检查 AgentBuilder 配置是否包含 HttpURLConnection
2. 查看 Agent 启动日志是否显示 "已安装...HTTP 客户端拦截器"
3. 确认使用的是 java.net.HttpURLConnection（而不是自定义 HTTP 工具）

### 问题2: 下游服务的 Trace ID 不同
**症状**: Service A 的 ID 是 "abc123"，Service B 的数据显示 "xyz789"
**原因**: ServletInterceptor 没有正确提取 Header
**解决**:
1. 检查下游服务是否部署了 Agent
2. 查看日志是否显示 "📥 [Servlet] 继承上游 Trace ID"
3. 确认 Servlet 框架是 HttpServlet 或 Spring FrameworkServlet

### 问题3: ThreadLocal 内存泄漏
**症状**: 长时间运行后内存持续增长
**原因**: TraceHolder.remove() 没有在合适的地方调用
**解决**:
1. LogInterceptor 在 main 方法结束时调用 remove()
2. 对于非 main 的异步任务，需要额外处理（子线程）

## 监控指标

建议在 Grafana 中创建以下指标来验证跨进程追踪：

### 1. Trace ID 唯一性
```sql
SELECT COUNT(DISTINCT traceId) FROM nebula_metrics
# 应该有多个不同的 Trace ID
```

### 2. 单条 Trace 的方法数
```sql
SELECT COUNT(*) as method_count FROM nebula_metrics GROUP BY traceId
# 应该看到多个方法（跨服务）
```

### 3. 各服务的 Trace 占比
```sql
SELECT serviceName, COUNT(*) as count FROM nebula_metrics GROUP BY serviceName
# Service A、Service B、Service C 都应该有数据
```

## 总体架构

```
┌─────────────────────────────────────────────────┐
│         Distributed Application                 │
│  ┌────────────────┐        ┌────────────────┐  │
│  │  Service A     │        │  Service B     │  │
│  │  + Agent       │ ─HTTP→ │  + Agent       │  │
│  │  (Monitor)     │ ←──────│  (Monitor)     │  │
│  └────────────────┘        └────────────────┘  │
│         │                        │              │
│    Trace ID: abc123         Trace ID: abc123   │
│         │                        │              │
│         └────────────┬───────────┘              │
└──────────────────────┼──────────────────────────┘
                       │ (Netty)
                       v
           ┌───────────────────────┐
           │   Nebula Server       │
           │   (Port 8888)         │
           │   - Buffer (Redis)    │
           │   - Persist (ES)      │
           └───────────────────────┘
                    │
        ┌───────────┼───────────┐
        v           v           v
   ┌────────┐  ┌────────┐  ┌──────────┐
   │ Redis  │  │   ES   │  │ Grafana  │
   │ (Cache)│  │ (Data) │  │ (Visual) │
   └────────┘  └────────┘  └──────────┘
```

## 下一步方向

1. **消息队列支持**: 为 RabbitMQ/Kafka 添加拦截器，支持异步调用的 Trace 传播
2. **数据库追踪**: 为 JDBC 添加拦截器，记录数据库查询的 Trace
3. **缓存追踪**: 为 Redis/Memcached 添加拦截器，追踪缓存操作
4. **RPC 框架**: 支持 Dubbo/gRPC 等 RPC 框架的 Trace 传播
5. **链路可视化**: 开发 UI 显示完整的服务调用拓扑和 Trace 详情

## 参考资源

- [OpenTelemetry 标准](https://opentelemetry.io/)
- [Zipkin 分布式追踪](https://zipkin.io/)
- [Jaeger 全链路追踪](https://www.jaegertracing.io/)
- [ByteBuddy 文档](https://bytebuddy.net/)
