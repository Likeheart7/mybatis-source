/**
 * Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.util.List;

/**
 * @author Clinton Begin
 * 处理存储过程的实现。
 * 它一共完成两步工作
 * ：一是通过 registerOutputParameters方法中转后调用 java.sql.CallableStatement中的输出参数注册方法完成输出参数的注册；
 * 二是通过 ParameterHandler接口经过多级中转后调用 java.sql.PreparedStatement类中的参数赋值方法
 */
public class CallableStatementHandler extends BaseStatementHandler {

    public CallableStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public int update(Statement statement) throws SQLException {
        CallableStatement cs = (CallableStatement) statement;
        cs.execute();
        int rows = cs.getUpdateCount();
        Object parameterObject = boundSql.getParameterObject();
        KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
        keyGenerator.processAfter(executor, mappedStatement, cs, parameterObject);
        resultSetHandler.handleOutputParameters(cs);
        return rows;
    }

    @Override
    public void batch(Statement statement) throws SQLException {
        CallableStatement cs = (CallableStatement) statement;
        cs.addBatch();
    }

    @Override
    public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
        CallableStatement cs = (CallableStatement) statement;
        // 执行存储过程
        cs.execute();
        // 处理存储过程返回的结果集
        List<E> resultList = resultSetHandler.handleResultSets(cs);
        // 处理输出参数
        resultSetHandler.handleOutputParameters(cs);
        return resultList;
    }

    @Override
    public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
        CallableStatement cs = (CallableStatement) statement;
        cs.execute();
        Cursor<E> resultList = resultSetHandler.handleCursorResultSets(cs);
        resultSetHandler.handleOutputParameters(cs);
        return resultList;
    }

    @Override
    protected Statement instantiateStatement(Connection connection) throws SQLException {
        String sql = boundSql.getSql();
        if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
            return connection.prepareCall(sql);
        } else {
            return connection.prepareCall(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
        }
    }

    /**
     * 对语句中的参数进行处理
     *
     * @param statement statement对象，代表SQL语句
     * @throws SQLException
     */
    @Override
    public void parameterize(Statement statement) throws SQLException {
        // 输出参数的注册
        registerOutputParameters((CallableStatement) statement);
        // 输入参数的处理
        parameterHandler.setParameters((CallableStatement) statement);
    }

    private void registerOutputParameters(CallableStatement cs) throws SQLException {
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        for (int i = 0, n = parameterMappings.size(); i < n; i++) {
            ParameterMapping parameterMapping = parameterMappings.get(i);
            if (parameterMapping.getMode() == ParameterMode.OUT || parameterMapping.getMode() == ParameterMode.INOUT) {
                if (null == parameterMapping.getJdbcType()) {
                    throw new ExecutorException("The JDBC Type must be specified for output parameter.  Parameter: " + parameterMapping.getProperty());
                } else {
                    if (parameterMapping.getNumericScale() != null && (parameterMapping.getJdbcType() == JdbcType.NUMERIC || parameterMapping.getJdbcType() == JdbcType.DECIMAL)) {
                        cs.registerOutParameter(i + 1, parameterMapping.getJdbcType().TYPE_CODE, parameterMapping.getNumericScale());
                    } else {
                        if (parameterMapping.getJdbcTypeName() == null) {
                            cs.registerOutParameter(i + 1, parameterMapping.getJdbcType().TYPE_CODE);
                        } else {
                            cs.registerOutParameter(i + 1, parameterMapping.getJdbcType().TYPE_CODE, parameterMapping.getJdbcTypeName());
                        }
                    }
                }
            }
        }
    }

}
