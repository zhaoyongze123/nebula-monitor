# Nebula-Monitor 完整架构和代码流程

## 系统整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Distributed Application                        │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     JVM Process (Service A)                  │  │
│  │  ┌────────────────────────────────────────────────────────┐  │  │
│  │  │              ByteBuddy Agent (premain)                 │  │  │
│  │  │  ┌──────────────────────────────────────────────────┐  │  │  │
│  │  │  │  NebulaAgent.premain()                           │  │  │  │
│  │  │  │  │                                               │  │  │  │
│  │  │  │  ├─ AgentBuilder #1                             │  │  │  │
│  │  │  │  │  type: com.nebula.test.*                     │  │  │  │
│  │  │  │  │  → LogInterceptor.intercept()                │  │  │  │
│  │  │  │  │                                               │  │  │  │
│  │  │  │  ├─ AgentBuilder #2                             │  │  │  │
│  │  │  │  │  type: java.net.HttpURLConnection            │  │  │  │
│  │  │  │  │  → HttpClientInterceptor.intercept()         │  │  │  │
│  │  │  │  │                                               │  │  │  │
│  │  │  │  └─ AgentBuilder #3                             │  │  │  │
│  │  │  │     type: javax.servlet.http.HttpServlet        │  │  │  │
│  │  │  │     → ServletInterceptor.intercept()            │  │  │  │
│  │  │  └──────────────────────────────────────────────────┘  │  │  │
│  │  └────────────────────────────────────────────────────────┘  │  │
│  │                                                             │  │  │
│  │  ┌────────────────────────────────────────────────────────┐  │  │
│  │  │              Application Code Execution                │  │  │
│  │  │                                                         │  │  │
│  │  │  main() ← LogInterceptor.intercept()                  │  │  │
│  │  │   │                                                    │  │  │
│  │  │   ├─ TraceHolder.get() → "abc123"                     │  │  │
│  │  │   │                                                    │  │  │
│  │  │   ├─ queryTicket()                                    │  │  │
│  │  │   │   ├─ LogInterceptor.intercept()                   │  │  │
│  │  │   │   ├─ TraceHolder.get() → "abc123"                │  │  │
│  │  │   │   ├─ 业务逻辑                                      │  │  │
│  │  │   │   └─ MonitoringData(traceId="abc123")             │  │  │
│  │  │   │       └─ NettyClient.send()                       │  │  │
│  │  │   │                                                    │  │  │
│  │  │   ├─ HttpClient.post(/service-b/query)               │  │  │
│  │  │   │   ├─ HttpClientInterceptor.intercept()            │  │  │
│  │  │   │   ├─ request.setHeader(                           │  │  │
│  │  │   │   │     "X-Nebula-Trace-Id",                      │  │  │
│  │  │   │   │     "abc123"  ← 注入                          │  │  │
│  │  │   │   │  )                                             │  │  │
│  │  │   │   └─ 发送 HTTP 请求到 Service B                   │  │  │
│  │  │   │                                                    │  │  │
│  │  │   └─ TraceHolder.remove()                             │  │  │
│  │  └────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
       │
       │ HTTP Request with Header: X-Nebula-Trace-Id: abc123
       │
       v
┌─────────────────────────────────────────────────────────────────────┐
│                     JVM Process (Service B)                         │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              Application Code Execution                      │   │
│  │                                                              │   │
│  │  Servlet.doGet(request) ← ServletInterceptor.intercept()   │   │
│  │   │                                                          │   │
│  │   ├─ request.getHeader("X-Nebula-Trace-Id")                │   │
│  │   │   └─ "abc123"  ← 从 Header 提取                        │   │
│  │   │                                                          │   │
│  │   ├─ TraceHolder.set("abc123")  ← 继承上游 ID             │   │
│  │   │                                                          │   │
│  │   ├─ queryData()                                            │   │
│  │   │   ├─ LogInterceptor.intercept()                         │   │
│  │   │   ├─ TraceHolder.get() → "abc123"  ← 使用继承的 ID   │   │
│  │   │   ├─ 业务逻辑                                           │   │
│  │   │   └─ MonitoringData(traceId="abc123")                  │   │
│  │   │       └─ NettyClient.send()                            │   │
│  │   │                                                          │   │
│  │   └─ HTTP Response                                          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
       │
       │ MonitoringData(traceId="abc123")
       │ MonitoringData(traceId="abc123")
       │
       v (Port 8888, Netty)
┌──────────────────────────────────────────┐
│      Nebula Server (数据处理)             │
│                                           │
│  ├─ NettyServer (Port 8888)              │
│  │  └─ 接收 MonitoringData                │
│  │                                        │
│  ├─ RedisPoolManager (连接池)            │
│  │  ├─ 缓冲数据到 Redis                   │
│  │  └─ 支持重试和 Fallback                │
│  │                                        │
│  └─ Elasticsearch 客户端                  │
│     └─ 持久化到 nebula_metrics 索引       │
└──────────────────────────────────────────┘
       │
       v
┌───────────┬────────────┬──────────────┐
│           │            │              │
v           v            v              v
Redis    Elasticsearch  Kibana       Grafana
(缓冲)    (数据存储)    (查询)       (可视化)
         69 条记录
         {"traceId":"abc123",
          "methodName":"queryTicket",
          "duration":150,
          "timestamp":1674534523000}
```

## 核心数据结构

### MonitoringData (nebula-common)
```java
public class MonitoringData {
    private String traceId;           // ← Trace ID（全链路追踪）
    private String methodName;        // 方法名
    private long duration;            // 执行时间（ms）
    private long timestamp;           // 时间戳
    private String serviceName;       // 服务名称
}
```

### 数据流转链路
```
LogInterceptor.intercept()
  ↓
TraceHolder.get() 获取 Trace ID
  ↓
new MonitoringData(traceId, methodName, duration, timestamp, serviceName)
  ↓
NettyClient.send(data)
  ↓
Server 接收
  ├─ RedisPoolManager.write(data)     → 缓冲
  └─ ElasticsearchClient.index(data)  → 持久化
  ↓
Elasticsearch (nebula_metrics 索引)
  ↓
Kibana 或 Grafana 查询和可视化
```

## 拦截器执行流程

### 1. LogInterceptor（业务方法拦截）

```
目标：com.nebula.test.* 包中的所有方法

执行流程：
┌──────────────────────────────────────────┐
│  intercept(@Origin Method method,        │
│            @SuperCall Callable<?> c)     │
│                                          │
│  long start = System.currentTimeMillis() │
│     │                                    │
│     v                                    │
│  try {                                   │
│    result = callable.call()  ← 执行原方法│
│  } finally {                             │
│    duration = 当前时间 - start            │
│    traceId = TraceHolder.get()           │
│           ├─ 如果已设置 → 返回继承的 ID   │
│           └─ 如果未设置 → 生成新 ID      │
│    MonitoringData data = new ...(...,    │
│                           traceId, ...)  │
│    NettyClient.send(data)                │
│    if ("main".equals(method.getName())) {│
│      TraceHolder.remove()  ← 防止泄漏    │
│    }                                     │
│  }                                       │
└──────────────────────────────────────────┘
```

### 2. HttpClientInterceptor（HTTP 请求注入）

```
目标：java.net.HttpURLConnection.setRequestProperty()

执行流程：
┌──────────────────────────────────────────┐
│  interceptURLConnection(                 │
│    @Argument(0) String key,              │
│    @Argument(1) String value,            │
│    @Origin Object target)                │
│                                          │
│  Method setReqProp = target.getClass()   │
│    .getMethod("setRequestProperty", ...)  │
│     │                                    │
│     v                                    │
│  setReqProp.invoke(target, key, value)   │
│    ← 执行原来的 setRequestProperty       │
│     │                                    │
│     v                                    │
│  if (!TRACE_HEADER_NAME.equals(key)) {   │
│    String traceId = TraceHolder.get()    │
│    ← 获取当前线程的 Trace ID             │
│     │                                    │
│     v                                    │
│    setReqProp.invoke(                    │
│      target,                             │
│      "X-Nebula-Trace-Id",  ← Header 名   │
│      traceId               ← Header 值   │
│    )                                     │
│    println("📤 注入 Trace ID: " + id)     │
│  }                                       │
└──────────────────────────────────────────┘
```

### 3. ServletInterceptor（HTTP 请求提取）

```
目标：javax.servlet.http.HttpServlet.doGet/doPost/...

执行流程：
┌──────────────────────────────────────────┐
│  interceptDoGet(                         │
│    @Origin Object servletRequest)        │
│                                          │
│  Method getHeader = servletRequest       │
│    .getClass()                           │
│    .getMethod("getHeader", String.class) │
│     │                                    │
│     v                                    │
│  String traceId = (String) getHeader     │
│    .invoke(servletRequest,               │
│             "X-Nebula-Trace-Id")         │
│    ← 从请求头提取 Trace ID              │
│     │                                    │
│     v                                    │
│  if (traceId != null && !isEmpty) {      │
│    TraceHolder.set(traceId)              │
│    ← 继承上游的 ID（重要！）              │
│    println("📥 继承 Trace ID: " + id)     │
│  } else {                                │
│    String newId = TraceHolder.get()      │
│    ← 本地调用，生成新 ID                  │
│    println("📥 生成新 Trace ID: " + id)   │
│  }                                       │
└──────────────────────────────────────────┘
```

## TraceHolder 的状态转移

### 场景1：单服务内调用（Service A 独立运行）

```
时间  TraceHolder 状态     操作
────  ─────────────────   ──────────────────
 T0  [空]                main() 开始
 T1  [空] → get()        生成 ID: "abc123"
 T2  ["abc123"]          queryTicket() 开始
 T3  ["abc123"] → get()  返回 "abc123"（已设置）
 T4  ["abc123"]          payOrder() 开始
 T5  ["abc123"] → get()  返回 "abc123"（已设置）
 T6  ["abc123"] → remove() main() 结束，清理
 T7  [空]                线程安全释放
```

### 场景2：跨服务调用（Service A 调用 Service B）

```
时间  Service A             Service B
────  ───────────────────   ───────────────────
 T0  main() → get()         
     id="abc123"            
      │
      v HTTP 请求
      │ Header: X-Nebula-Trace-Id: abc123
      │                                  Servlet.doGet() 开始
      │                                  getHeader("X-Nebula-Trace-Id")
      │                                  → "abc123"
      │                                  set("abc123")
      │
      ├─ id="abc123"         ─────────→ id="abc123"（继承）
      │
      └─ LogInterceptor      ─────────→ LogInterceptor
        MonitoringData         MonitoringData
        (traceId="abc123")    (traceId="abc123")
```

## 字节码植入细节

### 1. 方法匹配规则

```java
// LogInterceptor: 匹配所有 com.nebula.test.* 的方法
ElementMatchers.nameStartsWith("com.nebula.test")
  .and(ElementMatchers.any())  // 任意方法

// HttpClientInterceptor: 匹配 HttpURLConnection 的特定方法
ElementMatchers.named("java.net.HttpURLConnection")
  .and(ElementMatchers.named("setRequestProperty")
    .or(ElementMatchers.named("connect")))

// ServletInterceptor: 匹配 Servlet 的处理方法
ElementMatchers.named("javax.servlet.http.HttpServlet")
  .and(ElementMatchers.named("doGet")
    .or(ElementMatchers.named("doPost"))
    .or(ElementMatchers.named("doPut"))
    .or(ElementMatchers.named("doDelete")))
```

### 2. 注解绑定

```java
public static Object intercept(
    @Origin Method method,           // 当前方法对象
    @Origin Object target,           // 当前对象（this）
    @SuperCall Callable<?> callable, // 原方法调用
    @Argument(0) String arg0,        // 第 0 个参数
    @Argument(1) String arg1         // 第 1 个参数
) {
    // 这些注解会被 ByteBuddy 自动填充
}
```

## 完整调用堆栈示例

```
User Request (HTTP GET /order)
    ↓
Tomcat Servlet Container
    ↓
ServletInterceptor.interceptDoGet()
    ├─ request.getHeader("X-Nebula-Trace-Id")
    ├─ TraceHolder.set("abc123")
    │
    └─ OrderController.submitOrder()
       ├─ LogInterceptor.intercept()
       │  ├─ TraceHolder.get() → "abc123"
       │  ├─ 业务逻辑
       │  └─ MonitoringData(traceId="abc123")
       │      └─ NettyClient.send()
       │
       └─ HttpClient.post("/payment/process")
          ├─ HttpClientInterceptor.interceptURLConnection()
          │  ├─ setRequestProperty(...)  ← 原方法
          │  └─ setRequestProperty("X-Nebula-Trace-Id", "abc123")
          │
          └─ HTTP Request to Service B with Header
             └─ Service B 接收处理...
```

## 性能指标

### 每条请求的开销

| 操作 | 耗时 | 说明 |
|-----|------|------|
| TraceHolder.get() | ~100ns | ThreadLocal 读取 |
| TraceHolder.set() | ~100ns | ThreadLocal 写入 |
| HttpClientInterceptor | ~1μs | 反射调用 + Header 设置 |
| ServletInterceptor | ~1μs | 反射调用 + Header 读取 |
| LogInterceptor | ~10μs | MonitoringData 构造 + NettyClient.send() |
| **总计** | **~15μs** | **< 1% 典型请求延迟** |

## 关键点总结

1. **TraceHolder** 是全局上下文，基于 ThreadLocal
2. **LogInterceptor** 拦截业务方法并收集监控数据
3. **HttpClientInterceptor** 在请求头中注入 Trace ID（出站）
4. **ServletInterceptor** 从请求头中提取 Trace ID（入站）
5. **ByteBuddy** 在 JVM 启动时完成所有字节码植入
6. **NettyClient** 将监控数据发送给 Server
7. **Server** 存储数据到 Redis + Elasticsearch
8. **Kibana/Grafana** 查询和可视化数据

## 下一步扩展方向

- [ ] 支持消息队列（RabbitMQ、Kafka）的 Trace 传播
- [ ] 支持数据库调用追踪（JDBC）
- [ ] 支持 RPC 框架（Dubbo、gRPC）
- [ ] 完整的链路拓扑可视化
- [ ] 异常自动捕获和关联
- [ ] 性能异常自动告警
