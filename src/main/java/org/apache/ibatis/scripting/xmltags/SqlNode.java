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
 */
package org.apache.ibatis.scripting.xmltags;

/**
 * @author Clinton Begin
 * 处理动态SQL语句的时候，会将动态SQL解析为SQLNode对象，多个SqlNode对象通过组合模式组成树形结构供上层使用
 */
public interface SqlNode {
    // 根据用户传入的实参，解析该SqlNode所表示的动态SQL，将解析后的SQL片段追加到DynamicContext.sqlBuilder中暂存
    // 当所有的动态SQL判断解析完，就可以从DynamicContext.sqlBuilder中得到完整的sql语句
    boolean apply(DynamicContext context);
}
