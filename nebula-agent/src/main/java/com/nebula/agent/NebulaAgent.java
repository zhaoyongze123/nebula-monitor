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

        new AgentBuilder.Default()
            .type(ElementMatchers.nameStartsWith("com.nebula.test"))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public DynamicType.Builder<?> transform(
                        DynamicType.Builder<?> builder,
                        TypeDescription typeDescription,
                        ClassLoader classLoader,
                        JavaModule module,
                        ProtectionDomain protectionDomain) {
                    return builder.method(ElementMatchers.any())
                                  .intercept(MethodDelegation.to(LogInterceptor.class));
                }
            })
            .installOn(inst);
    }
}
