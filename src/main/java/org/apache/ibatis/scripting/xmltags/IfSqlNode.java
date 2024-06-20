/**
 * Copyright 2009-2017 the original author or authors.
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
 * 解析<if>、<when>标签
 */
public class IfSqlNode implements SqlNode {
    // ExpressionEvaluator底层依赖OGNL表达式，实现解析test属性对应的表达式
    private final ExpressionEvaluator evaluator;
    // 这里记录test属性的表达式，即判断时的测试条件
    private final String test;
    // 如果if成立，要被拼接的SQL判断信息
    private final SqlNode contents;

    public IfSqlNode(SqlNode contents, String test) {
        this.test = test;
        this.contents = contents;
        this.evaluator = new ExpressionEvaluator();
    }

    /**
     * 直接调用解析器解析，条件成立就合并，不成立就不合并
     *
     * @param context 上下文环境，解析结果就合并到这里面
     * @return 解析结果
     */
    @Override
    public boolean apply(DynamicContext context) {
        // 判断解析结果
        if (evaluator.evaluateBoolean(test, context.getBindings())) {
            // 拼接到上下文
            contents.apply(context);
            return true;
        }
        return false;
    }

}
