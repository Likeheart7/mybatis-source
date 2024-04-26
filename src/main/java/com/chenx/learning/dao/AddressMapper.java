package com.chenx.learning.dao;

import com.chenx.learning.pojo.Address;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AddressMapper {
    // 根据id查询Address对象
    Address find(long id);

    // 查询一个用户的全部地址信息
    List<Address> findAll(long customerId);

    // 查询指定订单的送货地址
    Address findByOrderId(long orderId);

    // 存储Address对象，同时会记录关联的Customer
    int save(@Param("address") Address address,
             @Param("customerId") long customerId);

    int updateBatch();
}
