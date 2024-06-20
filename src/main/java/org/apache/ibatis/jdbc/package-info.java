/**
 *    Copyright 2009-2018 the original author or authors.
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
 * Utilities for JDBC.
 * 本包负责提供数据库操作语句的执行能力、脚本运行能力和拼接SQL的能力。
 * 考虑两个问题：
 *  1. 为什么AbstractSQL类中方法名要大写？
 *      为了符合编写SQL时候的习惯
 *  2. 为什么整个包所有类未被外部引用？
 *      那是因为 jdbc包是 MyBatis提供的一个功能独立的工具包，留给用户自行使用而不是由 MyBatis调用。
 */
package org.apache.ibatis.jdbc;
