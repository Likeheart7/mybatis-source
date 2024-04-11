package com.chenx.learning.service;

import com.chenx.learning.pojo.Product;
import com.chenx.learning.serivce.ProductService;
import org.junit.Test;

import java.math.BigDecimal;

public class ProductServiceTest {
    private ProductService productService = new ProductService();

    /**
     * 创建一个商品
     */
    @Test
    public void testCreateProject() {
        Product product = new Product();
        product.setDescription("这是一个华为Mate20 pro");
        product.setName("华为Mate20");
        product.setPrice(new BigDecimal("5999.9"));
        System.out.println("this product id is " + productService.createProduct(product));
    }

    /**
     * 根据商品id查询商品
     */
    @Test
    public void testFind() {
        System.out.println(productService.find(1));
    }

    /**
     * 根据名称查询类似的商品
     */
    @Test
    public void testFindWithName() {
        System.out.println(productService.find("华为"));
    }
}
