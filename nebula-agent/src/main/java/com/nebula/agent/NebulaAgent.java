package com.nebula.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class NebulaAgent {
    // JVM 启动时会先调这个 premain 方法
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("🚀 Nebula Agent 已启动，准备拦截方法...");
        
        // 启动时自动建立连接
        NettyClient.connect("127.0.0.1", 8888);

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
        new AgentBuilder.Default()
            .type(ElementMatchers.named("java.net.HttpURLConnection"))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public DynamicType.Builder<?> transform(
                        DynamicType.Builder<?> builder,
                        TypeDescription typeDescription,
                        ClassLoader classLoader,
                        JavaModule module) {
                    return builder.method(ElementMatchers.named("setRequestProperty"))
                                  .intercept(MethodDelegation.to(HttpClientInterceptor.class))
                            .method(ElementMatchers.named("connect"))
                                  .intercept(MethodDelegation.to(HttpClientInterceptor.class));
                }
            })
            .installOn(inst);
        
        // 3. 拦截 Servlet 请求入口（javax.servlet.http.HttpServlet）- 用于提取上游的 Trace ID
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
                    return builder.method(ElementMatchers.named("doGet")
                                    .or(ElementMatchers.named("doPost"))
                                    .or(ElementMatchers.named("doPut"))
                                    .or(ElementMatchers.named("doDelete")))
                                  .intercept(MethodDelegation.to(ServletInterceptor.class));
                }
            })
            .installOn(inst);
        
        System.out.println("✅ 已安装业务方法、HTTP 客户端、Servlet 拦截器");
    }
}
