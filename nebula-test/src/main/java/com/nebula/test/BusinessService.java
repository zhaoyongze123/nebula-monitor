package com.nebula.test;

import com.nebula.agent.TraceHolder;

/**
 * 业务服务，支持跨服务调用
 * 
 * 场景说明：
 * 1. queryTicket() - 本地操作，traceId 自动继承
 * 2. payOrder() - 包含跨服务调用
 *    → 调用远程支付服务 (Service B)
 *    → HttpClientInterceptor 自动注入 Trace ID Header
 *    → 远程服务 ServletInterceptor 自动提取并继承
 *    → 完整链路：所有服务都使用相同的 traceId
 */
public class BusinessService {

    public void queryTicket() {
        System.out.println("🚂 [Service A] 正在查询上海到北京的余票...");
        try {
            // 模拟数据库查询耗时 500ms
            Thread.sleep(500);
            System.out.println("   → 查询完成，余票: 50 张");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void payOrder() {
        System.out.println("💰 [Service A] 正在处理支付请求...");
        
        try {
            // 第一步：本地操作（单服务）
            System.out.println("   Step 1: 验证订单信息 (本地)");
            Thread.sleep(200);
            System.out.println("   ✓ 订单验证成功");
            
            // 第二步：调用远程支付服务（跨服务）
            System.out.println("   Step 2: 调用远程支付服务 (Service B)");
            String currentTraceId = TraceHolder.get();
            System.out.println("   → 当前 Trace ID: " + currentTraceId);
            
            // 这里会触发 HttpClientInterceptor
            // 自动注入 X-Nebula-Trace-Id: [currentTraceId] 到 HTTP Header
            System.out.println("   → 发起 HTTP 请求到 Service B: http://localhost:8081/api/process");
            String remoteResponse = RemoteServiceClient.callRemoteService(
                "http://localhost:8081", 
                "/api/process"
            );
            
            if (remoteResponse != null) {
                System.out.println("   ✓ 远程支付完成: " + remoteResponse);
            }
            
            // 第三步：完成本地操作
            System.out.println("   Step 3: 完成本地支付流程");
            Thread.sleep(300);
            System.out.println("   ✓ 支付请求处理完成");
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 跨服务查询示例
     */
    public void remoteQuery() {
        System.out.println("🔍 [Service A] 执行远程查询");
        
        String traceId = TraceHolder.get();
        System.out.println("   → 当前 Trace ID: " + traceId);
        
        try {
            // 本地处理
            System.out.println("   Step 1: 准备查询参数 (本地)");
            Thread.sleep(100);
            
            // 跨服务调用
            System.out.println("   Step 2: 调用 Service B 的查询 API");
            String response = RemoteServiceClient.queryRemote(traceId);
            System.out.println("   ✓ 远程查询完成");
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
