package com.nebula.test;

/**
 * 测试应用主程序
 * 
 * 演示场景：
 * 1. 单服务调用 - queryTicket, payOrder (本地)
 * 2. 跨服务调用 - 调用远程 Service B
 * 
 * 跨进程 Trace 传播验证：
 * - Service A 发起请求时，HttpClientInterceptor 注入 Trace ID Header
 * - Service B 接收请求时，从 Header 中读取 Trace ID
 * - 两个服务的监控数据都带相同的 traceId
 */
public class TestApp {
    
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     Nebula Monitor - 跨服务链路追踪演示                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // 启动模拟的 Service B
        RemoteServiceServer remoteSvc = new RemoteServiceServer();
        try {
            remoteSvc.start();
            System.out.println();
            
            // 等待 Service B 启动完成
            Thread.sleep(1000);
            
            // 执行测试场景
            BusinessService service = new BusinessService();
            
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("场景 1: 单服务内调用（自动生成 Trace ID）");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            service.queryTicket();
            System.out.println();
            
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("场景 2: 单服务内调用（使用相同 Trace ID）");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            service.payOrder();
            System.out.println();
            
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("场景 3: 跨服务调用（Trace ID 跨网络传播）");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            service.remoteQuery();
            System.out.println();
            
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("✅ 所有场景执行完成");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println();
            System.out.println("📊 验证方式：");
            System.out.println("   1. 查看上面的日志，确认 Trace ID 跨越了两个服务");
            System.out.println("   2. 查询 Elasticsearch: curl -s http://localhost:9200/nebula_metrics/_search | jq");
            System.out.println("   3. 在 Kibana (http://localhost:5601) 中搜索相同的 traceId");
            System.out.println("   4. 在 Grafana (http://localhost:3000) 中查看仪表盘");
            System.out.println();
            
        } finally {
            // 关闭 Service B
            remoteSvc.stop();
        }
    }
}
