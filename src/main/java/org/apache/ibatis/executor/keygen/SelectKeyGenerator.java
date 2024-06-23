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
package org.apache.ibatis.executor.keygen;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;

import java.sql.Statement;
import java.util.List;

/**
 * @author Clinton Begin
 * @author Jeff Butler
 * Oracle不支持自动生成主键自增id，一般用这个实现类
 * 启用该类的方法是，在映射文件的相关数据库操作节点内的SQL语句前添加一段selectKey标签
 * <\selectKey resultType="java.lang.Integer" keyProperty="id" order="AFTER">
 * SELECT LAST_INSERT_ID()
 * <\/selectKey>
 * 在数据库操作节点同时设置useGeneratedKeys属性和selectKey标签的情况下，selectKey标签生效。
 * <p>
 * 本类功能描述起来很简单：先执行一段特定的 SQL 语句获取一个值，然后将该值赋给 Java对象的自增属性。
 * 这个操作有两个执行时机：
 * 1. 在插入语句执行之前，执行特定的SQL，获得自增值，赋值给对象的自增属性，再将对象插入数据库
 * ① 如果数据库不支持主键自增，则完整的对象完整的插入数据库中
 * ② 如果数据库设置了主键自增，则刚才特定SQL语句查询的自增属性值会被数据库自身的自增值覆盖掉。这可能会导致Java对象的自增属性值和数据库的值不一致，这时候对于支持自增的数据库，推荐使用Jdbc3KeyGenerator类
 * 2. 在插入语句执行之后，Java对象的自增属性被设置成特定SQL语句的执行结果。
 * ① 如果数据库不支持自增，则之前插入数据库的对象的自增属性没有赋值，而Java对象的自增属性有值，这会导致不一致。这种操作是错误的。
 * ② 如果数据设置了主键自增，则数据库自增输出的值和sql执行产生的值可能不一样，
 * 所以，使用本类是需要根据具体情况仔细考虑、判断，对于支持自增的数据库，推荐使用Jdbc3KeyGenerator类
 * <p>
 * todo: 这块接触不多，所以没有深入研究。
 */
public class SelectKeyGenerator implements KeyGenerator {

    // 用于生成组建的SQL语句的特有标志，会追加在用于生成主键的SQL语句的id后方
    public static final String SELECT_KEY_SUFFIX = "!selectKey";
    // 为true插入前执行，为false插入后执行
    private final boolean executeBefore;
    // 用户生成主键的SQL语句，就是在数据库操作节点内sql语句前面添加的selectKey标签的内容
    private final MappedStatement keyStatement;

    public SelectKeyGenerator(MappedStatement keyStatement, boolean executeBefore) {
        this.executeBefore = executeBefore;
        this.keyStatement = keyStatement;
    }

    /**
     * 数据插入之前执行的操作
     *
     * @param executor  执行器
     * @param ms        映射语句对象
     * @param stmt      Statement对象
     * @param parameter 语句实参对象
     */
    @Override
    public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        if (executeBefore) {
            processGeneratedKeys(executor, ms, parameter);
        }
    }

    /**
     * 数据插入之后执行的操作
     *
     * @param executor  执行器
     * @param ms        映射语句对象
     * @param stmt      Statement对象
     * @param parameter 语句实参对象
     */
    @Override
    public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        if (!executeBefore) {
            processGeneratedKeys(executor, ms, parameter);
        }
    }

    /**
     * 执行一段特定的SQL获取一个值，用来给Java对象的自增属性赋值
     *
     * @param executor  执行器
     * @param ms        映射语句对象
     * @param parameter 参数对象
     */
    private void processGeneratedKeys(Executor executor, MappedStatement ms, Object parameter) {
        try {
            // keyStatement是用户生成主键的SQL语句，getKeyProperties方法是为了拿到自增的属性
            if (parameter != null && keyStatement != null && keyStatement.getKeyProperties() != null) {
                // 要自增的属性
                String[] keyProperties = keyStatement.getKeyProperties();
                final Configuration configuration = ms.getConfiguration();
                final MetaObject metaParam = configuration.newMetaObject(parameter);
                // Do not close keyExecutor.
                // The transaction will be closed by parent executor.
                // 创建一个新的Executor对象来执行指定的select语句，父级执行器会关闭它
                Executor keyExecutor = configuration.newExecutor(executor.getTransaction(), ExecutorType.SIMPLE);
                // 执行sql语句，拿到主键值
                List<Object> values = keyExecutor.query(keyStatement, parameter, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
                // 查到的自增主键值必须有且仅有一个
                if (values.size() == 0) {
                    throw new ExecutorException("SelectKey returned no data.");
                } else if (values.size() > 1) {
                    throw new ExecutorException("SelectKey returned more than one value.");
                } else { // 查到的自增值有且仅有一个
                    // 创建实参对象的MetaObject对象
                    MetaObject metaResult = configuration.newMetaObject(values.get(0));
                    if (keyProperties.length == 1) {
                        // 将主键信息记录到用户传入的实参对象中
                        if (metaResult.hasGetter(keyProperties[0])) {
                            // 从metaResult中用getter方法得到主键值
                            setValue(metaParam, keyProperties[0], metaResult.getValue(keyProperties[0]));
                        } else {
                            // no getter for the property - maybe just a single value object
                            // so try that
                            // 可能返回的就是主键值本身
                            setValue(metaParam, keyProperties[0], values.get(0));
                        }
                    } else {
                        // 要把执行SQL语句得到的值赋给多个属性的处理。
                        handleMultipleProperties(keyProperties, metaParam, metaResult);
                    }
                }
            }
        } catch (ExecutorException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutorException("Error selecting key or setting result to parameter object. Cause: " + e, e);
        }
    }

    private void handleMultipleProperties(String[] keyProperties,
                                          MetaObject metaParam, MetaObject metaResult) {
        String[] keyColumns = keyStatement.getKeyColumns();

        if (keyColumns == null || keyColumns.length == 0) {
            // no key columns specified, just use the property names
            for (String keyProperty : keyProperties) {
                setValue(metaParam, keyProperty, metaResult.getValue(keyProperty));
            }
        } else {
            if (keyColumns.length != keyProperties.length) {
                throw new ExecutorException("If SelectKey has key columns, the number must match the number of key properties.");
            }
            for (int i = 0; i < keyProperties.length; i++) {
                setValue(metaParam, keyProperties[i], metaResult.getValue(keyColumns[i]));
            }
        }
    }

    private void setValue(MetaObject metaParam, String property, Object value) {
        if (metaParam.hasSetter(property)) {
            metaParam.setValue(property, value);
        } else {
            throw new ExecutorException("No setter found for the keyProperty '" + property + "' in " + metaParam.getOriginalObject().getClass().getName() + ".");
        }
    }
}
