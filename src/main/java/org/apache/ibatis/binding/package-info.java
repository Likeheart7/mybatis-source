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
 * <p>
 */
/**
 * 绑定mapper和xml文件的模块
 * 维护映射接口中抽象方法和xml文件数据库操作节点之间的关系
 * 基于动态代理为映射接口的抽象方法接入对应的数据库操作
 *
 * 总结：
 *  Mybatis在初始化阶段
 *      1. 解析所有的映射文件(XxxMapper.xml)，将各个数据库操作节点（select、insert等节点）的信息记录到{@link org.apache.ibatis.session.Configuration#mappedStatements}对象中
 *      2. 扫描所有映射接口，根据接口创建对应的MapperProxyFactory，由{@link org.apache.ibatis.binding.MapperRegistry#knownMappers} 维护映射接口和MapperProxyFactory的对应关系。
 *          getMapper方法就是通过映射接口的类型找到对应的MapperProxyFactory。MapperProxyFactory会产出映射接口对应的MapperProxy代理对象。
 *      3. 当调用Mapper接口的方法时，会经过代理对象的invoke方法，在{@link org.apache.ibatis.binding.MapperProxy#invoke}方法中，找到对应的MapperMethod对象（可能被缓存提高效率）。
*       4. 在上一步创建MapperMethod对象的过程中，{@link org.apache.ibatis.binding.MapperMethod}类有两个属性，其中一个时SqlCommand类型。在创建SqlCommand 的过程中，SqlCommand的构造方法会调用
 *          {@link org.apache.ibatis.binding.MapperMethod.SqlCommand#resolveMappedStatement}方法，该方法从Configuration对象的{@link org.apache.ibatis.session.Configuration#mappedStatements}
 *          属性中，通过当前 "映射接口名称.方法名称"查找已经解析好的SQL语句信息。
 *      5. 最终执行MapperMethod的execute方法，实现执行映射文件中对应的数据库操作语句。
 *      一个针对映射接口的方法调用，就被转成了对应的数据库操作
 *      补充：而在Spring中，Spring在启动阶段会使用 MapperScannerConfigurer类对
 *          指定包进行扫描。对于扫描到的映射接口，mybatis-spring 会将其当作MapperFactoryBean
 *          对象注册到 Spring的 Bean列表中。而 MapperFactoryBean可以给出映射接口的代理类
 *
 * Bings mapper interfaces with mapped statements.
 */
package org.apache.ibatis.binding;
