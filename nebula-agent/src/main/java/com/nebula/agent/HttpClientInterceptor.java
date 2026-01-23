package com.nebula.agent;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.Argument;
import java.lang.reflect.Method;
import java.net.URLConnection;

/**
 * HTTP 客户端请求拦截器
 * 
 * 用途：在跨进程调用时，将当前线程的 Trace ID 注入到 HTTP 请求头中
 * 
 * 原理：
 * 1. 拦截 URLConnection.setRequestProperty() 方法
 * 2. 在发送请求前，自动将 X-Nebula-Trace-Id 添加到请求头
 * 3. 下游服务接收到请求时，可以从请求头中提取 traceId 并继承
 * 
 * 支持的 HTTP 工具：
 * - java.net.HttpURLConnection
 * - org.apache.http.client.HttpClient (通过反射)
 * - okhttp3.OkHttpClient (通过反射)
 * 
 * 工作流程：
 * Service A: 拦截 HttpClient.execute() -> 注入 X-Nebula-Trace-Id: abc123
 *              |
 *              v (HTTP Request with Header)
 * Service B: 拦截 Servlet.doGet() -> 从 request.getHeader("X-Nebula-Trace-Id") 读取
 *            -> TraceHolder.set("abc123")
 *              |
 *              v (同一个 traceId)
 * Elasticsearch: 两个服务的数据都带同一个 traceId，可以完整追踪
 */
public class HttpClientInterceptor {
    
    private static final String TRACE_HEADER_NAME = "X-Nebula-Trace-Id";
    
    /**
     * 拦截 URLConnection 的 setRequestProperty 方法
     * 在设置 User-Agent 等属性后再注入 Trace ID
     */
    @RuntimeType
    public static void interceptURLConnection(@Argument(0) String key, 
                                             @Argument(1) String value,
                                             @Origin Object target) {
        // 通过反射调用原来的 setRequestProperty
        try {
            Method method = target.getClass().getMethod("setRequestProperty", String.class, String.class);
            method.invoke(target, key, value);
            
            // 如果还没有设置过 X-Nebula-Trace-Id，则添加
            if (!TRACE_HEADER_NAME.equals(key)) {
                String traceId = TraceHolder.get();
                method.invoke(target, TRACE_HEADER_NAME, traceId);
                System.out.println("📤 [HttpClient] 注入 Trace ID 到请求头: " + TRACE_HEADER_NAME + "=" + traceId);
            }
        } catch (Exception e) {
            System.err.println("❌ [HttpClient] 注入 Trace ID 失败: " + e.getMessage());
        }
    }
    
    /**
     * 拦截 HttpURLConnection.getOutputStream() 前，确保已注入 Trace ID
     */
    @RuntimeType
    public static void interceptConnect(@Origin Object target) {
        try {
            String traceId = TraceHolder.get();
            Method setRequestProperty = target.getClass().getMethod("setRequestProperty", String.class, String.class);
            setRequestProperty.invoke(target, TRACE_HEADER_NAME, traceId);
            System.out.println("📤 [HttpClient] 在连接前注入 Trace ID: " + traceId);
        } catch (Exception e) {
            System.err.println("❌ [HttpClient] 连接前注入 Trace ID 失败: " + e.getMessage());
        }
    }
}
