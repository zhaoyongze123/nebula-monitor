package com.nebula.agent;

/**
 * 支持链路追踪的 Runnable 包装类
 * 
 * 用途：在线程池执行异步任务时，自动传播 Trace ID 到子线程
 * 
 * 原理：
 * 1. 在主线程中保存当前的 Trace ID
 * 2. 当子线程运行时，先恢复主线程的 Trace ID
 * 3. 子线程内的所有方法都能正确获取 Trace ID
 * 4. 执行完毕后，清理 ThreadLocal 防止泄漏
 * 
 * 工作流程：
 * ┌─ 主线程（HTTP 请求线程）
 * │  traceId = TraceHolder.get() → "5a7d2f1c"
 * │  threadPool.execute(new TraceContextRunnable("5a7d2f1c", task))
 * │
 * └─→ 子线程（线程池线程）
 *    TraceHolder.set("5a7d2f1c")  ← 恢复主线程的 ID
 *    task.run()  ← 业务逻辑，现在有 Trace ID 了
 *    TraceHolder.remove()  ← 清理
 */
public class TraceContextRunnable implements Runnable {
    
    // 从主线程传来的 Trace ID
    private final String traceId;
    
    // 实际要执行的任务
    private final Runnable delegateTask;
    
    // 记录子线程之前的 Trace ID（用于恢复）
    private String previousTraceId;
    
    /**
     * 构造函数：保存主线程的 Trace ID 和要执行的任务
     * 
     * @param traceId 从主线程传来的 Trace ID（可能为 null）
     * @param delegateTask 实际要执行的业务代码
     */
    public TraceContextRunnable(String traceId, Runnable delegateTask) {
        this.traceId = traceId;
        this.delegateTask = delegateTask;
    }
    
    /**
     * 核心：子线程运行时先恢复 Trace ID，然后执行任务
     */
    @Override
    public void run() {
        // 🔑 第1步：在子线程中备份可能存在的旧 Trace ID
        this.previousTraceId = TraceHolder.getCurrentTraceId();
        
        try {
            // 🔑 第2步：设置从主线程传来的 Trace ID
            if (this.traceId != null) {
                TraceHolder.set(this.traceId);
                System.out.println(
                    "📊 [ThreadPool] 子线程继承 Trace ID: " + this.traceId
                    + " (Thread: " + Thread.currentThread().getName() + ")"
                );
            } else {
                // 如果主线程没有 Trace ID（新的请求链路），自动生成一个
                String newId = TraceHolder.get();
                System.out.println(
                    "📊 [ThreadPool] 子线程生成新 Trace ID: " + newId
                    + " (Thread: " + Thread.currentThread().getName() + ")"
                );
            }
            
            // 🔑 第3步：执行真实的业务代码
            // 这里 LogInterceptor 会拦截调用，并获取 TraceHolder 中的 ID
            this.delegateTask.run();
            
        } finally {
            // 🔑 第4步：清理，防止 ThreadLocal 泄漏
            TraceHolder.remove();
            
            // 🔑 第5步：恢复线程池之前的 Trace ID（如果有的话）
            if (this.previousTraceId != null) {
                TraceHolder.set(this.previousTraceId);
            }
        }
    }
}
