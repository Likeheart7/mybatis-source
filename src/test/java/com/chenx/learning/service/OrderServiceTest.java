package com.chenx.learning.service;

import com.chenx.learning.pojo.*;
import com.chenx.learning.serivce.CustomerService;
import com.chenx.learning.serivce.OrderService;
import com.chenx.learning.serivce.ProductService;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;

public class OrderServiceTest {
    private OrderService orderService = new OrderService();
    private CustomerService customerService = new CustomerService();
    private ProductService productService = new ProductService();

    @Test
    void testCreateOrder() {
        Customer customer = customerService.find(1);
        List<Address> addressList = customerService.findAllAddress(1);
        Order order = new Order();
        order.setCustomer(customer); // 买家
        order.setDeliveryAddress(addressList.get(0));
//        生成购买条目
        Product product = productService.find(2);
        OrderItem orderItem = new OrderItem();
        orderItem.setAmount(20);
        orderItem.setProduct(product);
        order.setOrderItems(Lists.newArrayList(orderItem));
        long orderId = orderService.createOrder(order);
        System.out.println(orderId);

    }

    @Test
    void testFind() {
        System.out.println(orderService.find(1));
    }


}
