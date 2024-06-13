/**
 * Copyright 2009-2015 the original author or authors.
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
 * Logging proxies that logs any JDBC statement.
 * Mybatis本身不负责数据库的操作，由JDBC负责，而JDBC的日志和Mybatis的日志是独立的
 * 这个包的作用是将数据库操作的信息打印到日志中
 * 一般只在测试环境中使用，否则可能影响系统性能
 */
package org.apache.ibatis.logging.jdbc;
