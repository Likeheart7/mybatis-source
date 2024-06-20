package com.chenx.learning.dao;


import com.chenx.learning.dao.provider.CustomerMapperProvider;
import com.chenx.learning.pojo.Customer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

public interface CustomerMapper {
    // 根据Id查询Customer（不查询Address）
    // @*Provider允许指定一个返回字符串的方法，来提供对应的sql
    @SelectProvider(type = CustomerMapperProvider.class, method = "findCustomerById")
    Customer find(@Param("id") long id);

    // 根据Id查询Customer（包含Address）
    Customer findWithAddress(long id);

    // 根据orderId查询Customer
    Customer findByOrderId(long orderId);

    // 持久化Customer对象
    int save(Customer customer);
}
