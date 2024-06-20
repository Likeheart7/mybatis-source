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
package org.apache.ibatis.scripting;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;

/**
 * 语言驱动类的父接口。
 * 用户可以通过配置文件的defaultScriptingLanguage属性自定义驱动。该功能由{@link org.apache.ibatis.builder.xml.XMLConfigBuilder#settingsElement} 中的
 * configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage"))); 实现
 */
public interface LanguageDriver {

    /**
     * 创建 {@link ParameterHandler} 参数处理器，其会传递实际阐述到JDBC statement
     *
     * @param mappedStatement 完整的数据库操作节点
     * @param parameterObject 参数对象
     * @param boundSql        数据库操作语句转化成的BoundSql对象
     * @return 常见的参数处理器
     * @author Frank D. Martinez [mnesarco]
     * @see DefaultParameterHandler
     */
    ParameterHandler createParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql);

    /**
     * 创建 {@link SqlSource} ，该方法在Mybatis启动阶段读取映射接口或映射文件时被调用
     *
     * @param configuration 全局配置对象
     * @param script        映射文件中的数据库操作节点
     * @param parameterType 参数类型
     * @return 创建的SqlSource对象
     */
    SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType);

    /**
     * Creates an {@link SqlSource} that will hold the statement read from an annotation.
     * It is called during startup, when the mapped statement is read from a class or an xml file.
     * <p>
     * 基于注解的方式，创建SqlSource对象。该方法在Mybatis启动阶段读取映射接口或映射文件时被调用
     *
     * @param configuration The MyBatis configuration  全局配置对象
     * @param script        The content of the annotation 注解中的SQL字符串
     * @param parameterType input parameter type got from a mapper method or specified in the parameterType xml attribute. Can be null. 参数类型
     * @return the sql source   创建的SqlSource对象
     */
    SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType);

}
