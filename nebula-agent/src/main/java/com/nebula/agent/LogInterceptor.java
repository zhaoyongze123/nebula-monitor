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
            MonitoringData data = new MonitoringData();
            data.setMethodName(method.getName());
            data.setDuration(duration);
            data.setTimestamp(System.currentTimeMillis());
            data.setServiceName("nebula-test-service"); // 暂时硬编码，后面再优化为动态获取

            System.out.println("📊 [Agent] 收集到监控数据: " + data);
        }
    }
}
