package com.chenx.learning.dao;


import com.chenx.learning.pojo.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductMapper {
    // 根据id查询商品信息
    Product find(long id);
    // 根据名称搜索商品信息
    List<Product> findByName(@Param("name") String name);
    // 保存商品信息
    long save(Product product);
}
