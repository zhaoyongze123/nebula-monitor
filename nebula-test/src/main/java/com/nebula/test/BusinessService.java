package com.nebula.test;

public class BusinessService {

    public void queryTicket() {
        System.out.println("🚂 正在查询上海到北京的余票...");
        try {
            // 模拟数据库查询耗时 500ms
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void payOrder() {
        System.out.println("💰 正在处理支付请求...");
        try {
            // 模拟支付逻辑耗时 800ms
            Thread.sleep(800);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
