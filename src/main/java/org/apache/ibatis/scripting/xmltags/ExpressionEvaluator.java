/**
 * Copyright 2009-2019 the original author or authors.
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

import org.apache.ibatis.builder.BuilderException;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Clinton Begin
 * 对OGNL的封装
 */
public class ExpressionEvaluator {

    /**
     * 对结果为布尔类型的表达式求值
     * 例如，“＜if test="name != null"＞” 的判断就可以直接调用本方法完成
     *
     * @param expression      表达式
     * @param parameterObject 参数对象
     * @return 求值结果
     */
    public boolean evaluateBoolean(String expression, Object parameterObject) {
        // 获取表达式的值
        Object value = OgnlCache.getValue(expression, parameterObject);
        // 如果是布尔型的结果
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        // 如果是数值类型的结果，判断是否等于0，不为0就是true
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value)).compareTo(BigDecimal.ZERO) != 0;
        }
        // 其他类型的情况下，不为null就是true
        return value != null;
    }

    /**
     * 该方法对结果为迭代形式的表达式求值
     * 例如，＜foreach item="id" collection="array" open="（" separator="，"close="）"＞＃{id} ＜/foreach＞ 就通过调用该方法完成
     *
     * @param expression      表达式
     * @param parameterObject 参数对象
     * @return 求值结果
     */
    public Iterable<?> evaluateIterable(String expression, Object parameterObject) {
        // 获取表达式的结果
        Object value = OgnlCache.getValue(expression, parameterObject);
        if (value == null) {
            throw new BuilderException("The expression '" + expression + "' evaluated to a null value.");
        }
        // 如果结果是Iterable的实现，直接返回
        if (value instanceof Iterable) {
            return (Iterable<?>) value;
        }
        // 如果结果是数组
        if (value.getClass().isArray()) {
            // the array may be primitive, so Arrays.asList() may throw
            // a ClassCastException (issue 209).  Do the work manually
            // Curse primitives! :) (JGB)
            // 这个数组可能是基本类型，调用Arrays.asList()可能抛出异常。手动转为ArrayList
            int size = Array.getLength(value);
            List<Object> answer = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                Object o = Array.get(value, i);
                answer.add(o);
            }
            return answer;
        }
        // 如果结果是Map，强转一下直接返回
        if (value instanceof Map) {
            return ((Map) value).entrySet();
        }
        throw new BuilderException("Error evaluating expression '" + expression + "'.  Return value (" + value + ") was not iterable.");
    }
}
