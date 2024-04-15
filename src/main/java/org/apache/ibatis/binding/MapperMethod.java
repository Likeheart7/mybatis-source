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
package org.apache.ibatis.binding;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 * 最终执行SQL的地方。
 * 这里记录了Mapper接口中的对应方法
 */
public class MapperMethod {
    // 维护了关联的SQL语句信息
    private final SqlCommand command;
    // 维护了Mapper接口中方法的相关关系
    private final MethodSignature method;

    public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
        this.command = new SqlCommand(config, mapperInterface, method);
        this.method = new MethodSignature(config, mapperInterface, method);
    }

    /**
     * 核心方法：根据要执行的 SQL 语句的具体类型执行 SqlSession 的相应方法完成数据库操作
     *
     * @param sqlSession sqlSession对象，通过它进行SQL操作
     * @param args       执行接口方法时要传入的参数
     * @return 数据库操作结果
     */
    public Object execute(SqlSession sqlSession, Object[] args) {
        Object result;
        // 根据数据库操作类型来决定处理方式（insert、update、delete、select、flush五种）
        // 对于insert、update、delete语句，一般返回结果都是个数字，表示影响的行数（可能根据配置有不同）
        switch (command.getType()) {
            case INSERT: {
                // 通过参数名称处理器的getNamedParams方法将实参和参数名称关联起来
                Object param = method.convertArgsToSqlCommandParam(args);
                // 通过sqlSession的insert来执行数据库操作
                // rowCountResult会对返回结果进行转换
                result = rowCountResult(sqlSession.insert(command.getName(), param));
                break;
            }
            case UPDATE: {
                Object param = method.convertArgsToSqlCommandParam(args);
                // 通过sqlSession的update方法执行sql
                result = rowCountResult(sqlSession.update(command.getName(), param));
                break;
            }
            case DELETE: {
                Object param = method.convertArgsToSqlCommandParam(args);
                // 通过sqlSession的delete方法操作
                result = rowCountResult(sqlSession.delete(command.getName(), param));
                break;
            }
            case SELECT:
                // 返回值类型为空且有ResultHandler类型的参数
                if (method.returnsVoid() && method.hasResultHandler()) {
                    // 内部用了sqlSession.select()，然后浇给ResultHandler处理
                    executeWithResultHandler(sqlSession, args);
                    result = null;
                    // 返回值时集合的情况
                } else if (method.returnsMany()) {
                    // 底层是selectList()
                    result = executeForMany(sqlSession, args);
                    // 返回值时map
                } else if (method.returnsMap()) {
                    // 底层是selectMap()
                    result = executeForMap(sqlSession, args);
                    // 返回游标的情况
                } else if (method.returnsCursor()) {
                    result = executeForCursor(sqlSession, args);
                } else {
                    // 返回一个对象的情况
                    Object param = method.convertArgsToSqlCommandParam(args);
                    result = sqlSession.selectOne(command.getName(), param);
                    if (method.returnsOptional()
                            && (result == null || !method.getReturnType().equals(result.getClass()))) {
                        result = Optional.ofNullable(result);
                    }
                }
                break;
            // flush就是单纯的提交缓存语句
            case FLUSH:
                result = sqlSession.flushStatements();
                break;
            // 如果是其他的就抛出异常
            default:
                throw new BindingException("Unknown execution method for: " + command.getName());
        }
        // 查询结果是null，返回类型是基本类型，并且不是void，返回变量不能接收查询结果，抛出异常
        if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
            throw new BindingException("Mapper method '" + command.getName()
                    + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
        }
        return result;
    }

    /**
     * 处理返回值是行数的情况
     *
     * @param rowCount 返回值数据
     * @return 转换后的mapper接口返回值
     */
    private Object rowCountResult(int rowCount) {
        final Object result;
        // 如果mapper返回值是void，就给null
        if (method.returnsVoid()) {
            result = null;
            // 返回值类型是Integer、int，直接返回rowCount
        } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
            result = rowCount;
            // 返回值是long、Long，强转为long
        } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
            result = (long) rowCount;
            // 返回值是布尔值，则大于0的都是true， 小于等于的都是false
        } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
            result = rowCount > 0;
            // 其他的抛出异常
        } else {
            throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
        }
        return result;
    }

    private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
        MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
        if (!StatementType.CALLABLE.equals(ms.getStatementType())
                && void.class.equals(ms.getResultMaps().get(0).getType())) {
            throw new BindingException("method " + command.getName()
                    + " needs either a @ResultMap annotation, a @ResultType annotation,"
                    + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
        }
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
        } else {
            sqlSession.select(command.getName(), param, method.extractResultHandler(args));
        }
    }

    private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
        List<E> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.selectList(command.getName(), param, rowBounds);
        } else {
            result = sqlSession.selectList(command.getName(), param);
        }
        // issue #510 Collections & arrays support
        if (!method.getReturnType().isAssignableFrom(result.getClass())) {
            if (method.getReturnType().isArray()) {
                return convertToArray(result);
            } else {
                return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
            }
        }
        return result;
    }

    private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
        Cursor<T> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.selectCursor(command.getName(), param, rowBounds);
        } else {
            result = sqlSession.selectCursor(command.getName(), param);
        }
        return result;
    }

    private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
        Object collection = config.getObjectFactory().create(method.getReturnType());
        MetaObject metaObject = config.newMetaObject(collection);
        metaObject.addAll(list);
        return collection;
    }

    @SuppressWarnings("unchecked")
    private <E> Object convertToArray(List<E> list) {
        Class<?> arrayComponentType = method.getReturnType().getComponentType();
        Object array = Array.newInstance(arrayComponentType, list.size());
        if (arrayComponentType.isPrimitive()) {
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        } else {
            return list.toArray((E[]) array);
        }
    }

    private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
        Map<K, V> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
        } else {
            result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
        }
        return result;
    }

    /**
     * HashMap的子类，只是重写了get方法让其在不包括某键时直接抛出异常，而不是返回null
     *
     * @param <V>
     */
    public static class ParamMap<V> extends HashMap<String, V> {

        private static final long serialVersionUID = -2212268410512043556L;

        @Override
        public V get(Object key) {
            if (!super.containsKey(key)) {
                throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
            }
            return super.get(key);
        }

    }

    public static class SqlCommand {
        // 关联的SQL语句的唯一标识
        private final String name;
        // SQL语句的操作类型（Insert、update、delete、select、flush）
        private final SqlCommandType type;

        // 这里实现了查找Mapper接口中一个方法对应的SQL信息
        public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
            final String methodName = method.getName();
            final Class<?> declaringClass = method.getDeclaringClass();

            // 核心逻辑
            MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
                    configuration);
            if (ms == null) {
                // 针对@flush的处理
                if (method.getAnnotation(Flush.class) != null) {
                    name = null;
                    type = SqlCommandType.FLUSH;
                } else { // 如果没有@Flush注解的处理
                    throw new BindingException("Invalid bound statement (not found): "
                            + mapperInterface.getName() + "." + methodName);
                }
            } else {
                // 记录SQL的唯一标识
                name = ms.getId();
                // 记录SQL语句的操作类型
                type = ms.getSqlCommandType();
                if (type == SqlCommandType.UNKNOWN) {
                    throw new BindingException("Unknown execution method for: " + name);
                }
            }
        }

        public String getName() {
            return name;
        }

        public SqlCommandType getType() {
            return type;
        }

        private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
                                                       Class<?> declaringClass, Configuration configuration) {
            // 接口名称 + “. “ + 方法名 作为标识
            String statementId = mapperInterface.getName() + "." + methodName;
            // 从Configuration配置对象中查询SQL语句
            if (configuration.hasStatement(statementId)) {
                return configuration.getMappedStatement(statementId);
            } else if (mapperInterface.equals(declaringClass)) {
                return null;
            }
            // 如果当前检擦的Mapper接口(mapperInterface)不是定义该方法的接口
            for (Class<?> superInterface : mapperInterface.getInterfaces()) {
                if (declaringClass.isAssignableFrom(superInterface)) {
                    // 尝试顺着 Mapper 接口的继承树进行查找，直至查找成功为止
                    MappedStatement ms = resolveMappedStatement(superInterface, methodName,
                            declaringClass, configuration);
                    if (ms != null) {
                        return ms;
                    }
                }
            }
            return null;
        }
    }

    public static class MethodSignature {

        // 下面七个是方法的返回值信息

        private final boolean returnsMany;  // 是否是集合/数组
        private final boolean returnsMap;   // 是否是map
        private final boolean returnsVoid;  // 是否为void
        private final boolean returnsCursor;    //是否为cursor，当返回结果集很大的时候会用，防止内存占用过高
        private final boolean returnsOptional;  // 是否为Optional
        private final Class<?> returnType;  // 具体的返回类型
        private final String mapKey;    //如果返回值是map，该字段记录作为key的列名

        // 与Mapper接口方法参数列表相关的字段
        private final Integer resultHandlerIndex; // 记录参数列表中ResultHandler类型的参数位置
        private final Integer rowBoundsIndex;   // 记录参数列表中RowBounds参数的位置
        private final ParamNameResolver paramNameResolver;  // 解释方法参数列表的工具类

        /**
         * 构造方法里会解析方法的返回值、参数列表等
         *
         * @param configuration   全局配置信息
         * @param mapperInterface 接口
         * @param method          方法
         */
        public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
            // 获取方法的返回值类型
            Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
            // 普通类型直接返回
            if (resolvedReturnType instanceof Class<?>) {
                this.returnType = (Class<?>) resolvedReturnType;
                // 泛型类型获取其原始类型，即List<String>返回List.class
            } else if (resolvedReturnType instanceof ParameterizedType) {
                this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
                // 其他的直接返回方法的返回类型
            } else {
                this.returnType = method.getReturnType();
            }

            // 根据上面获取的返回值类型来为成员变量赋值
            this.returnsVoid = void.class.equals(this.returnType);
            this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
            this.returnsCursor = Cursor.class.equals(this.returnType);
            this.returnsOptional = Optional.class.equals(this.returnType);
            this.mapKey = getMapKey(method);
            this.returnsMap = this.mapKey != null;
            this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
            this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
            // 创建ParamNameResolver参数名称解析器，构造时会解析方法的参数列表
            this.paramNameResolver = new ParamNameResolver(configuration, method);
        }

        public Object convertArgsToSqlCommandParam(Object[] args) {
            return paramNameResolver.getNamedParams(args);
        }

        public boolean hasRowBounds() {
            return rowBoundsIndex != null;
        }

        public RowBounds extractRowBounds(Object[] args) {
            return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
        }

        public boolean hasResultHandler() {
            return resultHandlerIndex != null;
        }

        public ResultHandler extractResultHandler(Object[] args) {
            return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        public boolean returnsMany() {
            return returnsMany;
        }

        public boolean returnsMap() {
            return returnsMap;
        }

        public boolean returnsVoid() {
            return returnsVoid;
        }

        public boolean returnsCursor() {
            return returnsCursor;
        }

        /**
         * return whether return type is {@code java.util.Optional}.
         *
         * @return return {@code true}, if return type is {@code java.util.Optional}
         * @since 3.5.0
         */
        public boolean returnsOptional() {
            return returnsOptional;
        }

        private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
            Integer index = null;
            final Class<?>[] argTypes = method.getParameterTypes();
            for (int i = 0; i < argTypes.length; i++) {
                if (paramType.isAssignableFrom(argTypes[i])) {
                    if (index == null) {
                        index = i;
                    } else {
                        throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
                    }
                }
            }
            return index;
        }

        public String getMapKey() {
            return mapKey;
        }

        private String getMapKey(Method method) {
            String mapKey = null;
            if (Map.class.isAssignableFrom(method.getReturnType())) {
                final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
                if (mapKeyAnnotation != null) {
                    mapKey = mapKeyAnnotation.value();
                }
            }
            return mapKey;
        }
    }

}
