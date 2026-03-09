# 快速参考 - Nebula-Monitor 跨进程追踪

## 核心概念速览

| 概念 | 说明 | 示例 |
|-----|------|------|
| **Trace ID** | 唯一标识一条完整的请求链路 | "abc123" |
| **Span** | 一条链路中的单个方法调用 | queryTicket(), payOrder() |
| **TraceHolder** | ThreadLocal 容器，存储当前线程的 Trace ID | TraceHolder.get() |
| **Propagation** | 跨进程传播 Trace ID | HTTP Header: X-Nebula-Trace-Id |

## 4 个核心拦截器

### 1️⃣ LogInterceptor（业务逻辑）
```java
// 拦截目标：com.nebula.test.* 的所有方法
// 工作：
//   ├─ 记录执行时间
//   ├─ 获取 Trace ID（生成或继承）
//   ├─ 构造 MonitoringData
//   └─ 发送给 Server

// 输出日志示例：
// 📊 [Agent] 收集到监控数据: MonitoringData{
//    traceId='abc123', methodName='queryTicket', duration=150, ...
// }
```

### 2️⃣ HttpClientInterceptor（请求出站）
```java
// 拦截目标：java.net.HttpURLConnection.setRequestProperty()
// 工作：
//   ├─ 在发送请求前拦截
//   ├─ 获取当前 Trace ID
//   └─ 注入 Header: X-Nebula-Trace-Id: [id]

// 输出日志示例：
// 📤 [HttpClient] 注入 Trace ID 到请求头: X-Nebula-Trace-Id=abc123
```

### 3️⃣ ServletInterceptor（请求入站）
```java
// 拦截目标：HttpServlet.doGet/doPost/...
// 工作：
//   ├─ 从请求头读取 X-Nebula-Trace-Id
//   ├─ 调用 TraceHolder.set() 继承 ID
//   └─ 后续业务方法使用继承的 ID

// 输出日志示例：
// 📥 [Servlet] 继承上游 Trace ID: abc123
//   或
// 📥 [Servlet] 生成新 Trace ID: xyz789 (无上游 ID)
```

### 4️⃣ TraceHolder（上下文管理）
```java
// ThreadLocal 容器

// 三个主要方法：
TraceHolder.get()          // 获取或生成 Trace ID
TraceHolder.set(id)        // 设置/继承 Trace ID
TraceHolder.remove()       // 清理（防止泄漏）

// 实际代码示例：
String traceId = TraceHolder.get();  // "abc123"
// 在 Servlet 中：
TraceHolder.set(header);             // 继承上游 ID
// 在 main() 结束时：
TraceHolder.remove();                // 清理线程本地数据
```

## 3 种场景的 Trace ID 流转

### 场景1：单服务内调用（无跨服务）
```
main()
  ├─ TraceHolder.get() → 生成 "abc123"
  ├─ queryTicket()
  │   └─ TraceHolder.get() → 使用 "abc123"
  └─ payOrder()
      └─ TraceHolder.get() → 使用 "abc123"

结果：所有方法的监控数据都带 traceId="abc123"
```

### 场景2：跨服务同步调用（HTTP）
```
Service A:
  main() → TraceHolder.get() = "abc123"
    ├─ queryTicket()
    │   └─ MonitoringData(traceId="abc123")
    └─ HttpClient.post("/service-b/api")
       └─ HttpClientInterceptor 注入
          request.setHeader("X-Nebula-Trace-Id", "abc123")

          │
          v HTTP Request with Header

Service B:
  Servlet.doGet(request)
    ├─ ServletInterceptor 提取
    │  request.getHeader("X-Nebula-Trace-Id") = "abc123"
    │  TraceHolder.set("abc123")  ← 继承！
    └─ queryData()
       └─ MonitoringData(traceId="abc123")

结果：两个服务的数据都带 traceId="abc123"，可以关联查询
```

### 场景3：本地调用（无 HTTP Header）
```
Service B 接收到没有 Header 的请求：
  Servlet.doGet(request)
    ├─ ServletInterceptor 提取
    │  request.getHeader("X-Nebula-Trace-Id") = null
    │  TraceHolder.get()  ← 自动生成新 ID
    │  生成 "xyz789"
    └─ queryData()
       └─ MonitoringData(traceId="xyz789")

结果：生成全新的 Trace ID，形成独立链路
```

## 代码片段速查

### 添加自定义拦截器
```java
// 在 NebulaAgent.premain() 中添加

new AgentBuilder.Default()
    .type(ElementMatchers.named("你的.全.类.名"))
    .transform(new AgentBuilder.Transformer() {
        @Override
        public DynamicType.Builder<?> transform(
                DynamicType.Builder<?> builder,
                TypeDescription typeDescription,
                ClassLoader classLoader,
                JavaModule module) {
            return builder
                .method(ElementMatchers.named("方法名"))
                .intercept(MethodDelegation.to(你的Interceptor类.class));
        }
    })
    .installOn(inst);
```

### 在业务代码中手动使用 Trace ID
```java
public void myBusinessMethod() {
    // 获取当前的 Trace ID（可能来自上游继承）
    String traceId = TraceHolder.get();
    
    // 在日志中输出
    logger.info("处理请求 {}", traceId);
    
    // 手动创建监控数据
    MonitoringData data = new MonitoringData(
        traceId,
        "myMethod",
        duration,
        System.currentTimeMillis(),
        "my-service"
    );
    NettyClient.send(data);
}
```

### 调试：打印 Trace 信息
```java
// 在任何需要调试的地方：
String currentTrace = TraceHolder.get();
System.out.println("当前 Trace ID: " + currentTrace);

// 或修改 TraceHolder 中的 println 为：
System.out.println("🔍 [Trace] 分配新 ID: " + id 
    + " (Thread: " + Thread.currentThread().getId() 
    + ", Name: " + Thread.currentThread().getName() + ")");
```

## 常用命令

### 编译打包
```bash
# 仅编译
mvn clean compile -DskipTests

# 完整打包
mvn clean package -DskipTests

# 打包并跳过 checksum 验证
mvn clean package -DskipTests -Dgpg.skip=true
```

### 启动系统
```bash
# 1. 启动基础设施（Docker）
cd /Users/mac/项目/project/nebula-monitor
docker-compose up -d

# 2. 启动 Server
java -jar nebula-server/target/nebula-server.jar &

# 3. 启动应用（带 Agent）
java -javaagent:nebula-agent/target/nebula-agent-0.0.1-SNAPSHOT.jar \
     -jar nebula-test/target/nebula-test-0.0.1-SNAPSHOT.jar
```

### 查询数据
```bash
# Elasticsearch
curl -s http://localhost:9200/nebula_metrics/_search | jq '.'

# 查询特定 traceId
curl -s http://localhost:9200/nebula_metrics/_search -d '{
  "query": { "match": { "traceId": "abc123" } }
}' | jq '.hits.hits[].\_source'

# Redis
redis-cli
> LLEN trace_queue

# Grafana API
curl -s http://localhost:3000/api/dashboards -u admin:admin123 | jq '.'
```

## 故障排查快速表

| 问题 | 排查项 | 解决方案 |
|-----|-------|--------|
| Trace ID 不一致 | 拦截器是否加载 | 查看启动日志的 "✅ 已安装..." 消息 |
| Header 没被注入 | HttpClientInterceptor 是否生效 | 确认使用的是 HttpURLConnection 而非其他 HTTP 工具 |
| 数据没写入 ES | Server 是否运行 | `lsof -i :8888` 检查 Port 8888 |
| Redis 缓冲溢出 | 队列积压 | 检查 Server 的 ES 连接是否正常 |
| 内存泄漏 | TraceHolder.remove() 是否调用 | LogInterceptor 在 main() 结束时必须调用 |

## 模块依赖关系

```
nebula-monitor (Parent POM)
│
├─ nebula-common
│  └─ MonitoringData.java
│
├─ nebula-agent
│  ├─ TraceHolder.java
│  ├─ LogInterceptor.java
│  ├─ HttpClientInterceptor.java (NEW)
│  ├─ ServletInterceptor.java (NEW)
│  ├─ NebulaAgent.java (Updated)
│  ├─ NettyClient.java
│  └─ 依赖: bytbuddy, netty, common
│
├─ nebula-server
│  ├─ NettyServer.java
│  ├─ RedisPoolManager.java
│  ├─ ElasticsearchClient.java
│  └─ 依赖: netty, jedis, elasticsearch
│
└─ nebula-test
   ├─ NebulaTestApplication.java
   ├─ TestController.java
   └─ 依赖: spring-boot, agent
```

## 最重要的 3 个类

### 1. TraceHolder
```java
public static String get() {           // 获取或生成 ID
    String id = TRACE_ID_CONTEXT.get();
    if (id == null) {
        id = UUID.randomUUID().toString().substring(0, 8);
        TRACE_ID_CONTEXT.set(id);
    }
    return id;
}

public static void set(String id) {    // 继承上游 ID
    if (id != null && !id.isEmpty()) {
        TRACE_ID_CONTEXT.set(id);
    }
}
```

### 2. HttpClientInterceptor
```java
// 在发送请求前，自动注入：
request.setHeader("X-Nebula-Trace-Id", TraceHolder.get());
```

### 3. ServletInterceptor
```java
// 在接收请求后，自动提取：
String remoteId = request.getHeader("X-Nebula-Trace-Id");
if (remoteId != null) {
    TraceHolder.set(remoteId);  // ← 关键：继承！
}
```

## 输出日志释义

```
🚀 Nebula Agent 已启动，准备拦截方法...
   → Agent 初始化开始

✅ 已安装业务方法、HTTP 客户端、Servlet 拦截器
   → 所有拦截器已成功注册

🔍 [Trace] 分配新 ID: abc123 (Thread: main)
   → 生成了新的 Trace ID

📤 [HttpClient] 注入 Trace ID 到请求头: X-Nebula-Trace-Id=abc123
   → HTTP 请求已携带 Trace ID

📥 [Servlet] 继承上游 Trace ID: abc123
   → 下游服务成功接收并继承 ID

📊 [Agent] 收集到监控数据: MonitoringData{...}
   → 监控数据已生成

🗑️  [Trace] 清理 ID: abc123
   → Trace ID 已清理，防止内存泄漏
```

## 关键指标

- **Trace ID 长度**: 8 位（UUID 前 8 字符）
- **Header 名称**: X-Nebula-Trace-Id
- **每条方法调用开销**: ~10-20 微秒
- **Elasticsearch 索引**: nebula_metrics
- **Redis 缓冲队列**: trace_queue
- **Server 监听端口**: 8888
- **Grafana 访问**: http://localhost:3000

## 参考文档

- [完整架构文档](./ARCHITECTURE_AND_FLOW.md)
- [跨进程传播原理](./CROSS_PROCESS_TRACING.md)
- [实现细节](./CROSS_PROCESS_IMPLEMENTATION.md)
- [Trace ID 实现指南](./TRACE_ID_IMPLEMENTATION.md)
