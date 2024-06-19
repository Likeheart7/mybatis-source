/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.mapping;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Vendor DatabaseId provider.
 * <p>
 * It returns database product name as a databaseId.
 * If the user provides a properties it uses it to translate database product name
 * key="Microsoft SQL Server", value="ms" will return "ms".
 * It can return null, if no database product name or
 * a properties was specified and no translation was found.
 * <p>
 * 多数据库种类支持的实现类
 *
 * @author Eduardo Macarron
 */
public class VendorDatabaseIdProvider implements DatabaseIdProvider {

    private Properties properties;

    /**
     * 获取数据库id的方法，就是根据连接获取数据源的类型，匹配databaseIdProvider标签配置的别名
     * 是现在执行sql语句时，根据数据库操作标签的databaseId属性确定要执行的是哪个sql
     *
     * @param dataSource 数据源
     */
    @Override
    public String getDatabaseId(DataSource dataSource) {
        if (dataSource == null) {
            throw new NullPointerException("dataSource cannot be null");
        }
        try {
            return getDatabaseName(dataSource);
        } catch (Exception e) {
            LogHolder.log.error("Could not get a databaseId from dataSource", e);
        }
        return null;
    }

    @Override
    public void setProperties(Properties p) {
        this.properties = p;
    }

    /**
     * 获取数据库名称
     *
     * @param dataSource 数据源
     * @return 获取到的数据库别名
     * @throws SQLException
     */
    private String getDatabaseName(DataSource dataSource) throws SQLException {
        // 获取当前连接的数据库名
        String productName = getDatabaseProductName(dataSource);
        // 如果存在properties值，则将获取到的数据库名称作为模糊的key，查询对应的value
        if (this.properties != null) {
            // 根据<databaseIdProvider>标签配置，查找自定义数据库名称
            for (Map.Entry<Object, Object> property : properties.entrySet()) {
                if (productName.contains((String) property.getKey())) {
                    // 返回配置的value
                    return (String) property.getValue();
                }
            }
            // no match, return null
            return null;
        }
        return productName;
    }

    private String getDatabaseProductName(DataSource dataSource) throws SQLException {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();
            return metaData.getDatabaseProductName();
        }

    }

    private static class LogHolder {
        private static final Log log = LogFactory.getLog(VendorDatabaseIdProvider.class);
    }

}
