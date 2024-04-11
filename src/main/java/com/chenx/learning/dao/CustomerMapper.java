package com.chenx.learning.dao;


import com.chenx.learning.pojo.Customer;

public interface CustomerMapper {
    // 根据Id查询Customer（不查询Address）
    Customer find(long id);

    // 根据Id查询Customer（包含Address）
    Customer findWithAddress(long id);
    // 根据orderId查询Customer
    Customer findByOrderId(long orderId);
    // 持久化Customer对象
    int save(Customer customer);
}
