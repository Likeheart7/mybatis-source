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
package org.apache.ibatis.type;

import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.session.Configuration;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The base {@link TypeHandler} for references a generic type.
 * <p>
 * Important: Since 3.5.0, This class never call the {@link ResultSet#wasNull()} and
 * {@link CallableStatement#wasNull()} method for handling the SQL {@code NULL} value.
 * In other words, {@code null} value handling should be performed on subclass.
 * </p>
 * 类型处理器的基础实现，实现一些TypeHandler的公共逻辑，采用了模板方法模式
 * 具体实现子类以{@link org.apache.ibatis.type.LongTypeHandler} 为例
 *
 * @author Clinton Begin
 * @author Simone Tripodi
 * @author Kzuki Shimizu
 */
public abstract class BaseTypeHandler<T> extends TypeReference<T> implements TypeHandler<T> {

    /**
     * @deprecated Since 3.5.0 - See https://github.com/mybatis/mybatis-3/issues/1203. This field will remove future.
     */
    @Deprecated
    protected Configuration configuration;

    /**
     * Sets the configuration.
     *
     * @param c the new configuration
     * @deprecated Since 3.5.0 - See https://github.com/mybatis/mybatis-3/issues/1203. This property will remove future.
     */
    @Deprecated
    public void setConfiguration(Configuration c) {
        this.configuration = c;
    }

    /**
     * 对PreparedStatement的数据进行设置
     *
     * @param ps        PreparedStatement对象
     * @param i         第i个参数
     * @param parameter 参数值
     * @param jdbcType  jdbc类型
     */
    @Override
    public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        // 如果parameter为空的话
        if (parameter == null) {
            if (jdbcType == null) {
                throw new TypeException("JDBC requires that the JdbcType must be specified for all nullable parameters.");
            }
            try {
                // 通过setNull方法赋空值
                ps.setNull(i, jdbcType.TYPE_CODE);
            } catch (SQLException e) {
                throw new TypeException("Error setting null for parameter #" + i + " with JdbcType " + jdbcType + " . "
                        + "Try setting a different JdbcType for this parameter or a different jdbcTypeForNull configuration property. "
                        + "Cause: " + e, e);
            }
        } else {
            // parameter不为空情况
            try {
                //该方法由具体的子类实现
                setNonNullParameter(ps, i, parameter, jdbcType);
            } catch (Exception e) {
                throw new TypeException("Error setting non null for parameter #" + i + " with JdbcType " + jdbcType + " . "
                        + "Try setting a different JdbcType for this parameter or a different configuration property. "
                        + "Cause: " + e, e);
            }
        }
    }

    @Override
    public T getResult(ResultSet rs, String columnName) throws SQLException {
        try {
            return getNullableResult(rs, columnName);
        } catch (Exception e) {
            throw new ResultMapException("Error attempting to get column '" + columnName + "' from result set.  Cause: " + e, e);
        }
    }

    @Override
    public T getResult(ResultSet rs, int columnIndex) throws SQLException {
        try {
            return getNullableResult(rs, columnIndex);
        } catch (Exception e) {
            throw new ResultMapException("Error attempting to get column #" + columnIndex + " from result set.  Cause: " + e, e);
        }
    }


    @Override
    public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
        try {
            return getNullableResult(cs, columnIndex);
        } catch (Exception e) {
            throw new ResultMapException("Error attempting to get column #" + columnIndex + " from callable statement.  Cause: " + e, e);
        }
    }

    /**
     * 向PreparedStatement对象中的指定变量位置写入一个不为null的值
     *
     * @param ps
     * @param i         指定变量位置
     * @param parameter 参数值
     * @param jdbcType  JDBC类型
     */
    public abstract void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

    /**
     * Gets the nullable result.
     * 从ResultSet中按照字段名读出一个可能为null的数据
     *
     * @param rs         the rs
     * @param columnName Colunm name, when configuration <code>useColumnLabel</code> is <code>false</code>
     * @return the nullable result
     * @throws SQLException the SQL exception
     */
    public abstract T getNullableResult(ResultSet rs, String columnName) throws SQLException;


    /**
     * 从 ResultSet 中按照字段编号读出一个可能为null的数据
     *
     * @param rs          结果集
     * @param columnIndex 字段索引
     */
    public abstract T getNullableResult(ResultSet rs, int columnIndex) throws SQLException;

    /**
     * 从CallableStatement中按照字段编号读出一个可能为NULL的数据
     *
     * @param cs
     * @param columnIndex 字段索引
     */
    public abstract T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException;

}
