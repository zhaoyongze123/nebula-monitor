package com.nebula.agent;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.Argument;
import java.lang.reflect.Method;

/**
 * Servlet 请求入口拦截器
 * 
 * 用途：在接收 HTTP 请求时，从请求头中提取 Trace ID 并设置到当前线程的 TraceHolder
 * 
 * 原理：
 * 1. 拦截 HttpServletRequest.getHeader() 或 doGet/doPost 等处理方法
 * 2. 检查是否有 X-Nebula-Trace-Id 请求头
 * 3. 如果有，调用 TraceHolder.set() 继承上游的 traceId
 * 4. 如果没有，TraceHolder.get() 会自动生成新的 traceId（本地调用）
 * 
 * 效果：
 * - Service A 的请求链中的 traceId 一路传递到 Service B
 * - 即使跨越多个服务，所有日志都能通过同一个 traceId 查询
 * - 形成完整的分布式链路追踪
 * 
 * 工作流程：
 * 1. HttpServletRequest 到达 Servlet
 * 2. 此拦截器读取 X-Nebula-Trace-Id 请求头
 * 3. 调用 TraceHolder.set(remoteTraceId) 继承上游 ID
 * 4. 后续的业务方法调用 TraceHolder.get() 拿到继承的 ID
 * 5. 所有监控数据都带上这个 ID，发送到 Elasticsearch
 */
public class ServletInterceptor {
    
    private static final String TRACE_HEADER_NAME = "X-Nebula-Trace-Id";
    
    /**
     * 拦截 HttpServletRequest 的 getHeader 方法
     * 在获取请求头时自动提取 Trace ID 并设置到 TraceHolder
     */
    @RuntimeType
    public static String interceptGetHeader(@Argument(0) String headerName,
                                           @Origin Object request) {
        try {
            // 调用原来的 getHeader
            Method method = request.getClass().getMethod("getHeader", String.class);
            String value = (String) method.invoke(request, headerName);
            
            // 如果是 Trace ID 请求头，则设置到 TraceHolder
            if (TRACE_HEADER_NAME.equals(headerName) && value != null) {
                TraceHolder.set(value);
                System.out.println("📥 [Servlet] 从请求头提取 Trace ID: " + value);
                return value;
            }
            
            return value;
        } catch (Exception e) {
            System.err.println("❌ [Servlet] 提取 Trace ID 失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 拦截 doGet/doPost 等 Servlet 处理方法的入口
     * 在处理请求前，主动从请求对象中读取 Trace ID
     */
    @RuntimeType
    public static void interceptDoGet(@Origin Object servletRequest) {
        try {
            // 从 request 对象获取 X-Nebula-Trace-Id 请求头
            Method getHeader = servletRequest.getClass().getMethod("getHeader", String.class);
            String traceId = (String) getHeader.invoke(servletRequest, TRACE_HEADER_NAME);
            
            if (traceId != null && !traceId.isEmpty()) {
                TraceHolder.set(traceId);
                System.out.println("📥 [Servlet] 接收并继承上游 Trace ID: " + traceId);
            } else {
                // 如果没有上游 ID，自动生成新的（本地调用）
                String newId = TraceHolder.get();
                System.out.println("📥 [Servlet] 无上游 Trace ID，生成新的: " + newId);
            }
        } catch (Exception e) {
            System.err.println("❌ [Servlet] 处理 Trace ID 失败: " + e.getMessage());
        }
    }
}
