package com.nebula.agent;

import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池拦截器
 * 
 * 用途：拦截 ThreadPoolExecutor 的 execute() 和 submit() 方法，
 *      自动在提交的任务中注入 Trace ID 传播能力
 * 
 * 工作原理：
 * 1. 拦截 execute(Runnable task) 或 submit() 调用
 * 2. 在主线程中获取当前的 Trace ID
 * 3. 将任务包装成 TraceContextRunnable
 * 4. 提交包装后的任务到线程池
 * 5. 子线程运行包装任务时，自动恢复并继承 Trace ID
 * 
 * 被拦截的方法：
 * - java.util.concurrent.ThreadPoolExecutor.execute(Runnable)
 * - java.util.concurrent.ThreadPoolExecutor.submit(Runnable)
 * - java.util.concurrent.ThreadPoolExecutor.submit(Callable)
 */
public class ThreadPoolInterceptor {
    
    /**
     * 拦截 ThreadPoolExecutor.execute(Runnable) 方法
     * 
     * @param executor 被拦截的 ThreadPoolExecutor 对象
     * @param task 要执行的任务
     * @param zuper 原始方法的调用器
     */
    @RuntimeType
    public static void interceptExecute(
            @This Object executor,              // ← 获取 ThreadPoolExecutor 对象
            @Argument(0) Runnable task,
            @SuperCall Callable<?> zuper) {
        try {
            // 🔑 第1步：在主线程中获取当前的 Trace ID
            String traceId = TraceHolder.getCurrentTraceId();
            
            // 🔑 第2步：如果主线程没有 Trace ID，立即生成一个
            if (traceId == null) {
                traceId = TraceHolder.get();  // 自动生成
            }
            
            // 🔑 第3步：用 Trace ID 包装原始任务
            Runnable wrappedTask = new TraceContextRunnable(traceId, task);
            
            // 🔑 第4步：调用原始的 execute 方法（通过 zuper，传入包装后的任务）
            // 但 zuper 已经绑定了原始参数，所以我们需要直接调用
            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executor;
                threadPool.execute(wrappedTask);
            } else {
                // 降级处理：直接调用（但这会导致任务没有被包装）
                zuper.call();
            }
            
            System.out.println(
                "📊 [ThreadPool] 拦截 execute() - 包装任务，Trace ID: " + traceId
            );
            
        } catch (Exception e) {
            System.err.println("❌ [ThreadPool] 拦截 execute 失败: " + e.getMessage());
            try {
                zuper.call();
            } catch (Exception ignored) {
            }
        }
    }
    
    /**
     * 拦截 ThreadPoolExecutor.submit(Runnable) 方法
     * 
     * @param executor 被拦截的 ThreadPoolExecutor 对象
     * @param task 要执行的任务
     * @param zuper 原始方法的调用器
     */
    @RuntimeType
    public static Object interceptSubmitRunnable(
            @This Object executor,              // ← 获取 ThreadPoolExecutor 对象
            @Argument(0) Runnable task,
            @SuperCall Callable<?> zuper) {
        try {
            // 🔑 第1步：获取当前的 Trace ID
            String traceId = TraceHolder.getCurrentTraceId();
            
            if (traceId == null) {
                traceId = TraceHolder.get();
            }
            
            // 🔑 第2步：包装任务
            Runnable wrappedTask = new TraceContextRunnable(traceId, task);
            
            // 🔑 第3步：直接调用 submit 方法
            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executor;
                Object future = threadPool.submit(wrappedTask);
                
                System.out.println(
                    "📊 [ThreadPool] 拦截 submit(Runnable) - Trace ID: " + traceId
                );
                
                return future;
            } else {
                return zuper.call();
            }
            
        } catch (Exception e) {
            System.err.println("❌ [ThreadPool] 拦截 submit(Runnable) 失败: " + e.getMessage());
            try {
                return zuper.call();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
    
    /**
     * 拦截 ThreadPoolExecutor.submit(Callable) 方法
     * 
     * 注意：Callable 的情况更复杂，因为需要保留返回值
     * 
     * @param executor 被拦截的 ThreadPoolExecutor 对象
     * @param task 要执行的任务
     * @param zuper 原始方法的调用器
     */
    @RuntimeType
    public static Object interceptSubmitCallable(
            @This Object executor,              // ← 获取 ThreadPoolExecutor 对象
            @Argument(0) Callable<?> task,
            @SuperCall Callable<?> zuper) {
        try {
            // 🔑 第1步：获取当前的 Trace ID
            String traceId = TraceHolder.getCurrentTraceId();
            
            if (traceId == null) {
                traceId = TraceHolder.get();
            }
            
            // 🔑 第2步：包装 Callable（需要保留返回值）
            final String finalTraceId = traceId;
            Callable<?> wrappedCallable = new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    // 子线程中恢复 Trace ID
                    String previousId = TraceHolder.getCurrentTraceId();
                    try {
                        if (finalTraceId != null) {
                            TraceHolder.set(finalTraceId);
                            System.out.println(
                                "📊 [ThreadPool] 子线程继承 Trace ID: " + finalTraceId
                                + " (Thread: " + Thread.currentThread().getName() + ")"
                            );
                        } else {
                            String newId = TraceHolder.get();
                            System.out.println(
                                "📊 [ThreadPool] 子线程生成新 Trace ID: " + newId
                            );
                        }
                        
                        // 执行真实的 Callable
                        return task.call();
                    } finally {
                        TraceHolder.remove();
                        if (previousId != null) {
                            TraceHolder.set(previousId);
                        }
                    }
                }
            };
            
            // 🔑 第3步：直接调用 submit 方法
            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executor;
                Object future = threadPool.submit(wrappedCallable);
                
                System.out.println(
                    "📊 [ThreadPool] 拦截 submit(Callable) - Trace ID: " + traceId
                );
                
                return future;
            } else {
                return zuper.call();
            }
            
        } catch (Exception e) {
            System.err.println("❌ [ThreadPool] 拦截 submit(Callable) 失败: " + e.getMessage());
            try {
                return zuper.call();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
