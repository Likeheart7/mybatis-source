package com.chenx.learning.jdbc;

import org.apache.ibatis.jdbc.SqlRunner;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class SqlRunnerTest {
    @Test
    public void testSqlRunner() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/mybatis-learning?serverTimezone=GMT%2B8", "root", "root");
        SqlRunner sqlRunner = new SqlRunner(conn);
        // SqlRunner会替换 ? 占位符
        Map<String, Object> resultMap = sqlRunner.selectOne("SELECT * FROM t_customer WHERE id = ?", 1); // 这个返回的Map就是各列及其对应值，列名称全大写作为键
        System.out.println(resultMap);
        List<Map<String, Object>> results = sqlRunner.selectAll("SELECT * FROM t_customer");
        System.out.println(results);
    }
}
