package com.chenx.learning.exploreprocess;

import com.chenx.learning.dao.CustomerMapper;
import com.chenx.learning.pojo.Customer;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

public class TestProcess {
    /**
     * 测试查询全流程的单元测试
     */
    @Test
    public void testSelectProcess() {
        String resource = "mybatis-config-sample.xml";
        try (
                InputStream configResource = Resources.getResourceAsStream(resource);
                SqlSession sqlSession = new SqlSessionFactoryBuilder().build(configResource).openSession();
        ) {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            // 查询两次，测试缓存，加了<setting name="cacheEnabled" value="false"/>所有二级缓存不生效
            // 首先，使用同一个sqlSession执行两次查询，再未开启二级缓存的情况下，也只会查询一次数据库，因为默认一级缓存是SqlSession级别的。就是BaseExecutor下的localCache属性
            Customer customer = mapper.selectCustomerWithAddress(1L);
            System.out.println(customer);
        } catch (IOException e) {
            // pass
        }
    }

    /**
     * 测试插入流程的单元测试
     */
    @Test
    public void testInsertProcess() {
        String resource = "mybatis-config-sample.xml";
        try (
                InputStream configResource = Resources.getResourceAsStream(resource);
                SqlSession sqlSession = new SqlSessionFactoryBuilder().build(configResource).openSession();
        ) {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            Customer customer = new Customer();
            customer.setName("likeheart");
            customer.setPhone("17799777979");
            int save = mapper.save(customer);
            sqlSession.commit();
            if (save > 0) {
                System.out.println("插入成功， 新插入的主键为：" + customer.getId());
            } else {
                System.out.println("插入异常");
            }
        } catch (IOException e) {
            // pass
        }
    }


    /**
     * 测试一级缓存
     * 加了<setting name="cacheEnabled" value="false"/>所有二级缓存不生效
     */
    @Test
    public void testBaseExecutor() {
        String resource = "mybatis-config-sample.xml";
        try (
                InputStream configResource = Resources.getResourceAsStream(resource);
                SqlSession sqlSession = new SqlSessionFactoryBuilder().build(configResource).openSession();
        ) {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            // 查询两次，测试缓存
            // 首先，使用同一个sqlSession执行两次查询，再未开启二级缓存的情况下，也只会查询一次数据库，因为默认一级缓存是SqlSession级别的。就是BaseExecutor下的localCache属性
            Customer customer = mapper.selectCustomerWithAddress(1L);
            System.out.println(customer);
//            sqlSession.commit();  // commit之后，一级缓存失效
            Customer customer2 = mapper.selectCustomerWithAddress(1L);
            System.out.println(customer2);
        } catch (IOException e) {
            // pass
        }
    }


    /**
     * 测试二级缓存
     */
    @Test
    public void testCachingExecutor() {
        // 首先，在不开启二级缓存的情况下，即cacheEnabled是false或没有cache标签，使用两个SqlSession获取，看到连接了两次
        // 在保证cacheEnabled是true的情况下，在映射文件添加cache标签
        // 省略了close
        try {
            String resource = "mybatis-config-sample.xml";
            InputStream configResource = Resources.getResourceAsStream(resource);
            SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configResource);
            SqlSession sqlSession1 = sqlSessionFactory.openSession();
            SqlSession sqlSession2 = sqlSessionFactory.openSession();
            CustomerMapper mapper1 = sqlSession1.getMapper(CustomerMapper.class);
            CustomerMapper mapper2 = sqlSession2.getMapper(CustomerMapper.class);
            // 查询两次，测试缓存
            // 首先，使用同一个sqlSession执行两次查询，再未开启二级缓存的情况下，也只会查询一次数据库，因为默认一级缓存是SqlSession级别的。就是BaseExecutor下的localCache属性
            Customer customer = mapper1.selectCustomerWithAddress(1L);
            System.out.println(customer);
            sqlSession1.commit();   // 提交数据库操作，这样缓存才会存起来，否则只会在TransactionalCache的entriesToAddOnCommit暂存
            Customer customer2 = mapper2.selectCustomerWithAddress(1L);
            System.out.println(customer2);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
