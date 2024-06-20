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
 */
package org.apache.ibatis.scripting.xmltags;

import ognl.Ognl;
import ognl.OgnlException;
import org.apache.ibatis.builder.BuilderException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches OGNL parsed expressions.
 *
 * @author Eduardo Macarron
 * @see <a href='https://github.com/mybatis/old-google-code-issues/issues/342'>Issue 342</a>
 * 为提示OGNL的运行效率，提供的缓存
 */
public final class OgnlCache {

    // Mybatis提供的OgnlMemberAccess
    private static final OgnlMemberAccess MEMBER_ACCESS = new OgnlMemberAccess();
    // Mybatis提供的的OgnlClassResolver
    private static final OgnlClassResolver CLASS_RESOLVER = new OgnlClassResolver();
    // 缓存解析后的OGNL表达式，用于提高效率
    private static final Map<String, Object> expressionCache = new ConcurrentHashMap<>();

    private OgnlCache() {
        // Prevent Instantiation of Static Class
    }

    /**
     * 读取表达式的查询结果
     *
     * @param expression 表达式
     * @param root       根环境
     * @return 查询结果
     */
    public static Object getValue(String expression, Object root) {
        try {
            // 创建默认的上下文环境
            Map context = Ognl.createDefaultContext(root, MEMBER_ACCESS, CLASS_RESOLVER, null);
            // 依次传入表达式树、上下文、根，获取表达式查询结果
            return Ognl.getValue(parseExpression(expression), context, root);
        } catch (OgnlException e) {
            throw new BuilderException("Error evaluating expression '" + expression + "'. Cause: " + e, e);
        }
    }

    /**
     * 解析表达式，获得解析后的表达式树
     *
     * @param expression 表达式
     * @return 解析到的表达式树
     */
    private static Object parseExpression(String expression) throws OgnlException {
        // 先尝试从缓存中获取
        Object node = expressionCache.get(expression);
        // 缓存中没有，就解析，然后存入缓存
        if (node == null) {
            node = Ognl.parseExpression(expression);
            expressionCache.put(expression, node);
        }
        return node;
    }

}
