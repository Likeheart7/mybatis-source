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
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * ResultSet proxy to add logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public final class ResultSetLogger extends BaseJdbcLogger implements InvocationHandler {

    private static final Set<Integer> BLOB_TYPES = new HashSet<>();
    private boolean first = true;
    private int rows;
    private final ResultSet rs;
    private final Set<Integer> blobColumns = new HashSet<>();

    static {
        BLOB_TYPES.add(Types.BINARY);
        BLOB_TYPES.add(Types.BLOB);
        BLOB_TYPES.add(Types.CLOB);
        BLOB_TYPES.add(Types.LONGNVARCHAR);
        BLOB_TYPES.add(Types.LONGVARBINARY);
        BLOB_TYPES.add(Types.LONGVARCHAR);
        BLOB_TYPES.add(Types.NCLOB);
        BLOB_TYPES.add(Types.VARBINARY);
    }

    private ResultSetLogger(ResultSet rs, Log statementLog, int queryStack) {
        super(statementLog, queryStack);
        this.rs = rs;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
        try {
            // 同样，Object声明的方法直接执行，不做处理
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, params);
            }
            // 先执行方法拿到返回值
            Object o = method.invoke(rs, params);
            // 如果调用的是next()，返回的结果是个布尔值
            if ("next".equals(method.getName())) {
                if ((Boolean) o) {
                    rows++; // 记录ResultSet中的行数
                    if (isTraceEnabled()) {
                        // 获取数据集的列元数据
                        ResultSetMetaData rsmd = rs.getMetaData();
                        // 获取结果集的列数
                        final int columnCount = rsmd.getColumnCount();
                        // 判断数据是不是第一行数据，如果是，打印一个表头信息
                        if (first) {
                            first = false;
                            // 除了打印表头信息，还会记录超大类型的列的名称，在下一步打印数据的时候就不打印这些列的具体值
                            printColumnHeaders(rsmd, columnCount);
                        }
                        // 输出当前遍历的这行记录，这里会过滤掉超大类型列的数据，不进行输出
                        printColumnValues(columnCount);
                    }
                } else {
                    // 打印出结果集总行数
                    debug("     Total: " + rows, false);
                }
            }
            // 清空这三个缓存columnMap，columnNames，columnValues
            clearColumnInfo();
            return o;
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }

    private void printColumnHeaders(ResultSetMetaData rsmd, int columnCount) throws SQLException {
        StringJoiner row = new StringJoiner(", ", "   Columns: ", "");
        for (int i = 1; i <= columnCount; i++) {
            if (BLOB_TYPES.contains(rsmd.getColumnType(i))) {
                // 记录BLOB等超大类型的列名
                blobColumns.add(i);
            }
            row.add(rsmd.getColumnLabel(i));
        }
        trace(row.toString(), false);
    }

    private void printColumnValues(int columnCount) {
        StringJoiner row = new StringJoiner(", ", "       Row: ", "");
        for (int i = 1; i <= columnCount; i++) {
            try {
                // 如果是超大类型的列，就直接打印<<BLOB>> 防止打印大量字符
                if (blobColumns.contains(i)) {
                    row.add("<<BLOB>>");
                } else {
                    row.add(rs.getString(i));
                }
            } catch (SQLException e) {
                // generally can't call getString() on a BLOB column
                row.add("<<Cannot Display>>");
            }
        }
        trace(row.toString(), false);
    }

    /**
     * Creates a logging version of a ResultSet.
     *
     * @param rs           the ResultSet to proxy
     * @param statementLog the statement log
     * @param queryStack   the query stack
     * @return the ResultSet with logging
     */
    public static ResultSet newInstance(ResultSet rs, Log statementLog, int queryStack) {
        InvocationHandler handler = new ResultSetLogger(rs, statementLog, queryStack);
        ClassLoader cl = ResultSet.class.getClassLoader();
        return (ResultSet) Proxy.newProxyInstance(cl, new Class[]{ResultSet.class}, handler);
    }

    /**
     * Get the wrapped result set.
     *
     * @return the resultSet
     */
    public ResultSet getRs() {
        return rs;
    }

}
