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
package org.apache.ibatis.builder.annotation;

import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class ProviderSqlSource implements SqlSource {

    // 全局配置对象
    private final Configuration configuration;
    // @XxxProvider注解的type属性指向的类型
    private final Class<?> providerType;
    // 语言驱动
    private final LanguageDriver languageDriver;
    // 被该注解修饰该注解的接口方法
    private final Method mapperMethod;
    // 注解的method属性指向的方法
    private final Method providerMethod;
    // 给定的SQL语句方法对应的参数
    private final String[] providerMethodArgumentNames;
    // 给定的SQL语句方法对应的参数的类型
    private final Class<?>[] providerMethodParameterTypes;
    // 工具类
    private final ProviderContext providerContext;
    // ProviderContext编号
    private final Integer providerContextIndex;

    /**
     * This constructor will remove at a future version.
     *
     * @param configuration the configuration
     * @param provider      the provider
     * @deprecated Since 3.5.3, Please use the {@link #ProviderSqlSource(Configuration, Annotation, Class, Method)}
     * instead of this.
     */
    @Deprecated
    public ProviderSqlSource(Configuration configuration, Object provider) {
        this(configuration, provider, null, null);
    }

    /**
     * This constructor will remove at a future version.
     *
     * @param configuration the configuration
     * @param provider      the provider
     * @param mapperType    the mapper type
     * @param mapperMethod  the mapper method
     * @since 3.4.5
     * @deprecated Since 3.5.3, Please use the {@link #ProviderSqlSource(Configuration, Annotation, Class, Method)} instead of this.
     */
    @Deprecated
    public ProviderSqlSource(Configuration configuration, Object provider, Class<?> mapperType, Method mapperMethod) {
        this(configuration, (Annotation) provider, mapperType, mapperMethod);
    }

    /**
     * Instantiates a new provider sql source.
     * 处理间接注解映射的方法。内部通过{@link ProviderContext} 和 {@link ProviderMethodResolver} 协助
     *
     * @param configuration the configuration
     * @param provider      the provider
     * @param mapperType    the mapper type
     * @param mapperMethod  the mapper method
     * @since 3.5.3
     */
    public ProviderSqlSource(Configuration configuration, Annotation provider, Class<?> mapperType, Method mapperMethod) {
        //
        String candidateProviderMethodName;
        Method candidateProviderMethod = null;
        try {
            this.configuration = configuration;
            this.mapperMethod = mapperMethod;
            Lang lang = mapperMethod == null ? null : mapperMethod.getAnnotation(Lang.class);
            this.languageDriver = configuration.getLanguageDriver(lang == null ? null : lang.value());
            this.providerType = getProviderType(configuration, provider, mapperMethod);
            candidateProviderMethodName = (String) provider.annotationType().getMethod("method").invoke(provider);

            if (candidateProviderMethodName.length() == 0 && ProviderMethodResolver.class.isAssignableFrom(this.providerType)) {
                candidateProviderMethod = ((ProviderMethodResolver) this.providerType.getDeclaredConstructor().newInstance())
                        .resolveMethod(new ProviderContext(mapperType, mapperMethod, configuration.getDatabaseId()));
            }
            if (candidateProviderMethod == null) {
                candidateProviderMethodName = candidateProviderMethodName.length() == 0 ? "provideSql" : candidateProviderMethodName;
                for (Method m : this.providerType.getMethods()) {
                    if (candidateProviderMethodName.equals(m.getName()) && CharSequence.class.isAssignableFrom(m.getReturnType())) {
                        if (candidateProviderMethod != null) {
                            throw new BuilderException("Error creating SqlSource for SqlProvider. Method '"
                                    + candidateProviderMethodName + "' is found multiple in SqlProvider '" + this.providerType.getName()
                                    + "'. Sql provider method can not overload.");
                        }
                        candidateProviderMethod = m;
                    }
                }
            }
        } catch (BuilderException e) {
            throw e;
        } catch (Exception e) {
            throw new BuilderException("Error creating SqlSource for SqlProvider.  Cause: " + e, e);
        }
        if (candidateProviderMethod == null) {
            throw new BuilderException("Error creating SqlSource for SqlProvider. Method '"
                    + candidateProviderMethodName + "' not found in SqlProvider '" + this.providerType.getName() + "'.");
        }
        this.providerMethod = candidateProviderMethod;
        this.providerMethodArgumentNames = new ParamNameResolver(configuration, this.providerMethod).getNames();
        this.providerMethodParameterTypes = this.providerMethod.getParameterTypes();

        ProviderContext candidateProviderContext = null;
        Integer candidateProviderContextIndex = null;
        for (int i = 0; i < this.providerMethodParameterTypes.length; i++) {
            Class<?> parameterType = this.providerMethodParameterTypes[i];
            if (parameterType == ProviderContext.class) {
                if (candidateProviderContext != null) {
                    throw new BuilderException("Error creating SqlSource for SqlProvider. ProviderContext found multiple in SqlProvider method ("
                            + this.providerType.getName() + "." + providerMethod.getName()
                            + "). ProviderContext can not define multiple in SqlProvider method argument.");
                }
                candidateProviderContext = new ProviderContext(mapperType, mapperMethod, configuration.getDatabaseId());
                candidateProviderContextIndex = i;
            }
        }
        this.providerContext = candidateProviderContext;
        this.providerContextIndex = candidateProviderContextIndex;
    }

    /**
     * 获取一个BoundSql对象
     *
     * @param parameterObject 参数对象
     * @return BoundSql对象
     */
    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        // 获取SqlSource对象
        SqlSource sqlSource = createSqlSource(parameterObject);
        // 从SqlSource对象获取BoundSql对象
        return sqlSource.getBoundSql(parameterObject);
    }

    /**
     * 获取一个SqlSource对象
     *
     * @param parameterObject 参数对象
     * @return 创建的SqlSource对象
     */
    private SqlSource createSqlSource(Object parameterObject) {
        try {
            // 表示SQL的字符串信息
            String sql;
            // 参数对象是Map
            if (parameterObject instanceof Map) {
                int bindParameterCount = providerMethodParameterTypes.length - (providerContext == null ? 0 : 1);
                if (bindParameterCount == 1
                        && providerMethodParameterTypes[Integer.valueOf(0).equals(providerContextIndex) ? 1 : 0].isAssignableFrom(parameterObject.getClass())) {
                    sql = invokeProviderMethod(extractProviderMethodArguments(parameterObject));
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) parameterObject;
                    // 调用@XxxProvider注解的type类的method方法，获得该方法产出的表示Sql的字符串
                    sql = invokeProviderMethod(extractProviderMethodArguments(params, providerMethodArgumentNames));
                }
                // 根据@XxxProvider注解的type和method属性指向的方法是否需要参数
            } else if (providerMethodParameterTypes.length == 0) {
                sql = invokeProviderMethod();
            } else if (providerMethodParameterTypes.length == 1) {
                if (providerContext == null) {
                    sql = invokeProviderMethod(parameterObject);
                } else {
                    sql = invokeProviderMethod(providerContext);
                }
            } else if (providerMethodParameterTypes.length == 2) {
                sql = invokeProviderMethod(extractProviderMethodArguments(parameterObject));
            } else {
                throw new BuilderException("Cannot invoke SqlProvider method '" + providerMethod
                        + "' with specify parameter '" + (parameterObject == null ? null : parameterObject.getClass())
                        + "' because SqlProvider method arguments for '" + mapperMethod + "' is an invalid combination.");
            }
            Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
            // 通过languageDriver来生成SqlSource对象
            return languageDriver.createSqlSource(configuration, sql, parameterType);
        } catch (BuilderException e) {
            throw e;
        } catch (Exception e) {
            throw new BuilderException("Error invoking SqlProvider method '" + providerMethod
                    + "' with specify parameter '" + (parameterObject == null ? null : parameterObject.getClass()) + "'.  Cause: " + extractRootCause(e), e);
        }
    }

    private Throwable extractRootCause(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private Object[] extractProviderMethodArguments(Object parameterObject) {
        if (providerContext != null) {
            Object[] args = new Object[2];
            args[providerContextIndex == 0 ? 1 : 0] = parameterObject;
            args[providerContextIndex] = providerContext;
            return args;
        } else {
            return new Object[]{parameterObject};
        }
    }

    private Object[] extractProviderMethodArguments(Map<String, Object> params, String[] argumentNames) {
        Object[] args = new Object[argumentNames.length];
        for (int i = 0; i < args.length; i++) {
            if (providerContextIndex != null && providerContextIndex == i) {
                args[i] = providerContext;
            } else {
                args[i] = params.get(argumentNames[i]);
            }
        }
        return args;
    }

    private String invokeProviderMethod(Object... args) throws Exception {
        Object targetObject = null;
        if (!Modifier.isStatic(providerMethod.getModifiers())) {
            targetObject = providerType.getDeclaredConstructor().newInstance();
        }
        CharSequence sql = (CharSequence) providerMethod.invoke(targetObject, args);
        return sql != null ? sql.toString() : null;
    }

    private Class<?> getProviderType(Configuration configuration, Annotation providerAnnotation, Method mapperMethod)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> type = (Class<?>) providerAnnotation.annotationType().getMethod("type").invoke(providerAnnotation);
        Class<?> value = (Class<?>) providerAnnotation.annotationType().getMethod("value").invoke(providerAnnotation);
        if (value == void.class && type == void.class) {
            if (configuration.getDefaultSqlProviderType() != null) {
                return configuration.getDefaultSqlProviderType();
            }
            throw new BuilderException("Please specify either 'value' or 'type' attribute of @"
                    + providerAnnotation.annotationType().getSimpleName()
                    + " at the '" + mapperMethod.toString() + "'.");
        }
        if (value != void.class && type != void.class && value != type) {
            throw new BuilderException("Cannot specify different class on 'value' and 'type' attribute of @"
                    + providerAnnotation.annotationType().getSimpleName()
                    + " at the '" + mapperMethod.toString() + "'.");
        }
        return value == void.class ? type : value;
    }

}
