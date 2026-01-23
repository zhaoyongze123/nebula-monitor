package com.nebula.agent;

import java.util.UUID;

/**
 * 全链路 Trace ID 上下文管理器
 * 使用 ThreadLocal 确保同一线程内的所有方法调用共享同一个 Trace ID
 * 
 * 原理：
 * - 每个线程拥有独立的 Trace ID，用于标识一条完整的请求链路
 * - 从入口方法（main）开始生成 ID，在同线程内所有被拦截的方法都会沿用这个 ID
 * - 最后由主线程清理 ThreadLocal 防止内存泄漏
 * 
 * 场景示例：
 * Thread-1: main() → queryTicket() → payOrder()  [都带同一个 traceId]
 * Thread-2: main() → queryTicket() → payOrder()  [都带另一个 traceId]
 */
public class TraceHolder {
    
    private static final ThreadLocal<String> TRACE_ID_CONTEXT = new ThreadLocal<>();

    /**
     * 获取当前线程的 Trace ID
     * 如果不存在则自动生成一个新的 8 字符短 UUID
     */
    public static String get() {
        String id = TRACE_ID_CONTEXT.get();
        if (id == null) {
            // 生成 8 位短 UUID 作为身份证号
            // UUID: 123e4567-e89b-12d3-a456-426614174000
            // 截断前 8 位: 123e4567
            id = UUID.randomUUID().toString().substring(0, 8);
            TRACE_ID_CONTEXT.set(id);
            System.out.println("🔍 [Trace] 分配新 ID: " + id + " (Thread: " + Thread.currentThread().getName() + ")");
        }
        return id;
    }

    /**
     * 清理当前线程的 Trace ID
     * 必须在 main 方法结束时调用，防止线程池环境下的内存泄漏
     */
    public static void remove() {
        String id = TRACE_ID_CONTEXT.get();
        if (id != null) {
            System.out.println("🗑️  [Trace] 清理 ID: " + id);
            TRACE_ID_CONTEXT.remove();
        }
    }

    /**
     * 直接设置 Trace ID（用于分布式调用时接收上游的 ID）
     * 在微服务场景中，可以从 HTTP Header 中提取上游的 traceId
     */
    public static void set(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            TRACE_ID_CONTEXT.set(traceId);
            System.out.println("📤 [Trace] 继承上游 ID: " + traceId);
        }
    }

    /**
     * 获取或创建 Trace ID（和 get() 一样，但语义更明确）
     */
    public static String getOrCreate() {
        return get();
    }
}
