package com.chenx.learning.cache;

import com.chenx.learning.dao.CustomerMapper;
import com.chenx.learning.pojo.Customer;
import com.chenx.learning.util.DaoUtils;
import org.junit.Test;

import java.util.List;

/**
 * 测试缓存相关的用例
 */
public class TestCache {

    /**
     * 测试二级缓存
     * 添加cache标签后，下面代码执行，查看日志，打印命中缓存的信息
     * 两个sqlSession是不同的，这也证明二级缓存是跨SqlSession的
     */
    @Test
    public void testCache() {
        List<Customer> result1 = DaoUtils.execute(sqlSession -> {
            System.err.println(sqlSession);
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            return mapper.findAllCustomer();
        });
        List<Customer> result2 = DaoUtils.execute(sqlSession -> {
            System.err.println(sqlSession);
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            return mapper.findAllCustomer();
        });
        System.out.println(result2);
    }

    /**
     * 测试二级缓存的更新
     * 根据运行结果可以看到，在执行save方法后，再次查询，数据是最新的
     * 关于为什么在save方法执行之后，findAllCustomer方法可以查到新的数据。见{org/apache/ibatis/cache/缓存机制介绍.java}
     */
    @Test
    public void testCacheUpdate() {
        List<Customer> result = DaoUtils.execute(sqlSession -> {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            return mapper.findAllCustomer();
        });
        System.out.println(result);
        // 更新数据库
        int affectRow = DaoUtils.execute(sqlSession -> {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            return mapper.save(new Customer(11L, "likeheart", "110"));
        });
        System.out.println("插入成功，插入了 " + affectRow + " 条数据");
        // 再次查询，查看数据是否发生变化
        List<Customer> resultAfterSave = DaoUtils.execute(sqlSession -> {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            return mapper.findAllCustomer();
        });
        System.out.println(resultAfterSave);
    }
}
