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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wraps a database connection.
 * Handles the connection lifecycle that comprises: its creation, preparation, commit/rollback and close.
 * 所有事务的接口
 *
 * @author Clinton Begin
 */
public interface Transaction {

    /**
     * Retrieve inner database connection.
     * 获取事务对应的数据库连接
     *
     * @return 数据库连接
     * @throws SQLException the SQL exception
     */
    Connection getConnection() throws SQLException;

    /**
     * 提交事务
     *
     * @throws SQLException the SQL exception
     */
    void commit() throws SQLException;

    /**
     * 回滚事务
     *
     * @throws SQLException the SQL exception
     */
    void rollback() throws SQLException;

    /**
     * 关闭对应的数据库连接
     *
     * @throws SQLException the SQL exception
     */
    void close() throws SQLException;

    /**
     * 读取设置的事务超时时间
     *
     * @return 事务超时时间
     * @throws SQLException the SQL exception
     */
    Integer getTimeout() throws SQLException;

}
