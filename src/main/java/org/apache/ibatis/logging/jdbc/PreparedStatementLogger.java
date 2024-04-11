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
package org.apache.ibatis.logging.jdbc;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * PreparedStatement proxy to add logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public final class PreparedStatementLogger extends BaseJdbcLogger implements InvocationHandler {

    private final PreparedStatement statement;

    private PreparedStatementLogger(PreparedStatement stmt, Log statementLog, int queryStack) {
        super(statementLog, queryStack);
        this.statement = stmt;
    }

    /**
     * 这个方法是PrepareStatement的方法在调用的时候真正的处理逻辑。
     *
     * @param proxy  被代理的对象
     * @param method 要调用的方法
     * @param params 方法的参数
     * @return 方法返回值
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
        try {
            // 如果是Object声明的方法，直接调用，不做处理
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, params);
            }
            // 如果EXECUTE_METHODS包括了这个方法名称，说明他是那里面的四个方法之一，打印日志
            if (EXECUTE_METHODS.contains(method.getName())) {
                if (isDebugEnabled()) {
                    debug("Parameters: " + getParameterValueString(), true);
                }
                clearColumnInfo();
                if ("executeQuery".equals(method.getName())) {
                    ResultSet rs = (ResultSet) method.invoke(statement, params);
                    // 将JDBC的返回结果集ResultSet做了一层代理，当调用ResultSet的方法获取数据时，执行的会是代理逻辑 也就是ResultSetLogger::Invoke
                    return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
                } else {
                    return method.invoke(statement, params);
                }
            } else if (SET_METHODS.contains(method.getName())) {
                if ("setNull".equals(method.getName())) {
                    setColumn(params[0], null);
                } else {
                    setColumn(params[0], params[1]);
                }
                return method.invoke(statement, params);
            } else if ("getResultSet".equals(method.getName())) {
                ResultSet rs = (ResultSet) method.invoke(statement, params);
                return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
            } else if ("getUpdateCount".equals(method.getName())) {
                int updateCount = (Integer) method.invoke(statement, params);
                if (updateCount != -1) {
                    debug("   Updates: " + updateCount, false);
                }
                return updateCount;
            } else {
                return method.invoke(statement, params);
            }
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }

    /**
     * Creates a logging version of a PreparedStatement.
     *
     * @param stmt         - the statement
     * @param statementLog - the statement log
     * @param queryStack   - the query stack
     * @return - the proxy
     */
    public static PreparedStatement newInstance(PreparedStatement stmt, Log statementLog, int queryStack) {
        InvocationHandler handler = new PreparedStatementLogger(stmt, statementLog, queryStack);
        ClassLoader cl = PreparedStatement.class.getClassLoader();
        return (PreparedStatement) Proxy.newProxyInstance(cl, new Class[]{PreparedStatement.class, CallableStatement.class}, handler);
    }

    /**
     * Return the wrapped prepared statement.
     *
     * @return the PreparedStatement
     */
    public PreparedStatement getPreparedStatement() {
        return statement;
    }

}
