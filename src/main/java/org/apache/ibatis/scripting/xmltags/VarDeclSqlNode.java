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
 * @author Frank D. Martinez [mnesarco]
 * 对应bind标签，功能是将一个OGNL表达式的值绑定到一个指定变量名上，并记录到DynamicContext上下文中
 */
public class VarDeclSqlNode implements SqlNode {

    // bind标签的name值
    private final String name;
    // bind标签的value值，一般是一个OGNL表达式
    private final String expression;

    public VarDeclSqlNode(String var, String exp) {
        name = var;
        expression = exp;
    }

    @Override
    public boolean apply(DynamicContext context) {
        // 通过 OGNL 工具类解析 expression 这个表达式的值
        final Object value = OgnlCache.getValue(expression, context.getBindings());
        // 将解析结果与 name 字段的值一起绑定到 DynamicContext 上下文中
        context.bind(name, value);
        return true;
    }

}
