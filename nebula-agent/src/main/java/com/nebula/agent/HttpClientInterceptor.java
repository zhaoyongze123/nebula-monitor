package com.nebula.agent;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

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
 * 工作流程：
 * Service A: 拦截 HttpClient.setRequestProperty() -> 调用原方法 -> 注入 X-Nebula-Trace-Id: abc123
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
    private static volatile boolean hasSetTraceIdForThisConnection = false;
    
    /**
     * 拦截 setRequestProperty 方法
     * 在调用原方法后，如果还没注入 Trace ID，则自动注入
     */
    @RuntimeType
    public static void interceptSetRequestProperty(
            @Argument(0) String key, 
            @Argument(1) String value,
            @SuperCall Callable<?> zuper,
            @Origin Object target) {
        try {
            // 先调用原方法
            zuper.call();
            
            // 只在不是设置 Trace ID 本身的时候才注入
            if (!TRACE_HEADER_NAME.equals(key)) {
                // 检查是否已经注入过了
                String traceId = TraceHolder.get();
                
                // 通过反射检查是否已设置过 Trace ID Header
                try {
                    Method getRequestProperty = target.getClass().getDeclaredMethod("getRequestProperty", String.class);
                    getRequestProperty.setAccessible(true);
                    Object existingValue = getRequestProperty.invoke(target, TRACE_HEADER_NAME);
                    
                    if (existingValue == null || existingValue.toString().isEmpty()) {
                        // 没有设置过，现在设置
                        Method setRequestProperty = target.getClass().getMethod("setRequestProperty", String.class, String.class);
                        setRequestProperty.invoke(target, TRACE_HEADER_NAME, traceId);
                        System.out.println("📤 [HttpClient] 注入 Trace ID 到请求头: X-Nebula-Trace-Id=" + traceId);
                    }
                } catch (NoSuchMethodException e) {
                    // getRequestProperty 不存在，直接调用 setRequestProperty
                    Method setRequestProperty = target.getClass().getMethod("setRequestProperty", String.class, String.class);
                    setRequestProperty.invoke(target, TRACE_HEADER_NAME, traceId);
                    System.out.println("📤 [HttpClient] 注入 Trace ID 到请求头: X-Nebula-Trace-Id=" + traceId);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [HttpClient] 拦截 setRequestProperty 失败: " + e.getMessage());
            try {
                zuper.call();
            } catch (Exception ignored) {
            }
        }
    }
    
    /**
     * 拦截 connect 方法，在连接前确保已注入 Trace ID
     */
    @RuntimeType
    public static void interceptConnect(@SuperCall Callable<?> zuper, @Origin Object target) {
        try {
            String traceId = TraceHolder.get();
            
            // 在连接前，将 Trace ID 注入到请求头
            try {
                Method setRequestProperty = target.getClass().getMethod("setRequestProperty", String.class, String.class);
                setRequestProperty.invoke(target, TRACE_HEADER_NAME, traceId);
                System.out.println("📤 [HttpClient] 在连接前注入 Trace ID: X-Nebula-Trace-Id=" + traceId);
            } catch (NoSuchMethodException ignored) {
                // 如果没有 setRequestProperty 方法，忽略
            }
            
            // 调用原来的 connect 方法
            zuper.call();
        } catch (Exception e) {
            System.err.println("❌ [HttpClient] 拦截 connect 失败: " + e.getMessage());
            try {
                zuper.call();
            } catch (Exception ignored) {
            }
        }
    }
}
