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
 * Parses XML files to create a Configuration.
 * 本包负责解析xml文件
 * XMLConfigBuilder解析配置文件。XMLMapperEntityResolver解析配置文件头、映射文件头
 * XMLMapperBuilder解析映射文件。XMLMapperEntityResolver解析映射文件头。XMlStatementBuilder解析映射文件体（SQL语句等）。XMLIncludeTransformer解析include标签
 */
package org.apache.ibatis.builder.xml;
