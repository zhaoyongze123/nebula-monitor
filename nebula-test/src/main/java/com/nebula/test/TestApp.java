package com.nebula.test;

public class TestApp {
    public static void main(String[] args) {
        BusinessService service = new BusinessService();
        
        System.out.println("--- 业务开始执行 ---");
        service.queryTicket();
        service.payOrder();
        System.out.println("--- 业务执行结束 ---");
    }
}
