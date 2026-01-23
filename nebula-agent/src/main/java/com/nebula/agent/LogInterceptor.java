package com.nebula.agent;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import com.nebula.common.MonitoringData;

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
            // 创建监控数据对象 (DTO)
            MonitoringData data = new MonitoringData(
                method.getName(),
                duration,
                System.currentTimeMillis(),
                "nebula-test-service"
            );

            System.out.println("📊 [Agent] 收集到监控数据: " + data);
            // 通过 Netty 客户端发送给监控服务端
            NettyClient.send(data);
        }
    }
}
