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

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author Clinton Begin
 * 在解析SQL节点树的过程中保存解析完成的SQL片段和保存解析过程中 一些参数和环境信息
 */
public class DynamicContext {

    public static final String PARAMETER_OBJECT_KEY = "_parameter";
    public static final String DATABASE_ID_KEY = "_databaseId";

    static {
        // 这里是OGNL表达式读写ContextMap
        OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
    }

    // 上下文环境
    private final ContextMap bindings;
    // 用来记录解析完成的语句片段
    private final StringJoiner sqlBuilder = new StringJoiner(" ");
    // 解析时的唯一标识，防止解析混乱
    private int uniqueNumber = 0;

    /**
     * 构造方法
     *
     * @param configuration   全局配置信息
     * @param parameterObject 传入的查询参数对象
     */
    public DynamicContext(Configuration configuration, Object parameterObject) {
        // 这里会根据传入进来的用来替换#{}的实参类型创建对应的ContextMap对象
        // 对于不是Map类型的实参，创建对象的MetaObject对象，封装成ContextMap。基于参数对象的元数据可以方便地引用参数对象的属性值，让我们在编写SQL语句的时候可以直接引用参数对象的属性
        if (parameterObject != null && !(parameterObject instanceof Map)) {
            // 获取参数对象的元对象
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            // 判断参数对象本身是否有对应的类型处理器
            boolean existsTypeHandler = configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass());
            // 存入上下文信息
            bindings = new ContextMap(metaObject, existsTypeHandler);
        } else {
            // 对于Map类型的实参，创建一个空的ContextMap对象
            bindings = new ContextMap(null, false);
        }
        //  参数对象放入上下文信息，对应的key是_parameter。因此在编写sql语句时，可以直接使用PARAMETER_OBJECT_KEY引用整个参数对象
        bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
        // 数据库id放入上下文信息。因此在编写SQL语句时，我们可以直接使用DATABASE_ID_KEY变量引用数据库id
        bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
    }

    public Map<String, Object> getBindings() {
        return bindings;
    }

    public void bind(String name, Object value) {
        bindings.put(name, value);
    }

    public void appendSql(String sql) {
        sqlBuilder.add(sql);
    }

    public String getSql() {
        return sqlBuilder.toString().trim();
    }

    public int getUniqueNumber() {
        return uniqueNumber++;
    }

    /**
     * 这个类是用来记录用户传入的，用来替换#{}占位符的实参
     * 该子类对HashMap的get方法进行了改造
     */
    static class ContextMap extends HashMap<String, Object> {
        private static final long serialVersionUID = 2977601501966151582L;
        private final MetaObject parameterMetaObject;
        private final boolean fallbackParameterObject;

        public ContextMap(MetaObject parameterMetaObject, boolean fallbackParameterObject) {
            this.parameterMetaObject = parameterMetaObject;
            this.fallbackParameterObject = fallbackParameterObject;
        }

        /**
         * 重写的get方法，如果从map中没有查到，尝试从参数对象的元对象查。所以我们在编写SQL的时候可以直接使用实参的属性名获取值。
         *
         * @param key 键
         * @return 查询结果
         */
        @Override
        public Object get(Object key) {
            String strKey = (String) key;
            // 如果有对应的key，直接返回
            if (super.containsKey(strKey)) {
                return super.get(strKey);
            }

            // 如果没有对应的key，尝试从参数对象的元对象中获取
            if (parameterMetaObject == null) {
                return null;
            }

            if (fallbackParameterObject && !parameterMetaObject.hasGetter(strKey)) {
                return parameterMetaObject.getOriginalObject();
            } else {
                // issue #61 do not modify the context when reading
                return parameterMetaObject.getValue(strKey);
            }
        }
    }

    static class ContextAccessor implements PropertyAccessor {

        @Override
        public Object getProperty(Map context, Object target, Object name) {
            Map map = (Map) target;

            Object result = map.get(name);
            if (map.containsKey(name) || result != null) {
                return result;
            }

            Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
            if (parameterObject instanceof Map) {
                return ((Map) parameterObject).get(name);
            }

            return null;
        }

        @Override
        public void setProperty(Map context, Object target, Object name, Object value) {
            Map<Object, Object> map = (Map<Object, Object>) target;
            map.put(name, value);
        }

        @Override
        public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
            return null;
        }

        @Override
        public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
            return null;
        }
    }
}
