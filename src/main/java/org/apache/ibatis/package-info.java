/**
 *    Copyright 2009-2015 the original author or authors.
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
 * The MyBatis data mapper framework makes it easier to use a relational database with object-oriented applications.
 * 主要做了两件事情：
 *  1. 通过代理类接管JDBC的Connection、PreparedStatement等类，实现JDBC内类的日志打印
 *  2. 通过一系列适配器接口，提供不同的日志框架的适配。
 */
package org.apache.ibatis;
