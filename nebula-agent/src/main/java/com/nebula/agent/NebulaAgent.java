package com.nebula.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;

public class NebulaAgent {
    // JVM 启动时会先调这个 premain 方法
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("🚀 Nebula Agent 已启动，准备拦截方法...");
        
        // 从环境变量读取 Server 地址，默认 127.0.0.1:8888（本机）
        String serverHost = System.getenv("NEBULA_SERVER_HOST");
        if (serverHost == null) {
            serverHost = System.getProperty("nebula.server.host", "127.0.0.1");
        }
        
        String serverPortStr = System.getenv("NEBULA_SERVER_PORT");
        if (serverPortStr == null) {
            serverPortStr = System.getProperty("nebula.server.port", "8888");
        }
        int serverPort = Integer.parseInt(serverPortStr);
        
        // 启动时自动建立连接
        NettyClient.connect(serverHost, serverPort);
        System.out.println("📡 Agent 连接服务端: " + serverHost + ":" + serverPort);

        // 1. 拦截业务方法（com.nebula.test 包中的所有方法）
        new AgentBuilder.Default()
            .type(ElementMatchers.nameStartsWith("com.nebula.test"))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public DynamicType.Builder<?> transform(
                        DynamicType.Builder<?> builder,
                        TypeDescription typeDescription,
                        ClassLoader classLoader,
                        JavaModule module) {
                    return builder.method(ElementMatchers.any())
                                  .intercept(MethodDelegation.to(LogInterceptor.class));
                }
            })
            .installOn(inst);
        
        // 2. 拦截 HTTP 客户端（java.net.URLConnection）- 用于注入 Trace ID 到请求头
        try {
            new AgentBuilder.Default()
                .type(ElementMatchers.named("java.net.HttpURLConnection"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(
                            DynamicType.Builder<?> builder,
                            TypeDescription typeDescription,
                            ClassLoader classLoader,
                            JavaModule module) {
                        System.out.println("✓ [HttpClient] 拦截器已成功安装");
                        return builder.method(ElementMatchers.named("setRequestProperty"))
                                      .intercept(MethodDelegation.to(HttpClientInterceptor.class))
                                .method(ElementMatchers.named("connect"))
                                      .intercept(MethodDelegation.to(HttpClientInterceptor.class));
                    }
                })
                .installOn(inst);
        } catch (Exception e) {
            System.err.println("❌ [HttpClient] 拦截器安装失败: " + e.getMessage());
        }
        
        // 3. 拦截 Servlet 请求入口（javax.servlet.http.HttpServlet）- 用于提取上游的 Trace ID
        // 【改进】添加异常处理，处理应用不使用 Servlet 的情况
        try {
            new AgentBuilder.Default()
                .type(ElementMatchers.named("javax.servlet.http.HttpServlet")
                        .or(ElementMatchers.named("org.springframework.web.servlet.FrameworkServlet")))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(
                            DynamicType.Builder<?> builder,
                            TypeDescription typeDescription,
                            ClassLoader classLoader,
                            JavaModule module) {
                        System.out.println("✓ [Servlet] 拦截器已成功安装到: " + typeDescription.getName());
                        return builder.method(ElementMatchers.named("doGet")
                                        .or(ElementMatchers.named("doPost"))
                                        .or(ElementMatchers.named("doPut"))
                                        .or(ElementMatchers.named("doDelete")))
                                      .intercept(MethodDelegation.to(ServletInterceptor.class));
                    }
                })
                .installOn(inst);
        } catch (Exception e) {
            // 类不存在或匹配失败时，说明应用不使用 Servlet，这是正常情况
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                System.out.println("ℹ️ [Servlet] 目标应用不使用 Servlet 框架（可能是纯 Netty/RPC），跳过 Servlet 拦截器");
            } else {
                // 其他异常才是真正的问题
                System.err.println("❌ [Servlet] 拦截器安装失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 4. 🆕 拦截线程池（java.util.concurrent.ThreadPoolExecutor）- 用于传播 Trace ID 到异步任务
        try {
            new AgentBuilder.Default()
                .type(ElementMatchers.named("java.util.concurrent.ThreadPoolExecutor"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(
                            DynamicType.Builder<?> builder,
                            TypeDescription typeDescription,
                            ClassLoader classLoader,
                            JavaModule module) {
                        System.out.println("✓ [ThreadPool] 拦截器已成功安装");
                        return builder
                                // 拦截 execute(Runnable) 方法
                                .method(ElementMatchers.named("execute")
                                        .and(ElementMatchers.takesArguments(1)))
                                .intercept(MethodDelegation.to(ThreadPoolInterceptor.class))
                                
                                // 拦截 submit(Runnable) 方法
                                .method(ElementMatchers.named("submit")
                                        .and(ElementMatchers.takesArgument(0, 
                                            ElementMatchers.named("java.lang.Runnable"))))
                                .intercept(MethodDelegation.to(ThreadPoolInterceptor.class))
                                
                                // 拦截 submit(Callable) 方法
                                .method(ElementMatchers.named("submit")
                                        .and(ElementMatchers.takesArgument(0, 
                                            ElementMatchers.named("java.util.concurrent.Callable"))))
                                .intercept(MethodDelegation.to(ThreadPoolInterceptor.class));
                    }
                })
                .installOn(inst);
        } catch (Exception e) {
            System.err.println("❌ [ThreadPool] 拦截器安装失败: " + e.getMessage());
        }
        
        System.out.println("✅ 已安装业务方法、HTTP 客户端、Servlet、线程池拦截器");
    }
}
