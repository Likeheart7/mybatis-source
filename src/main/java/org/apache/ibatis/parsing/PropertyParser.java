/**
 * Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * 将XMl中的${XXXX} 替换成properties节点对应的值
 */
public class PropertyParser {

    private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
    /**
     * The special property key that indicate whether enable a default value on placeholder.
     * <p>
     * The default value is {@code false} (indicate disable a default value on placeholder)
     * If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
     * </p>
     *
     * @since 3.4.2
     */
    public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

    /**
     * The special property key that specify a separator for key and default value on placeholder.
     * <p>
     * The default separator is {@code ":"}.
     * </p>
     *
     * @since 3.4.2
     */
    public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

    private static final String ENABLE_DEFAULT_VALUE = "false";
    private static final String DEFAULT_VALUE_SEPARATOR = ":";

    private PropertyParser() {
        // Prevent Instantiation
    }

    /**
     * 进行字符串指定属性变量的替换
     *
     * @param string    输入的字符串
     * @param variables 属性映射表
     * @return 经过属性变量替换的字符串
     */
    public static String parse(String string, Properties variables) {
        // 负责字符串替换的类
        VariableTokenHandler handler = new VariableTokenHandler(variables);
        // 负责占位符解析、定位的类
        GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
        // 内部会先定位，在通过调用handler.handleToken()替换占位符的值
        return parser.parse(string);
    }

    private static class VariableTokenHandler implements TokenHandler {
        // 输入的属性变量，Properties是Hashtable的子类
        private final Properties variables;
        // 是否启用默认值
        private final boolean enableDefaultValue;
        // 如果启用默认值，则表示键和默认值之间的分隔符
        private final String defaultValueSeparator;

        private VariableTokenHandler(Properties variables) {
            this.variables = variables;
            this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
            this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
        }

        private String getPropertyValue(String key, String defaultValue) {
            return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
        }

        /**
         * 父接口 TokenHandler的方法实现
         * 以传入的字符串作为键，从variables中找出返回对应的值
         * 由键寻值的过程允许设置默认值
         * 如果启用默认值，则传入的字符串content形如key:defaultValue
         * 如果没有启用默认值，就只有key
         *
         * @param content 输入的字符串
         * @return 输出的字符串
         */
        @Override
        public String handleToken(String content) {
            if (variables != null) {
                String key = content;
                // 如果启用了默认值
                if (enableDefaultValue) {
                    // 默认分隔符是 :
                    final int separatorIndex = content.indexOf(defaultValueSeparator);
                    String defaultValue = null;
                    if (separatorIndex >= 0) {
                        // 根据分隔符拆分出 key 和 defaultValue
                        key = content.substring(0, separatorIndex);
                        defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
                    }
                    if (defaultValue != null) {
                        return variables.getProperty(key, defaultValue);
                    }
                }
                if (variables.containsKey(key)) {
                    // 尝试获取键对应的值
                    return variables.getProperty(key);
                }
            }
            // 如果variables是null，直接返回
            return "${" + content + "}";
        }
    }

}
