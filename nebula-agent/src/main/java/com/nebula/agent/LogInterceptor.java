package com.nebula.agent;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import com.nebula.common.MonitoringData;

/**
 * 全链路追踪拦截器
 * 
 * 原理：
 * 1. 在 main 方法执行时，TraceHolder 自动生成一个唯一的 8 位 traceId
 * 2. 该线程内的所有方法调用（queryTicket、payOrder 等）都会沿用同一个 traceId
 * 3. 所有监控数据都会携带这个 traceId 发送到 Server
 * 4. Server 接收后存入 Elasticsearch，形成一条可追踪的链路
 * 5. 最后在 main 方法结束时清理 ThreadLocal，防止内存泄漏
 * 
 * 效果：在 Kibana 中搜索 traceId，可以查看这个请求的完整链路
 */
public class LogInterceptor {
    @RuntimeType
    public static Object intercept(@Origin Method method, 
                                   @SuperCall Callable<?> callable) throws Exception {
        long start = System.currentTimeMillis();
        try {
            // 执行原来的方法
            return callable.call();
        } finally {
            long duration = System.currentTimeMillis() - start;
            
            // 获取当前线程的 Trace ID（如果不存在则自动生成）
            String traceId = TraceHolder.get();
            
            // 获取应用名称，用于区分采样率
            String appName = NebulaAgent.getApplicationName();
            
            // 【新增采样决策】获取该应用的采样率
            float samplingRate = GlobalConfig.getSamplingRate(appName);
            
            // 【新增采样决策】根据采样率决定是否收集数据
            if (ThreadLocalRandom.current().nextDouble() > samplingRate) {
                // 命中降频逻辑，直接跳过数据采集和发送
                return null;
            }
            
            // 创建监控数据对象，包含 traceId
            MonitoringData data = new MonitoringData(
                traceId,  // ✨ 全链路追踪 ID
                method.getName(),
                duration,
                System.currentTimeMillis(),
                "nebula-test-service"
            );
            
            // 标记为已采样
            data.setSampled(true);

            System.out.println("📊 [Agent] 收集到监控数据: " + data);
            
            // 通过 Netty 客户端发送给监控服务端
            NettyClient.send(data);
            
            // 如果是业务入口方法（main），清理 ThreadLocal 防止内存泄漏
            if ("main".equals(method.getName())) {
                TraceHolder.remove();
            }
        }
    }
}

