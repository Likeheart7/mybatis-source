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
package org.apache.ibatis.transaction.jdbc;

import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Creates {@link JdbcTransaction} instances.
 *
 * @author Clinton Begin
 * JdbcTransaction类的工厂。
 * @see JdbcTransaction
 */
public class JdbcTransactionFactory implements TransactionFactory {

    /**
     * 基于给定的连接生成JdbcTransaction对象
     *
     * @param conn 给定的连接
     * @return
     */
    @Override
    public Transaction newTransaction(Connection conn) {
        return new JdbcTransaction(conn);
    }

    /**
     * 基于给定的数据源、事务隔离级别、是否自动提交，生成对应的JdbcTransaction对象
     *
     * @param ds         给定的数据源
     * @param level      事务隔离级别
     * @param autoCommit 是否自动提交
     * @return
     */
    @Override
    public Transaction newTransaction(DataSource ds, TransactionIsolationLevel level, boolean autoCommit) {
        return new JdbcTransaction(ds, level, autoCommit);
    }
}
