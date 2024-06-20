package com.chenx.learning.datasource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatasourceTest {
    @Test
    public void testDatasource() throws SQLException {
        UnpooledDataSource dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/demo-db?serverTimezone=GMT%2B8", "root", "root");
        Connection conn = dataSource.getConnection();
        System.out.println(conn.getMetaData().getDriverVersion());
    }

    @Test
    public void testDriverManager() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/demo-db?serverTimezone=GMT%2B8", "root", "root");
        System.out.println(conn.getMetaData().getDriverVersion());
    }
}
