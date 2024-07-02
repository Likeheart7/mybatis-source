package com.chenx.learning.dao;


import com.chenx.learning.dao.provider.CustomerMapperProvider;
import com.chenx.learning.pojo.Customer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

public interface CustomerMapper {
    // 根据Id查询Customer（不查询Address）
    // @*Provider允许指定一个返回字符串的方法，来提供对应的sql
    // 使用SelectProvider似乎针对该语句二级缓存不存在
    @SelectProvider(type = CustomerMapperProvider.class, method = "findCustomerById")
    Customer find(@Param("id") long id);

    // 根据Id查询Customer（包含Address）
    Customer findWithAddress(long id);

    // 根据orderId查询Customer
    Customer findByOrderId(long orderId);

    // 持久化Customer对象
    int save(Customer customer);

    // 获取所有Customer
    List<Customer> findAllCustomer();

    // 获取指定customer，用于测试懒加载
    List<Customer> findCustomerLazyLoading(@Param("name") String name);

    /**
     * 获取Customer信息，用于测试整个查询流程，测试代码见{@link com.chenx.learning.exploreprocess.TestProcess#testSelectProcess()}
     */
    Customer selectCustomerWithAddress(@Param("customerId") Long customerId);
}
