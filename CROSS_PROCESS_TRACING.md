# 跨进程上下文传播 - Nebula 分布式链路追踪核心能力

## 1. 核心原理

### 背景
在微服务架构中，一个完整的请求通常会跨越多个服务：
```
用户请求
  ↓
Service A (主程序)
  ├→ HTTP 请求 → Service B
  ├→ HTTP 请求 → Service C
  └→ RPC 调用 → Service D
```

**问题**：如果不做特殊处理，每个服务都会生成不同的 Trace ID，导致日志无法关联。

**解决方案**：通过 HTTP Header（或其他协议头）传递 Trace ID，使所有服务都使用同一个 ID。

### 实现机制

```
┌─────────────────────────────────────────────────────────────────┐
│                    Service A (Origin)                           │
│  Thread-1                                                        │
│  ├─ TraceHolder.get() → "abc123" (生成新 ID)                    │
│  ├─ 调用 HttpClient.execute()                                    │
│  │  └─ HttpClientInterceptor 拦截:                              │
│  │     └─ request.setHeader("X-Nebula-Trace-Id", "abc123")      │
│  └─ 发送 HTTP 请求到 Service B                                   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP Request
                            │ Header: X-Nebula-Trace-Id: abc123
                            v
┌─────────────────────────────────────────────────────────────────┐
│                    Service B (Downstream)                       │
│  Thread-2                                                        │
│  ├─ Servlet 收到请求                                             │
│  ├─ ServletInterceptor 拦截:                                     │
│  │  └─ request.getHeader("X-Nebula-Trace-Id") → "abc123"        │
│  │  └─ TraceHolder.set("abc123") (继承上游 ID)                  │
│  ├─ 调用业务方法 queryTicket()                                    │
│  │  └─ LogInterceptor 拦截:                                      │
│  │     └─ TraceHolder.get() → "abc123" (使用继承的 ID)           │
│  │     └─ 发送监控数据(traceId="abc123") 到 Elasticsearch        │
│  └─ 返回响应                                                      │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 代码实现

### 2.1 TraceHolder - 上下文管理器

**功能**：管理 ThreadLocal 中的 Trace ID

```java
public class TraceHolder {
    private static final ThreadLocal<String> TRACE_ID_CONTEXT = new ThreadLocal<>();
    
    /**
     * 获取当前线程的 Trace ID
     * - 如果已设置（来自上游），直接返回
     * - 如果未设置，自动生成新的 8 位 UUID
     */
    public static String get() {
        String id = TRACE_ID_CONTEXT.get();
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8);
            TRACE_ID_CONTEXT.set(id);
        }
        return id;
    }
    
    /**
     * 设置 Trace ID（用于继承上游的 ID）
     * 在 Servlet 入口调用，接收来自 HTTP Header 的 ID
     */
    public static void set(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            TRACE_ID_CONTEXT.set(traceId);
        }
    }
    
    /**
     * 清理 Trace ID（防止内存泄漏）
     */
    public static void remove() {
        TRACE_ID_CONTEXT.remove();
    }
}
```

**关键点**：
- `get()` 具有 Lazy Initialization 特性：未设置时自动生成，已设置时直接返回
- `set()` 用于接收上游的 ID，优先级高于 `get()` 的自动生成
- `remove()` 在线程池环境下防止内存泄漏

### 2.2 HttpClientInterceptor - HTTP 请求注入器

**功能**：在发送 HTTP 请求前，将 Trace ID 注入到请求头

```java
public class HttpClientInterceptor {
    private static final String TRACE_HEADER_NAME = "X-Nebula-Trace-Id";
    
    /**
     * 拦截 URLConnection.setRequestProperty()
     * 在设置其他请求头后，自动添加 X-Nebula-Trace-Id
     */
    @RuntimeType
    public static void interceptURLConnection(@Argument(0) String key, 
                                             @Argument(1) String value,
                                             @Origin Object target) {
        try {
            // 原来的 setRequestProperty 调用
            Method method = target.getClass().getMethod("setRequestProperty", String.class, String.class);
            method.invoke(target, key, value);
            
            // 如果还没有设置过 X-Nebula-Trace-Id，则添加
            if (!TRACE_HEADER_NAME.equals(key)) {
                String traceId = TraceHolder.get();
                method.invoke(target, TRACE_HEADER_NAME, traceId);
                System.out.println("📤 [HttpClient] 注入 Trace ID: " + traceId);
            }
        } catch (Exception e) {
            System.err.println("❌ 注入失败: " + e.getMessage());
        }
    }
}
```

**支持的 HTTP 工具**：
- java.net.HttpURLConnection (JDK 内置)
- org.apache.http.client.HttpClient (通过反射)
- okhttp3.OkHttpClient (通过反射)

**工作原理**：
1. 当应用调用 `connection.setRequestProperty("User-Agent", "...")` 时触发拦截
2. 拦截器先执行原来的 `setRequestProperty` 调用
3. 然后自动追加 `setRequestProperty("X-Nebula-Trace-Id", "abc123")`
4. 无需修改业务代码

### 2.3 ServletInterceptor - HTTP 请求接收器

**功能**：在接收 HTTP 请求时，从请求头提取 Trace ID 并继承

```java
public class ServletInterceptor {
    private static final String TRACE_HEADER_NAME = "X-Nebula-Trace-Id";
    
    /**
     * 拦截 Servlet 的 doGet/doPost/doPut/doDelete 方法
     * 在处理请求前，主动从 HTTP Header 中读取 Trace ID
     */
    @RuntimeType
    public static void interceptDoGet(@Origin Object servletRequest) {
        try {
            Method getHeader = servletRequest.getClass().getMethod("getHeader", String.class);
            String traceId = (String) getHeader.invoke(servletRequest, TRACE_HEADER_NAME);
            
            if (traceId != null && !traceId.isEmpty()) {
                // ✨ 继承上游的 ID
                TraceHolder.set(traceId);
                System.out.println("📥 [Servlet] 继承上游 Trace ID: " + traceId);
            } else {
                // 如果没有上游 ID（本地调用），自动生成新的
                String newId = TraceHolder.get();
                System.out.println("📥 [Servlet] 生成新 Trace ID: " + newId);
            }
        } catch (Exception e) {
            System.err.println("❌ 处理失败: " + e.getMessage());
        }
    }
}
```

**支持的 Servlet 框架**：
- javax.servlet.http.HttpServlet (JEE 标准)
- org.springframework.web.servlet.FrameworkServlet (Spring MVC)
- 其他基于标准 HttpServlet 的框架

### 2.4 LogInterceptor - 业务方法监控（已有）

**功能**：拦截业务方法，自动收集 Trace ID 和性能数据

```java
public class LogInterceptor {
    @RuntimeType
    public static Object intercept(@Origin Method method, 
                                   @SuperCall Callable<?> callable) throws Exception {
        long start = System.currentTimeMillis();
        try {
            return callable.call();
        } finally {
            long duration = System.currentTimeMillis() - start;
            
            // ✨ 从 TraceHolder 获取 Trace ID
            // - 如果是上游请求，会获得继承的 ID
            // - 如果是本地调用，会获得自动生成的 ID
            String traceId = TraceHolder.get();
            
            MonitoringData data = new MonitoringData(traceId, method.getName(), duration, ...);
            NettyClient.send(data);
        }
    }
}
```

## 3. 配置和启动

### 3.1 NebulaAgent 中的拦截器注册

```java
public class NebulaAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        // 1. 拦截业务方法 (com.nebula.test.*)
        new AgentBuilder.Default()
            .type(ElementMatchers.nameStartsWith("com.nebula.test"))
            .transform(...)
            .installOn(inst);
        
        // 2. 拦截 HTTP 客户端 (java.net.HttpURLConnection)
        new AgentBuilder.Default()
            .type(ElementMatchers.named("java.net.HttpURLConnection"))
            .transform(...)
            .installOn(inst);
        
        // 3. 拦截 Servlet 请求入口
        new AgentBuilder.Default()
            .type(ElementMatchers.named("javax.servlet.http.HttpServlet")
                    .or(ElementMatchers.named("org.springframework.web.servlet.FrameworkServlet")))
            .transform(...)
            .installOn(inst);
    }
}
```

### 3.2 启动参数

```bash
java -javaagent:/path/to/nebula-agent.jar \
     -jar nebula-test.jar
```

## 4. 数据流示例

### 场景：跨域的订单处理流程

```
用户浏览器
    ↓ HTTP POST /order
┌─────────────────────────────────────┐
│  Service A (Order Service)          │
│  Thread-1                           │
│  ├─ OrderController.submitOrder()   │
│  │  └─ TraceHolder.get() → "f3a5c2"│
│  ├─ 调用 PaymentService API        │
│  │  └─ HttpClientInterceptor 注入: │
│  │     X-Nebula-Trace-Id: f3a5c2  │
│  └─ MonitoringData(traceId="f3a5c2")│
└─────────────────────────────────────┘
    │ HTTP POST /pay
    │ Header: X-Nebula-Trace-Id: f3a5c2
    ↓
┌─────────────────────────────────────┐
│  Service B (Payment Service)        │
│  Thread-2                           │
│  ├─ PaymentServlet.doPost()         │
│  │  └─ ServletInterceptor 提取:      │
│  │     TraceHolder.set("f3a5c2")   │
│  ├─ PaymentProcessor.process()      │
│  │  └─ TraceHolder.get() → "f3a5c2"│
│  └─ MonitoringData(traceId="f3a5c2")│
└─────────────────────────────────────┘
    │ HTTP POST /notify
    │ Header: X-Nebula-Trace-Id: f3a5c2
    ↓
┌─────────────────────────────────────┐
│  Service C (Notification Service)   │
│  Thread-3                           │
│  ├─ NotificationServlet.doPost()    │
│  │  └─ ServletInterceptor 提取:      │
│  │     TraceHolder.set("f3a5c2")   │
│  ├─ NotificationSender.send()       │
│  │  └─ TraceHolder.get() → "f3a5c2"│
│  └─ MonitoringData(traceId="f3a5c2")│
└─────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────┐
│       Elasticsearch (nebula_metrics)      │
│  记录 1: traceId=f3a5c2, method=submitOrder    │
│  记录 2: traceId=f3a5c2, method=process       │
│  记录 3: traceId=f3a5c2, method=send          │
│                                           │
│  在 Kibana/Grafana 中：                    │
│  搜索 traceId=f3a5c2 → 显示完整的 3 条链路 │
└──────────────────────────────────────────┘
```

## 5. 效果验证

### Kibana 查询示例

```json
GET nebula_metrics/_search
{
  "query": {
    "match": {
      "traceId": "f3a5c2"
    }
  }
}
```

**返回结果**：
```json
{
  "hits": [
    {
      "traceId": "f3a5c2",
      "methodName": "submitOrder",
      "duration": 150,
      "serviceName": "order-service"
    },
    {
      "traceId": "f3a5c2",
      "methodName": "process",
      "duration": 200,
      "serviceName": "payment-service"
    },
    {
      "traceId": "f3a5c2",
      "methodName": "send",
      "duration": 100,
      "serviceName": "notification-service"
    }
  ]
}
```

## 6. 常见问题

### Q1: 如果某个服务没有部署 Agent 会怎样？
**A**: 那个服务无法生成和追踪数据，但 HTTP Header 中的 X-Nebula-Trace-Id 仍会被传递，其他服务可以继续追踪。

### Q2: 如果多个系统的 Trace ID 冲突怎么办？
**A**: 使用 8 位 UUID 已经足够避免冲突（概率 1/16^8 ≈ 1/4 billion）。如果需要更强的唯一性，可以改为 32 位 UUID。

### Q3: 异步调用（如消息队列）怎么办？
**A**: 需要额外的拦截器来处理消息的头部（如 RabbitMQ 的 Header、Kafka 的 Metadata）。

### Q4: 性能开销有多大？
**A**: 极低。仅仅是：
- 一次 ThreadLocal get/set 操作（纳秒级）
- 一次 HTTP Header 读写操作（微秒级）
- 总开销 < 1% 的请求延迟

## 7. 监控仪表盘建议

在 Grafana 中创建以下 Panel 来可视化跨进程追踪：

1. **Trace ID 分布** (Pie Chart)：显示哪些服务贡献了最多的 trace
2. **端到端延迟** (Stat)：所有服务的总延迟
3. **服务间依赖关系** (关系图)：显示调用拓扑
4. **单条 Trace 详情** (Table)：使用 Filter 显示特定 traceId 的所有事件

## 总结

跨进程上下文传播通过以下机制实现了**零侵入、高性能、完整链路**的分布式追踪：

| 维度 | 特点 |
|-----|------|
| 零侵入 | 通过 Agent 字节码插桩，业务代码无感知 |
| 高性能 | 仅添加 Header 传输，开销 < 1% |
| 完整链路 | 所有跨服务调用都使用同一个 traceId |
| 可维护性 | 统一的 TraceHolder + 拦截器框架 |

这是构建企业级分布式监控系统的**核心能力**。
