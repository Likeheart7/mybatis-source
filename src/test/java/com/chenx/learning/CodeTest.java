package com.chenx.learning;

import com.chenx.learning.dao.CustomerMapper;
import com.chenx.learning.pojo.Address;
import org.apache.ibatis.reflection.Reflector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

public class CodeTest {
    @Test
    public void testAddGetMethods() {
        new Reflector(Address.class);
    }

    @Test
    public void testPingDB() throws Exception {
        String poolPingQuery = "NO PING QUERY SET";
//        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mybatis-learning?serverTimezone=Asia/Shanghai", "root", "root");
        Statement pingQuerySet = connection.createStatement();
        ResultSet resultSet = pingQuerySet.executeQuery("select 1");
        System.out.println(resultSet);
        pingQuerySet.close();
        resultSet.close();
    }

    @Test
    public void testGetParamAnno() throws NoSuchMethodException {
        Method method = CustomerMapper.class.getMethod("find", long.class);
        System.out.println(Arrays.toString(method.getParameterAnnotations()));
    }

}
