package com.nebula.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.nebula.agent.TraceHolder;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * 模拟 Service B
 * 
 * 这是一个简单的 HTTP 服务器，模拟下游服务
 * 用于验证 Trace ID 在跨服务调用中的传播
 * 
 * 工作流程：
 * 1. 接收来自 Service A 的 HTTP 请求
 * 2. 从请求头中读取 X-Nebula-Trace-Id
 * 3. 在实际应用中，ServletInterceptor 会自动从 Header 中提取
 * 4. TraceHolder.set() 将继承的 ID 设置到 ThreadLocal
 * 5. 后续的业务方法都会使用同一个 traceId
 */
public class RemoteServiceServer {
    
    private HttpServer server;
    private static final int PORT = 8081;
    
    /**
     * 启动模拟的 Service B
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
        
        // 注册 /api/query 端点
        server.createContext("/api/query", new QueryHandler());
        server.createContext("/api/process", new ProcessHandler());
        server.createContext("/health", new HealthHandler());
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("🌐 [Service B] 已启动，监听端口 " + PORT);
        System.out.println("   → GET /api/query    - 查询端点");
        System.out.println("   → GET /api/process  - 处理端点");
        System.out.println("   → GET /health       - 健康检查");
    }
    
    /**
     * 停止服务
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("🛑 [Service B] 已停止");
        }
    }
    
    /**
     * 查询端点处理器
     */
    private static class QueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 从请求头中读取 Trace ID（尝试多种 Header 名称）
            String traceId = exchange.getRequestHeaders().getFirst("X-Nebula-Trace-Id");
            
            // 如果没找到，尝试小写版本
            if (traceId == null || traceId.isEmpty()) {
                traceId = exchange.getRequestHeaders().getFirst("x-nebula-trace-id");
            }
            
            System.out.println("📥 [Service B] 收到请求 /api/query");
            System.out.println("   接收到的所有 Headers: " + exchange.getRequestHeaders().keySet());
            
            if (traceId != null && !traceId.isEmpty()) {
                System.out.println("✅ [Service B] 从 Header 中提取 Trace ID: " + traceId);
                System.out.println("   → 设置 TraceHolder，使后续方法将继承此 Trace ID");
                // 关键：在 Service B 中设置继承的 Trace ID
                TraceHolder.set(traceId);
            } else {
                System.out.println("⚠️  [Service B] 未找到 Trace ID Header");
                System.out.println("   → 将生成新的 Trace ID");
                traceId = "SERVICE_B_" + System.nanoTime();
            }
            
            // 模拟处理逻辑（同时记录 Trace ID）
            String response = "{\n" +
                "  \"status\": \"success\",\n" +
                "  \"service\": \"Service B\",\n" +
                "  \"traceId\": \"" + traceId + "\",\n" +
                "  \"endpoint\": \"/api/query\",\n" +
                "  \"message\": \"Query completed\"\n" +
                "}";
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Nebula-Trace-Id", traceId);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            
            System.out.println("   → 响应数据已发送，包含 traceId: " + traceId);
        }
    }
    
    /**
     * 处理端点处理器
     */
    private static class ProcessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 从请求头中读取 Trace ID（尝试多种 Header 名称）
            String traceId = exchange.getRequestHeaders().getFirst("X-Nebula-Trace-Id");
            
            // 如果没找到，尝试小写版本
            if (traceId == null || traceId.isEmpty()) {
                traceId = exchange.getRequestHeaders().getFirst("x-nebula-trace-id");
            }
            
            System.out.println("📥 [Service B] 收到请求 /api/process");
            System.out.println("   接收到的所有 Headers: " + exchange.getRequestHeaders().keySet());
            
            if (traceId != null && !traceId.isEmpty()) {
                System.out.println("✅ [Service B] 继承 Trace ID: " + traceId);
                // 关键：在 Service B 中设置继承的 Trace ID
                TraceHolder.set(traceId);
            } else {
                traceId = "SERVICE_B_" + System.nanoTime();
                System.out.println("⚠️  [Service B] 生成新 Trace ID: " + traceId);
            }
            
            String response = "{\n" +
                "  \"status\": \"success\",\n" +
                "  \"service\": \"Service B\",\n" +
                "  \"traceId\": \"" + traceId + "\",\n" +
                "  \"endpoint\": \"/api/process\",\n" +
                "  \"message\": \"Processing completed\"\n" +
                "}";
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Nebula-Trace-Id", traceId);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    
    /**
     * 健康检查端点
     */
    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "OK";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
