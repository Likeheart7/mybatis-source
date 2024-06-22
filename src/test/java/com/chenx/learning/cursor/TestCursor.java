package com.chenx.learning.cursor;

import com.chenx.learning.dao.AddressMapper;
import com.chenx.learning.pojo.Address;
import com.chenx.learning.util.DaoUtils;
import org.apache.ibatis.cursor.Cursor;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang.time.StopWatch;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;

public class TestCursor {
    /**
     * 使用游标查询案例，对于大结果集，内存占用会小很多
     */
    @Test
    public void testCursor() {
        // 普通查询，通过jvisualvm查看内存
//        AtomicReference<List<Address>> list = new AtomicReference<>(new ArrayList());
//        DaoUtils.execute(sqlSession -> {
//            AddressMapper mapper = sqlSession.getMapper(AddressMapper.class);
//            list.set(mapper.findAll(2L));
//            return null;
//        });
//        try {
//            TimeUnit.SECONDS.sleep(20);
//        } catch (InterruptedException e) {
//        }
        // cursor查询
        DaoUtils.execute(sqlSession -> {
            AddressMapper mapper = sqlSession.getMapper(AddressMapper.class);
            Cursor<Address> cursor = mapper.queryAllWithCursor();
            System.out.println(cursor);
//            try {
//                TimeUnit.SECONDS.sleep(30);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            for (Address address : cursor) {
                System.out.println(address);
            }
            return null;
        });
    }

    /**
     * 向address表插入20w条数据
     */
    @Test
    public void insertAddress() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/mybatis-learning?serverTimezone=GMT%2B8&rewriteBatchedStatements=true", "root", "root");
        conn.setAutoCommit(false);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Statement statement = conn.createStatement();
        for (int i = 0; i < 200000; i++) {
            statement.addBatch(MessageFormat.format("insert into t_address(street, city, country, customer_id) value(''{0}'', ''安徽'', ''中国'', 2)", "星园街道" + i));
        }
        statement.executeBatch();
        conn.commit();
        stopWatch.stop();
        System.out.println(stopWatch.getTime());
    }
}
