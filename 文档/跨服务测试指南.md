# 跨服务调用测试指南

## 🎯 测试目标

验证 Nebula-Monitor 的跨进程 Trace ID 传播功能：
- Service A (主调) 发起 HTTP 请求时，自动注入 Trace ID Header
- Service B (被调) 接收请求时，从 Header 中提取并继承 Trace ID
- 两个服务的监控数据都使用相同的 traceId

## 📋 测试场景

### 场景 1: 单服务内调用
```
Service A: main()
  ├─ queryTicket()  (traceId="abc123")
  └─ payOrder()     (traceId="abc123")

✓ 所有方法使用相同的 Trace ID
✓ 数据存储到 Elasticsearch
```

### 场景 2: 跨服务调用
```
Service A                    Service B
─────────────────────────────────────
main()
  ├─ TraceHolder.get()
  │  = "abc123" (生成)
  │
  ├─ queryTicket()
  │  (traceId="abc123")
  │
  ├─ payOrder()
  │  (traceId="abc123")
  │  ├─ 本地操作
  │  └─ HttpClient.request()
  │     └─ 注入 Header: X-Nebula-Trace-Id: abc123
  │        │
  │        v HTTP Request
  │                          Servlet.doGet()
  │                          → 提取 Header
  │                          → TraceHolder.set("abc123")
  │                          → 处理请求
  │                          → MonitoringData(traceId="abc123")

✓ 链路完整，两个服务的数据都标记为 traceId="abc123"
```

### 场景 3: 多级服务调用
```
Service A → Service B → Service C

traceId: "abc123"
跨越所有三个服务，形成完整的分布式链路
```

## 🚀 启动步骤

### 步骤 1: 启动基础设施

```bash
cd /Users/mac/项目/project/nebula-monitor

# 启动 Docker 容器 (Elasticsearch, Redis, Kibana, Grafana)
docker-compose up -d

# 验证
docker-compose ps
# 应该看到所有容器都是 Up 状态
```

### 步骤 2: 启动 Nebula Server

```bash
# 在终端 1 中
java -jar nebula-server/target/nebula-server.jar

# 期望输出：
# Server 启动成功
# Listening on port 8888
```

### 步骤 3: 启动测试应用（带 Agent）

```bash
# 在终端 2 中
cd /Users/mac/项目/project/nebula-monitor

java -javaagent:nebula-agent/target/nebula-agent-0.0.1-SNAPSHOT.jar \
     -jar nebula-test/target/nebula-test-0.0.1-SNAPSHOT.jar

# 期望输出：
# 🚀 Nebula Agent 已启动，准备拦截方法...
# ✅ 已安装业务方法、HTTP 客户端、Servlet 拦截器
# 
# 场景 1, 2, 3 依次执行
# ✅ 所有场景执行完成
```

## 📊 验证方式

### 方式 1: 查看控制台日志

关键信息包括：

```
🚀 Nebula Agent 已启动，准备拦截方法...
✅ 已安装业务方法、HTTP 客户端、Servlet 拦截器

🔍 [Trace] 分配新 ID: abc123 (Thread: main)

[Service A] 正在查询上海到北京的余票...
   [Trace] 继承上游 ID: abc123

[Service A] 正在处理支付请求...
   📤 [HttpClient] 注入 Trace ID 到请求头: X-Nebula-Trace-Id=abc123

📥 [Service B] 收到请求 /api/query
✅ [Service B] 从 Header 中提取 Trace ID: abc123

📊 [Agent] 收集到监控数据: MonitoringData{traceId='abc123', ...}
```

### 方式 2: 查询 Elasticsearch

```bash
# 查看所有监控数据
curl -s http://localhost:9200/nebula_metrics/_search | jq '.'

# 查询特定 traceId 的所有数据
curl -s http://localhost:9200/nebula_metrics/_search -d '{
  "query": {
    "match": {
      "traceId": "abc123"
    }
  }
}' | jq '.hits.hits[] | ._source'

# 期望看到：
# 多条记录，每条记录都有相同的 traceId="abc123"
# {
#   "traceId": "abc123",
#   "methodName": "queryTicket",
#   "duration": 500,
#   "timestamp": 1674534523000,
#   "serviceName": "nebula-test-service"
# }
# {
#   "traceId": "abc123",
#   "methodName": "payOrder",
#   "duration": 800,
#   "timestamp": 1674534524000,
#   "serviceName": "nebula-test-service"
# }
```

### 方式 3: Kibana 可视化查询

```
1. 打开 http://localhost:5601
2. 创建 Index Pattern: nebula_metrics
3. 在 Discover 中搜索：
   traceId: abc123
4. 应该能看到跨越两个服务的所有数据
```

### 方式 4: Grafana 仪表盘

```
1. 打开 http://localhost:3000
2. 用户名: admin, 密码: admin123
3. 查看 "Nebula 分布式链路追踪仪表盘"
4. 可以看到按 Trace ID 统计的数据
```

## 🔍 详细流程说明

### HTTP Header 传播过程

#### 出站（Service A → Service B）

```
1. Service A.payOrder() 被 LogInterceptor 拦截
   → TraceHolder.get() = "abc123"
   → 生成 MonitoringData(traceId="abc123")
   → NettyClient.send() 发送到 Server

2. Service A 调用 HttpClient.request()
   → HttpClientInterceptor.intercept(setRequestProperty)
   → request.setHeader("User-Agent", "...")  [原调用]
   → request.setHeader("X-Nebula-Trace-Id", "abc123")  [自动注入]

3. HTTP 请求发送
   → Headers: {
       "User-Agent": "...",
       "X-Nebula-Trace-Id": "abc123"  ← 关键！
     }
```

#### 入站（Service B 接收）

```
1. HTTP 请求到达 Service B
   → Headers: {
       "User-Agent": "...",
       "X-Nebula-Trace-Id": "abc123"
     }

2. Service B.Servlet.doGet(request) 被拦截
   → ServletInterceptor.intercept()
   → request.getHeader("X-Nebula-Trace-Id") = "abc123"
   → TraceHolder.set("abc123")  ← 继承！

3. 后续所有方法调用
   → TraceHolder.get() = "abc123"  [返回继承的 ID]
   → MonitoringData(traceId="abc123")
```

## 🧪 关键测试点

| 测试点 | 预期结果 | 验证方法 |
|-------|---------|---------|
| Trace ID 生成 | 8 位 UUID | 查看日志中的 "分配新 ID" |
| Trace ID 继承 | Service A 的 ID 被 Service B 继承 | 查看日志和 Elasticsearch 数据 |
| HTTP Header 注入 | X-Nebula-Trace-Id 出现在请求头 | 查看 Service B 的日志 |
| 数据持久化 | 所有数据都存入 Elasticsearch | curl 查询 nebula_metrics 索引 |
| 链路完整性 | 同一 traceId 的多条记录 | 在 Kibana 中搜索同一 traceId |

## 📈 期望的数据

### Elasticsearch 中的数据示例

```json
{
  "_source": {
    "traceId": "abc123",
    "methodName": "queryTicket",
    "duration": 500,
    "timestamp": 1674534523000,
    "serviceName": "nebula-test-service"
  }
}

{
  "_source": {
    "traceId": "abc123",
    "methodName": "payOrder",
    "duration": 800,
    "timestamp": 1674534524000,
    "serviceName": "nebula-test-service"
  }
}
```

### 同一 Trace ID 的记录数

```
查询：
GET /nebula_metrics/_search
{
  "query": { "match": { "traceId": "abc123" } },
  "size": 100
}

期望：至少 2 条记录（queryTicket, payOrder）
```

## 🛠️ 故障排查

### 问题 1: Trace ID 不同

**症状**: Service B 的 traceId 与 Service A 不同

**排查**:
1. 检查 Service B 的日志是否显示 "从 Header 中提取 Trace ID"
2. 检查 HttpClientInterceptor 是否被加载
3. 确认使用的是 java.net.HttpURLConnection

**解决**:
1. 重新启动应用
2. 查看 Agent 启动日志是否显示 "已安装...HTTP 客户端拦截器"

### 问题 2: 无法连接到 Service B

**症状**: 日志显示无法连接到 localhost:8081

**排查**:
1. Service B 是否已启动
2. 端口 8081 是否被占用
3. 防火墙是否阻止 localhost:8081

**解决**:
1. 确保测试应用已启动（会自动启动 Service B）
2. 检查是否有其他进程占用 8081：`lsof -i :8081`

### 问题 3: Elasticsearch 中没有数据

**症状**: 查询 nebula_metrics 索引返回 0 条结果

**排查**:
1. Server 是否已启动
2. Elasticsearch 是否可访问：`curl http://localhost:9200`
3. 是否有日志错误

**解决**:
1. 检查 Server 的启动日志
2. 确认 Elasticsearch 容器已启动：`docker-compose ps`
3. 重新创建索引：`curl -X PUT "localhost:9200/nebula_metrics"`

## 📝 测试代码位置

```
nebula-test/src/main/java/com/nebula/test/
├── TestApp.java              - 主程序，演示三个场景
├── BusinessService.java      - 业务逻辑，包含跨服务调用
├── RemoteServiceClient.java  - HTTP 客户端，发起跨服务请求
└── RemoteServiceServer.java  - 模拟 Service B，接收请求
```

## 🎓 学习点

通过这个测试，可以验证以下技术点：

1. **ByteBuddy 字节码植入** - 拦截 HttpURLConnection 和 HttpServlet
2. **ThreadLocal 上下文传播** - TraceHolder 管理 Trace ID
3. **HTTP Header 传递** - X-Nebula-Trace-Id 跨进程传播
4. **分布式链路追踪** - 完整的跨服务调用链路
5. **零侵入设计** - 业务代码完全无感知

## 🎉 成功标志

当看到以下日志时，说明实现成功：

```
✅ [Service B] 从 Header 中提取 Trace ID: abc123
✅ [Service B] 继承 Trace ID: abc123
📊 [Agent] 收集到监控数据: MonitoringData{traceId='abc123', ...}
```

然后在 Elasticsearch 查询到相同 traceId 的多条记录。

---

**开始测试**: `java -javaagent:nebula-agent/target/nebula-agent-0.0.1-SNAPSHOT.jar -jar nebula-test/target/nebula-test-0.0.1-SNAPSHOT.jar`
