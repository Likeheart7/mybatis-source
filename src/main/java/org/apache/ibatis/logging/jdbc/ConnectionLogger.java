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
package org.apache.ibatis.logging.jdbc;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Connection proxy to add logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * Connection的日志管理，实现了InvocationHandler，为了JDBC的Connection相关的日志打印
 */
public final class ConnectionLogger extends BaseJdbcLogger implements InvocationHandler {

    private final Connection connection;

    // 创建的时候会传入一个连接，作为成员变量connection的值
    private ConnectionLogger(Connection conn, Log statementLog, int queryStack) {
        super(statementLog, queryStack);
        this.connection = conn;
    }

    /**
     * 当使用的是Connection的代理类时，所有该Connection类的调用都会进入该invoke方法
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] params)
            throws Throwable {
        try {
            // 判断是不是Object类声明的方法，如果是就直接调用，不做添加任何逻辑
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, params);
            }
            // 如果是prepareStatement() 或 prepareCall()这两个方法
            if ("prepareStatement".equals(method.getName()) || "prepareCall".equals(method.getName())) {
                if (isDebugEnabled()) {
                    // 打印个日志
                    debug(" Preparing: " + removeExtraWhitespace((String) params[0]), true);
                }
                PreparedStatement stmt = (PreparedStatement) method.invoke(connection, params);
                // 传递给下游的PrepareStatement对象也是被代理的。用于管理JDBC PreparedStatement的日志
                stmt = PreparedStatementLogger.newInstance(stmt, statementLog, queryStack);
                return stmt;
            } else if ("createStatement".equals(method.getName())) {    // 如果是createStatement()也创建个代理对象
                Statement stmt = (Statement) method.invoke(connection, params);
                // 传递给下游的Statement对象也是被代理的。用于管理JDBC Statement的日志
                stmt = StatementLogger.newInstance(stmt, statementLog, queryStack);
                return stmt;
            } else {
                return method.invoke(connection, params);
            }
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }

    /**
     * Creates a logging version of a connection.
     * 查看 {@link org.apache.ibatis.executor.BaseExecutor#getConnection(Log)} 的代码逻辑
     * 可以发现，当开启Debug日志级别时，为了打印出JDBC的Connection相关日志，使用的是ConnectionLogger.newInstance()获取的代理类实例
     *
     * @param conn         the original connection
     * @param statementLog the statement log
     * @param queryStack   the query stack
     * @return the connection with logging
     */
    public static Connection newInstance(Connection conn, Log statementLog, int queryStack) {
        InvocationHandler handler = new ConnectionLogger(conn, statementLog, queryStack);
        ClassLoader cl = Connection.class.getClassLoader();
        return (Connection) Proxy.newProxyInstance(cl, new Class[]{Connection.class}, handler);
    }

    /**
     * return the wrapped connection.
     *
     * @return the connection
     */
    public Connection getConnection() {
        return connection;
    }

}
