package com.chenx.learning.serivce;


import com.chenx.learning.dao.ProductMapper;
import com.chenx.learning.pojo.Product;
import com.chenx.learning.util.DaoUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.math.BigDecimal;
import java.util.List;

public class ProductService {
    /**
     * 创建商品
     *
     * @param product 商品对象
     * @return 新商品条目的id
     */
    public long createProduct(Product product) {
        Preconditions.checkArgument(product != null, "product is null");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(product.getName()), "product name is empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(product.getDescription()), "product description is empty");
        Preconditions.checkArgument(product.getPrice().compareTo(new BigDecimal("0")) > 0, "product price <= 0 error");
//        创建商品
        return DaoUtils.execute(sqlSession -> {
            ProductMapper mapper = sqlSession.getMapper(ProductMapper.class);
            return mapper.save(product);
        });
    }

    /**
     * 根据productId查询指定商品
     * @param productId 商品id
     * @return 商品对象
     */
    public Product find(long productId) {
        Preconditions.checkArgument(productId > 0, "productId [" + productId + "] is illegal");
        return DaoUtils.execute(sqlSession -> {
            ProductMapper mapper = sqlSession.getMapper(ProductMapper.class);
            return mapper.find(productId);
        });
    }

    /**
     * 根据名称模糊查询商品
     * @param productName 商品名称
     * @return  符合该名称的商品列表
     */
    public List<Product> find(String productName) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(productName), "productName is empty");
        return DaoUtils.execute(sqlSession -> {
            ProductMapper mapper = sqlSession.getMapper(ProductMapper.class);
            return mapper.findByName(productName);
        });
    }
}
