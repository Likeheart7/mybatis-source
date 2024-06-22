package com.chenx.learning.service;

import com.chenx.learning.serivce.CustomerService;
import org.junit.Test;

public class CustomerServiceTest {
    CustomerService customerService = new CustomerService();

    /**
     * 注册一个用户
     */
    @Test
    public void testCustomerRegister() {
        long result = customerService.register("chen", "123456");
        System.out.println("this user's id is " + result);
    }

    /**
     * 给id为1的用户添加三个地址
     */
    @Test
    public void testAddAddress() {
        System.out.println(customerService.addAddress(1, "龙泉街道", "安徽", "中国"));
        System.out.println(customerService.addAddress(1, "东山街道", "安徽", "中国"));
    }

    /**
     * 查找用户，包括地址
     */
    @Test
    public void testFindWithAddress() {
        System.out.println(customerService.findWithAddress(1));
    }

    /**
     * 根据用户id查询指定用户
     */
    @Test
    public void testFind() {
        System.out.println(customerService.find(1));
    }

    /**
     * 根据用户id查询对应地址
     */
    @Test
    public void testFindAllAddress() {
        System.out.println(customerService.findAllAddress(1));
    }

    /**
     * 获取全部customer
     */
    @Test
    public void testFindAllCustomer() {
        System.out.println(customerService.findAllCustomer());
    }
}
