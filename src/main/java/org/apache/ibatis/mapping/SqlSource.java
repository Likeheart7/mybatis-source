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
package org.apache.ibatis.mapping;

/**
 * Represents the content of a mapped statement read from an XML file or an annotation.
 * It creates the SQL that will be passed to the database out of the input parameter received from the user.
 *
 * @author Clinton Begin
 * 数据库操作标签包含的SQL语句
 * 有4个实现：
 * 1. DynamicSqlSource：动态SQL语句。指的是包含if、foreach等标签，或含有${}占位符的语句
 * 2. RawSqlSource：原生SQL语句。指非动态SQL语句，可以包含#{}占位符，因为其会被?直接替换
 * 3. StaticSqlSource：静态语句。可以包含 ?，可以直接提交给数据库执行
 * 4. ProviderSqlSource：通过注解获得的SQL语句。
 * DynamicSqlSource、RowSqlSource是两个主要子类。最后都被处理成StaticSqlSource，然后通过StaticSqlSource的getBoundSql获得SqlSource对象。
 */
public interface SqlSource {

    /**
     * 获取一个BoundSql对象
     *
     * @param parameterObject 参数对象
     * @return BoundSql对象
     */
    BoundSql getBoundSql(Object parameterObject);

}
