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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 * 处理Long类型的TypeHandler，内部就是通过调用PreparedStatement的方法实现的。
 * 其他的TypeHandler也是相似的逻辑
 * 泛型参数是Long表示其方法给出的就是Long类型的结果
 */
public class LongTypeHandler extends BaseTypeHandler<Long> {

    /**
     * 设置非空参数
     *
     * @param ps        PreparedStatement对象
     * @param i         第i个参数
     * @param parameter 参数值
     * @param jdbcType  jdbc类型
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Long parameter, JdbcType jdbcType)
            throws SQLException {
        // 调用setLong方法绑定参数
        ps.setLong(i, parameter);
    }

    /**
     * 根据列名获取指定列的值
     *
     * @param rs         ResultSet对象，返回的数据集
     * @param columnName 列名
     * @return 获取到的Long类型的数据
     */
    @Override
    public Long getNullableResult(ResultSet rs, String columnName)
            throws SQLException {
        // 调用getLong方法获取指定列值
        long result = rs.getLong(columnName);
        return result == 0 && rs.wasNull() ? null : result;
    }

    /**
     * 根据列的索引获取指定列的值
     *
     * @param rs          ResultSet对象，返回的数据集
     * @param columnIndex 列的索引
     * @return 获取到的Long类型的数据
     */
    @Override
    public Long getNullableResult(ResultSet rs, int columnIndex)
            throws SQLException {
        long result = rs.getLong(columnIndex);
        return result == 0 && rs.wasNull() ? null : result;
    }

    /**
     * CallableStatement是PreparedStatement的一个子类
     *
     * @param cs          CallableStatement对象
     * @param columnIndex 列的索引
     * @return 获取到的Long类型的数据
     */
    @Override
    public Long getNullableResult(CallableStatement cs, int columnIndex)
            throws SQLException {
        long result = cs.getLong(columnIndex);
        return result == 0 && cs.wasNull() ? null : result;
    }
}
