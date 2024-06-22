/**
 * Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.transaction;

import org.apache.ibatis.session.TransactionIsolationLevel;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Properties;

/**
 * Creates {@link Transaction} instances.
 *
 * @author Clinton Begin
 * 所有事务工厂的接口
 */
public interface TransactionFactory {

    /**
     * 配置事务工厂的属性
     * Sets transaction factory custom properties.
     *
     * @param props 工厂的属性
     *              the new properties
     */
    default void setProperties(Properties props) {
        // NOP
    }

    /**
     * 从给定的连接中获取一个事务
     * Creates a {@link Transaction} out of an existing connection.
     *
     * @param conn 给定的连接
     * @return 获取的事务对象
     * @since 3.1.0
     */
    Transaction newTransaction(Connection conn);

    /**
     * 从给定的数据源获取事务，并对事务进行一些设置
     * Creates a {@link Transaction} out of a datasource.
     *
     * @param dataSource 给定的数据源
     * @param level      事务隔离级别
     * @param autoCommit 是否自动提交
     * @return 获取的事务
     * @since 3.1.0
     */
    Transaction newTransaction(DataSource dataSource, TransactionIsolationLevel level, boolean autoCommit);

}
