/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
/**
 * Contains the key generators.
 * 本包负责主键自增相关，也提供一些支持Mybatis兼容不支持主键自增的数据库，如Oracle
 * 开启主键自增可以在配置文件的settings标签中添加 <\setting name="useGeneratedKeys" value="true"/>，或者直接在相关映射文件的数据库操作节点上添加useGeneratedKeys="true"属性，再通过keyProperty属性声明主键名称
 */
package org.apache.ibatis.executor.keygen;
