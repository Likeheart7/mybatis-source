package com.chenx.learning.load;

import com.chenx.learning.dao.CustomerMapper;
import com.chenx.learning.pojo.Customer;
import com.chenx.learning.util.DaoUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

public class testLazyLoad {
    /**
     * 我们通过Customer 及其属性address来测试懒加载功能。
     * 通过日志查看懒加载的具体操作
     */
    @Test
    public void testLazyLoading() {
        DaoUtils.execute(sqlSession -> {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            List<Customer> customers = mapper.findCustomerLazyLoading("chen");
            System.out.println("符合查询条件的结果有" + customers.size() + " 个。");
            // 开启激进懒加载后，可以看到，直到这个循环遍历输出完成，日志中都没有查询t_address表的记录
            for (Customer customer : customers) {
                System.out.println(customer.getName());
            }
            // 尝试访问customer中第一个元素的address属性，日志中应该多一条查询，查询该customer对应的地址。
            System.out.println(customers.get(0).getAddresses());
            // 关闭懒加载后，先执行所有查询，在输出两个System.out的内容，开启激进懒加载后，访问address属性时，才访问数据库查询了被访问懒加载元素的对象的address信息
            // 所以先查询t_customer，然后打印第一个sout，在查询第二个，打印第二个sout，所以这个过程是懒加载的
            /*
                Opening JDBC Connection
                Created connection 795321555.
                Setting autocommit to false on JDBC Connection [com.mysql.cj.jdbc.ConnectionImpl@2f67a4d3]
                ==>  Preparing: select * from t_customer where name = ?
                ==> Parameters: chen(String)
                <==    Columns: id, name, phone
                <==        Row: 2, chen, 123456
                <==        Row: 3, chen, 123456
                <==      Total: 2
                符合查询条件的结果有2 个。
                chen
                chen
                ==>  Preparing: SELECT * FROM t_address WHERE id = ?
                ==> Parameters: 2(Long)
                <==    Columns: id, street, city, country, customer_id
                <==        Row: 2, 金岭, 安徽, 中国, 1
                <==      Total: 1
                [Address{id=2, street='金岭', city='安徽', country='中国', customerId=1}]
             */
            return null;
        });
    }
}
