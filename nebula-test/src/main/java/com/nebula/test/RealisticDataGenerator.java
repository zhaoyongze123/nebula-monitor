package com.nebula.test;

import com.nebula.agent.TraceHolder;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 真实场景数据生成器
 * 
 * 模拟真实业务场景，生成多样化的trace数据：
 * - 70% 成功链路（快速响应）
 * - 15% 慢查询链路（数据库/缓存延迟）
 * - 10% 超时链路（第三方服务超时）
 * - 5% 错误链路（异常、错误等）
 */
public class RealisticDataGenerator {
    
    private static final Random random = new Random();
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger slowCount = new AtomicInteger(0);
    private static final AtomicInteger timeoutCount = new AtomicInteger(0);
    private static final AtomicInteger errorCount = new AtomicInteger(0);
    
    private final BusinessService businessService;
    private final ExecutorService executorService;
    
    public RealisticDataGenerator() {
        this.businessService = new BusinessService();
        this.executorService = Executors.newFixedThreadPool(10);
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     Nebula Monitor - 真实场景数据生成器                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // 启动远程服务
        RemoteServiceServer remoteSvc = new RemoteServiceServer();
        remoteSvc.start();
        Thread.sleep(2000);
        
        RealisticDataGenerator generator = new RealisticDataGenerator();
        
        try {
            // 生成100个trace，模拟5分钟的真实流量
            int totalTraces = 100;
            System.out.println("📊 开始生成 " + totalTraces + " 个真实trace...");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println();
            
            CountDownLatch latch = new CountDownLatch(totalTraces);
            
            for (int i = 0; i < totalTraces; i++) {
                final int traceNum = i + 1;
                
                // 模拟真实流量：随机间隔（50-500ms）
                Thread.sleep(50 + random.nextInt(450));
                
                generator.executorService.submit(() -> {
                    try {
                        generator.generateRealisticTrace(traceNum);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // 等待所有trace完成
            latch.await(5, TimeUnit.MINUTES);
            
            System.out.println();
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("✅ 数据生成完成");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println();
            System.out.println("📈 统计结果：");
            System.out.println("   ✅ 成功链路: " + successCount.get() + " 条");
            System.out.println("   🐌 慢查询链路: " + slowCount.get() + " 条");
            System.out.println("   ⏱️  超时链路: " + timeoutCount.get() + " 条");
            System.out.println("   ❌ 错误链路: " + errorCount.get() + " 条");
            System.out.println("   📊 总计: " + (successCount.get() + slowCount.get() + timeoutCount.get() + errorCount.get()) + " 条");
            System.out.println();
            
        } finally {
            generator.executorService.shutdown();
            remoteSvc.stop();
        }
    }
    
    /**
     * 生成一个真实的trace
     */
    private void generateRealisticTrace(int traceNum) {
        int scenario = random.nextInt(100);
        
        try {
            if (scenario < 70) {
                // 70% 成功场景
                generateSuccessTrace(traceNum);
                successCount.incrementAndGet();
            } else if (scenario < 85) {
                // 15% 慢查询场景
                generateSlowTrace(traceNum);
                slowCount.incrementAndGet();
            } else if (scenario < 95) {
                // 10% 超时场景
                generateTimeoutTrace(traceNum);
                timeoutCount.incrementAndGet();
            } else {
                // 5% 错误场景
                generateErrorTrace(traceNum);
                errorCount.incrementAndGet();
            }
        } catch (Exception e) {
            // 捕获异常，继续下一个trace
            System.err.println("❌ Trace #" + traceNum + " 生成失败: " + e.getMessage());
        }
    }
    
    /**
     * 场景1: 成功的快速响应链路
     */
    private void generateSuccessTrace(int traceNum) throws InterruptedException {
        // 随机选择业务类型
        int businessType = random.nextInt(3);
        
        switch (businessType) {
            case 0:
                // 查询票务
                businessService.queryTicket();
                break;
            case 1:
                // 支付订单（包含跨服务调用）
                businessService.payOrder();
                break;
            case 2:
                // 远程查询
                businessService.remoteQuery();
                break;
        }
        
        if (traceNum % 10 == 0) {
            System.out.println("✅ [" + traceNum + "] 成功链路完成");
        }
    }
    
    /**
     * 场景2: 慢查询链路（数据库/缓存延迟）
     */
    private void generateSlowTrace(int traceNum) throws InterruptedException {
        System.out.println("🐌 [" + traceNum + "] 模拟慢查询场景");
        
        // 模拟数据库慢查询
        businessService.queryTicket();
        
        // 额外的慢操作
        Thread.sleep(1000 + random.nextInt(2000)); // 1-3秒延迟
        
        System.out.println("   → 慢查询完成 (耗时较长)");
    }
    
    /**
     * 场景3: 超时链路（第三方服务超时）
     */
    private void generateTimeoutTrace(int traceNum) throws InterruptedException {
        System.out.println("⏱️  [" + traceNum + "] 模拟超时场景");
        
        try {
            // 模拟调用超时的第三方服务
            businessService.payOrder();
            
            // 模拟超时等待
            Thread.sleep(5000 + random.nextInt(3000)); // 5-8秒
            
            System.out.println("   → 操作超时");
        } catch (Exception e) {
            System.out.println("   → 捕获超时异常: " + e.getMessage());
        }
    }
    
    /**
     * 场景4: 错误链路（各种异常）
     */
    private void generateErrorTrace(int traceNum) {
        System.out.println("❌ [" + traceNum + "] 模拟错误场景");
        
        int errorType = random.nextInt(5);
        
        try {
            switch (errorType) {
                case 0:
                    // 数据库连接错误
                    simulateDatabaseError();
                    break;
                case 1:
                    // 空指针异常
                    simulateNullPointerError();
                    break;
                case 2:
                    // 业务逻辑错误
                    simulateBusinessError();
                    break;
                case 3:
                    // 网络连接错误
                    simulateNetworkError();
                    break;
                case 4:
                    // 死锁错误
                    simulateDeadlockError();
                    break;
            }
        } catch (Exception e) {
            System.out.println("   → 捕获异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
    
    private void simulateDatabaseError() throws Exception {
        businessService.queryTicket();
        throw new RuntimeException("Connection to database 'order_db' failed: timeout after 30s");
    }
    
    private void simulateNullPointerError() throws Exception {
        businessService.remoteQuery();
        throw new NullPointerException("Cannot invoke method 'getUserId()' on null object");
    }
    
    private void simulateBusinessError() throws Exception {
        businessService.payOrder();
        throw new IllegalStateException("Order amount exceeds user credit limit: 10000 > 5000");
    }
    
    private void simulateNetworkError() throws Exception {
        businessService.remoteQuery();
        throw new Exception("Connection refused: payment-service:8080");
    }
    
    private void simulateDeadlockError() throws Exception {
        businessService.queryTicket();
        throw new RuntimeException("Deadlock found when trying to get lock; try restarting transaction");
    }
}
