# 🚀 Grafana 监控仪表盘 - 完整集成指南

## 🎯 项目架构升级完成

你的 Nebula-Monitor 现在已升级为**企业级全栈可观测性平台**：

```
         ┌─────────────────────────────────────────────────┐
         │         Nebula-Monitor 监控系统架构             │
         └─────────────────────────────────────────────────┘
                              │
                              ▼
         ┌─────────────────────────────────────────────────┐
         │  JVM Agent (字节码插桩 + Trace ID 生成)         │
         │  • ByteBuddy 动态拦截                           │
         │  • TraceHolder 链路追踪                         │
         └─────────────────────────────────────────────────┘
                              │
                              ▼
         ┌─────────────────────────────────────────────────┐
         │  Netty Server (高性能网络通信)                  │
         │  • 8888 端口监听 Agent 数据                     │
         │  • Redis 连接池 + 重试机制                     │
         └─────────────────────────────────────────────────┘
                   │              │              │
        ┌──────────▼─────┬────────▼──────┬───────▼────────┐
        │                │                │                │
        ▼                ▼                ▼                ▼
    Redis           Elasticsearch      Memory Queue    (Fallback)
 (高速缓冲)         (数据持久化)         (故障转移)
    6379              9200
        │                │
        └────────┬───────┘
                 │
    ┌────────────┴─────────────┐
    │  可视化与分析层          │
    ├─────────────────────────┤
    │ Grafana (3000)          │  ← 实时指标大屏
    │ ├─ 12306 耗时趋势      │
    │ ├─ 方法调用分布图      │
    │ ├─ 性能对比柱状图      │
    │                         │
    │ Kibana (5601)           │  ← 链路追踪详情
    │ ├─ traceId 搜索        │
    │ ├─ 日志详情分析        │
    │                         │
    │ Redis Admin (8001)      │  ← Redis 数据浏览
    │ ├─ Queue 队列监控      │
    └─────────────────────────┘
```

---

## 📊 Grafana 仪表盘配置详解

### 快速访问

```
🔗 Grafana: http://localhost:3000
📝 用户名: admin
🔑 密码: admin123
```

### 预配置的仪表盘面板

#### 1️⃣ **"方法耗时趋势"** 面板（上左）
```
类型: 时间序列图表
数据: Elasticsearch nebula_metrics 索引
指标: duration 的平均值
分组: 按 @timestamp 时间聚合

用途: 实时监控各方法的耗时波动
```

#### 2️⃣ **"各方法调用次数分布"** 面板（上右）
```
类型: 统计数字面板
数据: 计算每个 methodName 的调用次数
排序: 按调用频率从高到低

用途: 快速了解各方法的热度
```

#### 3️⃣ **"平均耗时分布 - 方法对比"** 面板（下左）
```
类型: 饼图
数据: 按 methodName 分组，计算平均耗时比例
颜色: 不同方法不同颜色

用途: 一眼看出性能瓶颈在哪个方法
```

#### 4️⃣ **"方法平均耗时对比"** 面板（下右）
```
类型: 柱状图
数据: 按 methodName 分组，显示平均值、最大值、最小值
排序: 按平均值从高到低

用途: 精确对比各方法的性能差异
```

---

## 🔧 数据源配置

自动配置文件位置：
```
docker/grafana/provisioning/datasources/elasticsearch.yml
```

**配置内容**：
```yaml
datasources:
  - name: Nebula-Elasticsearch
    type: elasticsearch
    url: http://elasticsearch:9200
    timeField: timestamp
    esVersion: 8
```

✅ **已自动配置**，无需手动操作。

---

## 📈 实时数据流流程

1. **Agent 启动**：生成 Trace ID `47e917de`
   ```
   🔍 [Trace] 分配新 ID: 47e917de
   ```

2. **方法执行**：拦截所有调用
   ```
   queryTicket() [506ms]  → traceId=47e917de
   payOrder()    [800ms]  → traceId=47e917de
   main()        [1346ms] → traceId=47e917de
   ```

3. **数据上报**：通过 Netty 发送给 Server
   ```
   📊 Server 收到: MonitoringData{traceId='47e917de', methodName='queryTicket', ...}
   ```

4. **存储三处**：
   - ✅ **Redis**: 高速缓存队列
   - ✅ **Elasticsearch**: 持久化存储
   - ✅ **内存队列**: Fallback 机制

5. **可视化展示**：
   - 📊 **Grafana** 显示实时指标和大屏
   - 🔍 **Kibana** 显示链路详情和日志

---

## 🎮 实际操作指南

### 场景 1: 查看实时性能大屏

1. 打开 http://localhost:3000
2. 看到仪表盘加载 Nebula-Monitor 的数据
3. 实时折线图显示过去 1 小时的方法耗时曲线
4. 饼图和柱状图展示各方法的性能对比

### 场景 2: 调查单次请求详情

1. 打开 Kibana: http://localhost:5601
2. 进入 Discover，选择 `nebula_metrics` 索引
3. 在搜索框输入：`traceId : "47e917de"`
4. 立刻看到这次请求的完整链路：
   ```
   queryTicket (506ms, @23:28:34)
   payOrder    (800ms, @23:28:35)
   main        (1346ms, @23:28:35)
   ```

### 场景 3: 性能诊断

1. 在 Grafana 上看到 `payOrder` 耗时最高（约 800ms）
2. 点击柱状图中的 `payOrder` 数据
3. 深入到具体的执行记录
4. 找到性能瓶颈原因（例如数据库查询慢）

---

## 🏗️ 完整的 Docker Compose 配置

```yaml
services:
  # 核心基础设施
  redis:              # 数据缓冲层
    image: redis:7.0
    ports: 6379
  
  elasticsearch:      # 数据持久化
    image: elasticsearch:8.10.2
    ports: 9200
  
  # 可视化工具
  kibana:            # 链路追踪、日志分析
    image: kibana:8.10.2
    ports: 5601
    depends_on: [elasticsearch]
  
  grafana:           # 实时指标、大屏展示
    image: grafana:latest
    ports: 3000
    depends_on: [elasticsearch]
    volumes:
      - ./docker/grafana/provisioning:/etc/grafana/provisioning
  
  phpRedisAdmin:     # Redis 数据管理
    image: phpredisadmin:latest
    ports: 8001
    depends_on: [redis]
```

---

## 📊 监控关键指标总览

| 指标 | 在哪看 | 用途 |
|------|--------|------|
| **QPS**(请求数/秒) | Grafana 折线图 | 系统吞吐量 |
| **平均耗时** | Grafana 柱状图 | 系统响应速度 |
| **P95/P99** | Kibana 统计分析 | 长尾性能 |
| **错误率** | Kibana 日志搜索 | 系统稳定性 |
| **Trace 链路** | Kibana 搜索 traceId | 故障排查 |
| **Redis 队列深度** | Redis Admin | 缓冲池健康度 |

---

## 🚀 进阶优化方向

### 方案 A: 添加告警

```grafana
1. 在柱状图右击 → Edit
2. 设置 Alert 条件：
   - 如果 payOrder 平均耗时 > 1000ms
   - 发送钉钉/邮件告警
```

### 方案 B: 自定义仪表盘

```
现有 4 个面板只是基础。你可以继续添加：
- Service 级别的 QPS 对比
- Redis 连接池状态监控
- Elasticsearch 写入速度
- 错误日志实时告警
```

### 方案 C: 分布式链路完整性

```java
// 当 service A 调用 service B 时
Request req = new Request("http://service-b:8080/api");
req.addHeader("X-Trace-Id", TraceHolder.get());

// service B 收到请求后
String traceId = request.getHeader("X-Trace-Id");
TraceHolder.set(traceId);  // 继承链路 ID
```

---

## ✅ 快速启动清单

```bash
# 1. 启动所有 Docker 容器
docker-compose up -d

# 2. 启动 Server
java -jar nebula-server/target/nebula-server.jar &

# 3. 启动 TestApp（带 Agent）
java -javaagent:"./nebula-agent/target/nebula-agent-0.0.1-SNAPSHOT.jar" \
  -jar "./nebula-test/target/nebula-test-0.0.1-SNAPSHOT.jar"

# 4. 打开浏览器访问
http://localhost:3000   # Grafana 仪表盘
http://localhost:5601   # Kibana 日志查询
http://localhost:8001   # Redis 管理
```

---

## 🎊 成果总结

你现在拥有：

✅ **字节码插桩** - Agent 零侵入监控
✅ **全链路追踪** - TraceID 关联完整请求
✅ **实时指标** - Grafana 大屏展示
✅ **日志分析** - Kibana 细粒度追踪
✅ **故障转移** - Redis + Elasticsearch 高可用
✅ **可视化管理** - 多维度的数据看板

**这已经是生产级别的可观测性平台！** 🚀

---

## 🔗 各组件访问地址速览

```
Server:             127.0.0.1:8888
Grafana:            http://localhost:3000 (admin/admin123)
Kibana:             http://localhost:5601
Elasticsearch:      http://localhost:9200
Redis:              localhost:6379
Redis Admin:        http://localhost:8001
```

---

*恭喜！你已经构建了一个企业级的分布式监控系统！* 🎉
