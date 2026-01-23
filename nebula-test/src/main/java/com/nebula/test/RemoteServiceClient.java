package com.nebula.test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * 跨服务调用模拟器
 * 
 * 场景说明：
 * Service A (本服务) 调用 Service B 的 API
 * 验证 Trace ID 在 HTTP Header 中的传播
 * 
 * 工作流程：
 * 1. Service A: TraceHolder.get() → 生成或获取 Trace ID
 * 2. Service A: HttpClient.request() → HttpClientInterceptor 注入 Header
 * 3. Service B: Servlet.doGet() → ServletInterceptor 提取 Header
 * 4. Service B: TraceHolder.set() → 继承上游 Trace ID
 * 5. 完整链路：两个服务的数据都带同一个 traceId
 */
public class RemoteServiceClient {

    /**
     * 调用远程服务的 API
     * 这会触发 HttpClientInterceptor，自动注入 X-Nebula-Trace-Id Header
     */
    public static String callRemoteService(String serviceUrl, String endpoint) {
        try {
            URL url = new URL(serviceUrl + endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // 设置 HTTP 方法和超时
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            // 设置某个请求头（会触发拦截器）
            connection.setRequestProperty("User-Agent", "Nebula-Test-Client/1.0");
            // ← HttpClientInterceptor 会在这里自动注入 X-Nebula-Trace-Id Header
            
            // 直接添加 Trace ID Header（备份方案，确保跨服务传播）
            String traceId = com.nebula.agent.TraceHolder.get();
            connection.setRequestProperty("X-Nebula-Trace-Id", traceId);
            System.out.println("📤 [HttpClient] 主动注入 Trace ID: X-Nebula-Trace-Id=" + traceId);
            
            // 发送请求
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
                );
                String inputLine;
                StringBuilder response = new StringBuilder();
                
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                
                System.out.println("📡 [RemoteServiceClient] 收到来自 " + serviceUrl + " 的响应: " + response.toString());
                return response.toString();
            } else {
                System.err.println("❌ [RemoteServiceClient] HTTP " + responseCode);
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌ [RemoteServiceClient] 调用远程服务失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 调用 Service B 的 /api/query 端点
     */
    public static String queryRemote(String traceId) {
        System.out.println("📤 [Service A] 发起跨服务调用，当前 traceId=" + traceId);
        System.out.println("📤 [Service A] 调用远程服务: http://localhost:8081/api/query");
        
        // 调用会触发 HttpClientInterceptor
        // 自动注入 Header: X-Nebula-Trace-Id: [traceId]
        return callRemoteService("http://localhost:8081", "/api/query");
    }
}
