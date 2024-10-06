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
package org.apache.ibatis.type;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.chrono.JapaneseDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * 类型处理器注册表，管理所有的TypeHandler
 */
public final class TypeHandlerRegistry {
    // jdbc类型和类型处理器TypeHandler的映射
    private final Map<JdbcType, TypeHandler<?>> jdbcTypeHandlerMap = new EnumMap<>(JdbcType.class);
    // Java类型和Map<JdbcType, TypeHandler<?>>的映射
    // 之所以用两层Map嵌套？，因为一个Java类型可能对应多个jdbc类型，如String可能对应char、varchar、text等，针对String类型可能有多种可选的TypeHandler
    private final Map<Type, Map<JdbcType, TypeHandler<?>>> typeHandlerMap = new ConcurrentHashMap<>();
    // 未知的类型处理器
    private final TypeHandler<Object> unknownTypeHandler;
    // 键是typeHandler.getClass()，值是对应的实例，记录了所有TypeHandler
    private final Map<Class<?>, TypeHandler<?>> allTypeHandlersMap = new HashMap<>();

    // 空的jdbc类型到TypeHandler的映射，用于在typeHandlerMap中标识一个Java类型没有对应的类型处理器
    private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = Collections.emptyMap();
    // 默认的枚举类型处理器
    private Class<? extends TypeHandler> defaultEnumTypeHandler = EnumTypeHandler.class;

    /**
     * The default constructor.
     */
    public TypeHandlerRegistry() {
        this(new Configuration());
    }

    /**
     * The constructor that pass the MyBatis configuration.
     *
     * @param configuration a MyBatis configuration
     * @since 3.5.4
     */
    // 构造方法，注册多个TypeHandler对象
    // 可以通过<typeHandlers>标签添加自定义的TypeHandler
    public TypeHandlerRegistry(Configuration configuration) {
        this.unknownTypeHandler = new UnknownTypeHandler(configuration);

        register(Boolean.class, new BooleanTypeHandler());
        register(boolean.class, new BooleanTypeHandler());
        register(JdbcType.BOOLEAN, new BooleanTypeHandler());
        register(JdbcType.BIT, new BooleanTypeHandler());

        register(Byte.class, new ByteTypeHandler());
        register(byte.class, new ByteTypeHandler());
        register(JdbcType.TINYINT, new ByteTypeHandler());

        register(Short.class, new ShortTypeHandler());
        register(short.class, new ShortTypeHandler());
        register(JdbcType.SMALLINT, new ShortTypeHandler());

        register(Integer.class, new IntegerTypeHandler());
        register(int.class, new IntegerTypeHandler());
        register(JdbcType.INTEGER, new IntegerTypeHandler());

        register(Long.class, new LongTypeHandler());
        register(long.class, new LongTypeHandler());

        register(Float.class, new FloatTypeHandler());
        register(float.class, new FloatTypeHandler());
        register(JdbcType.FLOAT, new FloatTypeHandler());

        register(Double.class, new DoubleTypeHandler());
        register(double.class, new DoubleTypeHandler());
        register(JdbcType.DOUBLE, new DoubleTypeHandler());
        register(Reader.class, new ClobReaderTypeHandler());
        // String类型对应的类型处理器
        /*
        在typeHandlerMap中String标签对应的map应该是如下
        {
            "String.class": {
                "JdbcType.CHAR":        StringTypeHandler对象,
                "JdbcType.VARCHAR":     StringTypeHandler对象,
                "JdbcType.LONGVARCHAR": StringTypeHandler对象,
                "JdbcType.CLOB":        ClobTypeHandler对象,
                "JdbcType.NVARCHAR":    NStringTypeHandler对象,
                "JdbcType.NCHAR":       NStringTypeHandler对象,
                "JdbcType.NCLOB":       NClobTypeHandler对象
            }
        }

         */
        // 这一步将这个注册到allTypeHandlersMap中
        register(String.class, new StringTypeHandler());
        // StringTypeHandler可以处理String到char、varchar、longvarchar类型的转换
        register(String.class, JdbcType.CHAR, new StringTypeHandler());
        register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
        register(String.class, JdbcType.LONGVARCHAR, new StringTypeHandler());
        // ClobTypeHandler完成Clob和String的转换
        register(String.class, JdbcType.CLOB, new ClobTypeHandler());
        // NStringTypeHandler完成String和nvarchar、nchar之间的转换
        register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
        register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
        // NClobTypeHandler完成String和NClob中间的转换
        register(String.class, JdbcType.NCLOB, new NClobTypeHandler());


        register(JdbcType.CHAR, new StringTypeHandler());
        register(JdbcType.VARCHAR, new StringTypeHandler());
        register(JdbcType.CLOB, new ClobTypeHandler());
        register(JdbcType.LONGVARCHAR, new StringTypeHandler());
        register(JdbcType.NVARCHAR, new NStringTypeHandler());
        register(JdbcType.NCHAR, new NStringTypeHandler());
        register(JdbcType.NCLOB, new NClobTypeHandler());

        register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
        register(JdbcType.ARRAY, new ArrayTypeHandler());

        register(BigInteger.class, new BigIntegerTypeHandler());
        register(JdbcType.BIGINT, new LongTypeHandler());

        register(BigDecimal.class, new BigDecimalTypeHandler());
        register(JdbcType.REAL, new BigDecimalTypeHandler());
        register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
        register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

        register(InputStream.class, new BlobInputStreamTypeHandler());
        register(Byte[].class, new ByteObjectArrayTypeHandler());
        register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
        register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
        register(byte[].class, new ByteArrayTypeHandler());
        register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
        register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
        register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
        register(JdbcType.BLOB, new BlobTypeHandler());

        register(Object.class, unknownTypeHandler);
        register(Object.class, JdbcType.OTHER, unknownTypeHandler);
        register(JdbcType.OTHER, unknownTypeHandler);

        register(Date.class, new DateTypeHandler());
        register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
        register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
        register(JdbcType.TIMESTAMP, new DateTypeHandler());
        register(JdbcType.DATE, new DateOnlyTypeHandler());
        register(JdbcType.TIME, new TimeOnlyTypeHandler());

        register(java.sql.Date.class, new SqlDateTypeHandler());
        register(java.sql.Time.class, new SqlTimeTypeHandler());
        register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

        register(String.class, JdbcType.SQLXML, new SqlxmlTypeHandler());

        register(Instant.class, new InstantTypeHandler());
        register(LocalDateTime.class, new LocalDateTimeTypeHandler());
        register(LocalDate.class, new LocalDateTypeHandler());
        register(LocalTime.class, new LocalTimeTypeHandler());
        register(OffsetDateTime.class, new OffsetDateTimeTypeHandler());
        register(OffsetTime.class, new OffsetTimeTypeHandler());
        register(ZonedDateTime.class, new ZonedDateTimeTypeHandler());
        register(Month.class, new MonthTypeHandler());
        register(Year.class, new YearTypeHandler());
        register(YearMonth.class, new YearMonthTypeHandler());
        register(JapaneseDate.class, new JapaneseDateTypeHandler());

        // issue #273
        register(Character.class, new CharacterTypeHandler());
        register(char.class, new CharacterTypeHandler());
    }

    /**
     * Set a default {@link TypeHandler} class for {@link Enum}.
     * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
     *
     * @param typeHandler a type handler class for {@link Enum}
     * @since 3.4.5
     */
    public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
        this.defaultEnumTypeHandler = typeHandler;
    }

    public boolean hasTypeHandler(Class<?> javaType) {
        return hasTypeHandler(javaType, null);
    }

    public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
        return hasTypeHandler(javaTypeReference, null);
    }

    public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
        return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
    }

    public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
        return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
    }

    public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
        return allTypeHandlersMap.get(handlerType);
    }

    public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
        return getTypeHandler((Type) type, null);
    }

    public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
        return getTypeHandler(javaTypeReference, null);
    }

    /**
     * 根据jdbc类型获取typeHandler，直接从jdbcTypeHandlerMap取
     *
     * @param jdbcType jdbc类型
     */
    public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
        return jdbcTypeHandlerMap.get(jdbcType);
    }

    public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
        return getTypeHandler((Type) type, jdbcType);
    }

    public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
        return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
    }

    /**
     * 用于根据Java类型获取typeHandler的方法<br/>
     * Java数据类型和JDBC类型往往是一对多，所以处理过程是现根据Java类型获取Map&lt;JdbcType, TypeHandler&lt;?&gt;&gt;对象<br/>
     * 再根据jdbc类型获取对应的TypeHandler对象
     *
     * @param type     Java类型
     * @param jdbcType jdbc类型
     * @return 对应的typeHandler
     */
    @SuppressWarnings("unchecked")
    private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
        // 过滤掉ParamMap类型，因为ParamMap是MyBatis的内置类型，不应该被暴露使用
        if (ParamMap.class.equals(type)) {
            return null;
        }
        // 根据类型获取对应的jdbcTypeHandlerMap，这个方法会检测结合中相应的TypeHandler是否已经初始化
        Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);
        TypeHandler<?> handler = null;
        if (jdbcHandlerMap != null) {
            // 根据jdbc类型查找对应的typeHandler对象
            handler = jdbcHandlerMap.get(jdbcType);
            if (handler == null) {
                // 没有对应的TypeHandler实例，则使用null对应的TypeHandler
                handler = jdbcHandlerMap.get(null);
            }
            if (handler == null) {
                // 如果也没有和null对应的typeHandler，并且jdbcHandlerMap只注册了一个typeHandler，就使用这个。如果不止注册了一个，就返回null
                // #591
                handler = pickSoleHandler(jdbcHandlerMap);
            }
        }
        // type drives generics here
        return (TypeHandler<T>) handler;

    }

    /**
     * 根据Java类型获取对应的TypeHandler的映射表
     *
     * @param type Java类型
     * @return typeHandler映射表
     */
    private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
        Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(type);
        // 如果获取的这个映射表和NULL_TYPE_HANDLER_MAP相等，说明之前进过这个方法了，且没有查到，不需要再来一遍了，直接返回null
        if (NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap)) {
            return null;
        }
        // 初始化指定Java类型的TypeHandler集合
        if (jdbcHandlerMap == null && type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            // 针对枚举类型的处理
            if (Enum.class.isAssignableFrom(clazz)) {
                Class<?> enumClass = clazz.isAnonymousClass() ? clazz.getSuperclass() : clazz;
                jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(enumClass, enumClass);
                if (jdbcHandlerMap == null) {
                    register(enumClass, getInstance(enumClass, defaultEnumTypeHandler));
                    return typeHandlerMap.get(enumClass);
                }
            } else {
                // 查找父类关联的TypeHandler集合，并将其作为clazz对应的TypeHandler集合
                jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
            }
        }
        // 如果上述查找皆失败，则以NULL_TYPE_HANDLER_MAP作为clazz对应的TypeHandler集合
        typeHandlerMap.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
        return jdbcHandlerMap;
    }

    private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz, Class<?> enumClazz) {
        for (Class<?> iface : clazz.getInterfaces()) {
            Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(iface);
            if (jdbcHandlerMap == null) {
                jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface, enumClazz);
            }
            if (jdbcHandlerMap != null) {
                // Found a type handler regsiterd to a super interface
                HashMap<JdbcType, TypeHandler<?>> newMap = new HashMap<>();
                for (Entry<JdbcType, TypeHandler<?>> entry : jdbcHandlerMap.entrySet()) {
                    // Create a type handler instance with enum type as a constructor arg
                    newMap.put(entry.getKey(), getInstance(enumClazz, entry.getValue().getClass()));
                }
                return newMap;
            }
        }
        return null;
    }

    /**
     * 根据父类获取TypeHandler
     *
     * @param clazz 当前类
     * @return 父类对应的TypeHandler
     */
    private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
        Class<?> superclass = clazz.getSuperclass();
        if (superclass == null || Object.class.equals(superclass)) {
            return null;
        }
        Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(superclass);
        if (jdbcHandlerMap != null) {
            return jdbcHandlerMap;
        } else {
            // 递归往上找，直到找到对应的TypeHandler映射表或者找到Object类了
            return getJdbcHandlerMapForSuperclass(superclass);
        }
    }

    private TypeHandler<?> pickSoleHandler(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
        TypeHandler<?> soleHandler = null;
        for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
            if (soleHandler == null) {
                soleHandler = handler;
            } else if (!handler.getClass().equals(soleHandler.getClass())) {
                // More than one type handlers registered.
                return null;
            }
        }
        return soleHandler;
    }

    public TypeHandler<Object> getUnknownTypeHandler() {
        return unknownTypeHandler;
    }

    public void register(JdbcType jdbcType, TypeHandler<?> handler) {
        jdbcTypeHandlerMap.put(jdbcType, handler);
    }

    //
    // REGISTER INSTANCE
    //

    // Only handler

    @SuppressWarnings("unchecked")
    public <T> void register(TypeHandler<T> typeHandler) {
        boolean mappedTypeFound = false;
        MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
        if (mappedTypes != null) {
            for (Class<?> handledType : mappedTypes.value()) {
                register(handledType, typeHandler);
                mappedTypeFound = true;
            }
        }
        // @since 3.1.0 - try to auto-discover the mapped type
        // 从3.1.0版本开始，如果TypeHandler实现类同时继承了TypeReference这个抽象类，会尝试自动查找对应的Java类型
        if (!mappedTypeFound && typeHandler instanceof TypeReference) {
            try {
                TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
                register(typeReference.getRawType(), typeHandler);
                mappedTypeFound = true;
            } catch (Throwable t) {
                // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
            }
        }
        if (!mappedTypeFound) {
            register((Class<T>) null, typeHandler);
        }
    }

    // java type + handler

    public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
        register((Type) javaType, typeHandler);
    }

    /**
     * MyBatis初始化的时候，实例化所有TypeHandler对象后，会调用这个方法注册TypeHandler
     *
     * @param javaType    Java类型
     * @param typeHandler 类型处理器
     */
    private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
        //首先尝试通过@MappedJdbcTypes注解获取TypeHandler能处理的jdbcType
        MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);
        if (mappedJdbcTypes != null) {
            // 如果这个注解存在，直接通过注解的value获取能该TypeHandler能处理的JdbcType
            for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
                register(javaType, handledJdbcType, typeHandler);
            }
            // 判断是否包括null类型的处理，如果包括，即MappedJdbcTypes注解的includeNullJdbcType属性为true，只注册到allTypeHandlersMap
            if (mappedJdbcTypes.includeNullJdbcType()) {
                register(javaType, null, typeHandler);
            }
        } else {
            // 如果没有这个注解，只会将他注册到allTypeHandlersMap中
            register(javaType, null, typeHandler);
        }
    }

    public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
        register(javaTypeReference.getRawType(), handler);
    }

    // java type + jdbc type + handler

    // Cast is required here
    @SuppressWarnings("cast")
    public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
        register((Type) type, jdbcType, handler);
    }

    /**
     * 真正注册的方法
     *
     * @param javaType Java类型
     * @param jdbcType jdbc类型
     * @param handler  typeHandler处理器
     */
    private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
        // 如果没有明确指定这个TypeHandler能处理的Java类型，就不往typeHandlerMap和jdbcTypeHandlerMap里面存
        if (javaType != null) {
            Map<JdbcType, TypeHandler<?>> map = typeHandlerMap.get(javaType);
            if (map == null || map == NULL_TYPE_HANDLER_MAP) {
                map = new HashMap<>();
            }
            // 给这个Java类型对应的map里面添加这个对应关系
            map.put(jdbcType, handler);
            typeHandlerMap.put(javaType, map);
        }
        // 将其放到存储所有typeHandler的映射表里，
        allTypeHandlersMap.put(handler.getClass(), handler);
    }

    //
    // REGISTER CLASS
    //

    // Only handler type

    public void register(Class<?> typeHandlerClass) {
        boolean mappedTypeFound = false;
        // 尝试通过@MappedTypes注解获取该TypeHandler能处理的Java类型
        MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);
        if (mappedTypes != null) {
            for (Class<?> javaTypeClass : mappedTypes.value()) {
                register(javaTypeClass, typeHandlerClass);
                mappedTypeFound = true;
            }
        }
        if (!mappedTypeFound) {
            register(getInstance(null, typeHandlerClass));
        }
    }

    // java type + handler type

    public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
        register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
    }

    public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
        register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass));
    }

    // java type + jdbc type + handler type

    public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
        register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
    }

    // Construct a handler (used also from Builders)

    @SuppressWarnings("unchecked")
    public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
        if (javaTypeClass != null) {
            try {
                Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
                return (TypeHandler<T>) c.newInstance(javaTypeClass);
            } catch (NoSuchMethodException ignored) {
                // ignored
            } catch (Exception e) {
                throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
            }
        }
        try {
            Constructor<?> c = typeHandlerClass.getConstructor();
            return (TypeHandler<T>) c.newInstance();
        } catch (Exception e) {
            throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
        }
    }

    // scan
    // 扫描一个包下所有的TypeHandler的实现类，逐个注册
    public void register(String packageName) {
        ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
        resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);
        Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();
        for (Class<?> type : handlerSet) {
            //Ignore inner classes and interfaces (including package-info.java) and abstract classes
            if (!type.isAnonymousClass() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
                register(type);
            }
        }
    }

    // get information

    /**
     * Gets the type handlers.
     *
     * @return the type handlers
     * @since 3.2.2
     */
    public Collection<TypeHandler<?>> getTypeHandlers() {
        return Collections.unmodifiableCollection(allTypeHandlersMap.values());
    }

}
