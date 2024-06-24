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
package org.apache.ibatis.executor.loader;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BaseExecutor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Clinton Begin
 * @author Franta Mejta
 * 负责管理尚未触发加载的懒加载属性。
 */
public class ResultLoaderMap {

    //存储懒加载属性的map，键为属性名称大写
    private final Map<String, LoadPair> loaderMap = new HashMap<>();

    public void addLoader(String property, MetaObject metaResultObject, ResultLoader resultLoader) {
        String upperFirst = getUppercaseFirstProperty(property);
        if (!upperFirst.equalsIgnoreCase(property) && loaderMap.containsKey(upperFirst)) {
            throw new ExecutorException("Nested lazy loaded result property '" + property
                    + "' for query id '" + resultLoader.mappedStatement.getId()
                    + " already exists in the result map. The leftmost property of all lazy loaded properties must be unique within a result map.");
        }
        loaderMap.put(upperFirst, new LoadPair(property, metaResultObject, resultLoader));
    }

    public final Map<String, LoadPair> getProperties() {
        return new HashMap<>(this.loaderMap);
    }

    public Set<String> getPropertyNames() {
        return loaderMap.keySet();
    }

    public int size() {
        return loaderMap.size();
    }

    public boolean hasLoader(String property) {
        return loaderMap.containsKey(property.toUpperCase(Locale.ENGLISH));
    }

    public boolean load(String property) throws SQLException {
        LoadPair pair = loaderMap.remove(property.toUpperCase(Locale.ENGLISH));
        if (pair != null) {
            pair.load();
            return true;
        }
        return false;
    }

    public void remove(String property) {
        loaderMap.remove(property.toUpperCase(Locale.ENGLISH));
    }

    public void loadAll() throws SQLException {
        final Set<String> methodNameSet = loaderMap.keySet();
        String[] methodNames = methodNameSet.toArray(new String[methodNameSet.size()]);
        for (String methodName : methodNames) {
            load(methodName);
        }
    }

    private static String getUppercaseFirstProperty(String property) {
        String[] parts = property.split("\\.");
        return parts[0].toUpperCase(Locale.ENGLISH);
    }

    /**
     * Property which was not loaded yet.
     * 未加载的属性，本类实现属性加载功能
     */
    public static class LoadPair implements Serializable {

        private static final long serialVersionUID = 20130412;
        /**
         * Name of factory method which returns database connection.
         * 得到数据库连接的方法名，用来通过反射获取数据库连接
         */
        private static final String FACTORY_METHOD = "getConfiguration";
        /**
         * Object to check whether we went through serialization..
         * 判断是否经过序列化的标志位，因为被transient修饰，所以经过序列化+反序列化后，该属性会变为null
         */
        private final transient Object serializationCheck = new Object();
        /**
         * Meta object which sets loaded properties.
         * 输出结果对象的封装
         */
        private transient MetaObject metaResultObject;
        /**
         * Result loader which loads unread properties.
         * 用来加载未加载属性的加载器
         */
        private transient ResultLoader resultLoader;
        /**
         * Wow, logger.
         * 日志记录器
         */
        private transient Log log;
        /**
         * Factory class through which we get database connection.
         * 用来获取数据库连接的工厂
         */
        private Class<?> configurationFactory;
        /**
         * Name of the unread property.
         * 未加载属性的属性名
         */
        private String property;
        /**
         * ID of SQL statement which loads the property.
         * 能够加载未加载属性的SQL语句的编号
         */
        private String mappedStatement;
        /**
         * Parameter of the sql statement.
         * 能够加载未加载属性的sql语句的参数
         */
        private Serializable mappedParameter;

        private LoadPair(final String property, MetaObject metaResultObject, ResultLoader resultLoader) {
            this.property = property;
            this.metaResultObject = metaResultObject;
            this.resultLoader = resultLoader;

            /* Save required information only if original object can be serialized. */
            if (metaResultObject != null && metaResultObject.getOriginalObject() instanceof Serializable) {
                final Object mappedStatementParameter = resultLoader.parameterObject;

                /* @todo May the parameter be null? */
                if (mappedStatementParameter instanceof Serializable) {
                    this.mappedStatement = resultLoader.mappedStatement.getId();
                    this.mappedParameter = (Serializable) mappedStatementParameter;

                    this.configurationFactory = resultLoader.configuration.getConfigurationFactory();
                } else {
                    Log log = this.getLogger();
                    if (log.isDebugEnabled()) {
                        log.debug("Property [" + this.property + "] of ["
                                + metaResultObject.getOriginalObject().getClass() + "] cannot be loaded "
                                + "after deserialization. Make sure it's loaded before serializing "
                                + "forenamed object.");
                    }
                }
            }
        }

        /**
         * 加载未加载属性的方法
         *
         * @throws SQLException
         */
        public void load() throws SQLException {
            /* These field should not be null unless the loadpair was serialized.
             * Yet in that case this method should not be called. */
            if (this.metaResultObject == null) {
                throw new IllegalArgumentException("metaResultObject is null");
            }
            if (this.resultLoader == null) {
                throw new IllegalArgumentException("resultLoader is null");
            }

            this.load(null);
        }

        /**
         * 实际加载未加载属性
         *
         * @param userObject 需要被懒加载的对象，只有在this.metaResultObject 或 this.resultLoader都为null时才生效。否则采用metaResultObject对应的对象
         * @throws SQLException
         */
        public void load(final Object userObject) throws SQLException {
            // 输出结果对象或输出结果加载器不存在
            if (this.metaResultObject == null || this.resultLoader == null) {
                // 判断用以加载属性的对应的SQL语句是否存在，不存在抛异常
                if (this.mappedParameter == null) {
                    throw new ExecutorException("Property [" + this.property + "] cannot be loaded because "
                            + "required parameter of mapped statement ["
                            + this.mappedStatement + "] is not serializable.");
                }
                final Configuration config = this.getConfiguration();
                // 获取用来加载结果的sql语句
                final MappedStatement ms = config.getMappedStatement(this.mappedStatement);
                if (ms == null) {
                    throw new ExecutorException("Cannot lazy load property [" + this.property
                            + "] of deserialized object [" + userObject.getClass()
                            + "] because configuration does not contain statement ["
                            + this.mappedStatement + "]");
                }
                // 创建结果对象的包装
                this.metaResultObject = config.newMetaObject(userObject);
                // 创建结果加载器
                this.resultLoader = new ResultLoader(config, new ClosedExecutor(), ms, this.mappedParameter,
                        metaResultObject.getSetterType(this.property), null, null);
            }

            /* We are using a new executor because we may be (and likely are) on a new thread
             * and executors aren't thread safe. (Is this sufficient?)
             *
             * A better approach would be making executors thread safe. */
            // 只要经历过持久化，可能就处于别的线程中了，所以为这次懒加载创建新线程ResultLoader
            if (this.serializationCheck == null) {
                final ResultLoader old = this.resultLoader;
                this.resultLoader = new ResultLoader(old.configuration, new ClosedExecutor(), old.mappedStatement,
                        old.parameterObject, old.targetType, old.cacheKey, old.boundSql);
            }

            this.metaResultObject.setValue(property, this.resultLoader.loadResult());
        }

        private Configuration getConfiguration() {
            if (this.configurationFactory == null) {
                throw new ExecutorException("Cannot get Configuration as configuration factory was not set.");
            }

            Object configurationObject;
            try {
                final Method factoryMethod = this.configurationFactory.getDeclaredMethod(FACTORY_METHOD);
                if (!Modifier.isStatic(factoryMethod.getModifiers())) {
                    throw new ExecutorException("Cannot get Configuration as factory method ["
                            + this.configurationFactory + "]#["
                            + FACTORY_METHOD + "] is not static.");
                }

                if (!factoryMethod.isAccessible()) {
                    configurationObject = AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
                        try {
                            factoryMethod.setAccessible(true);
                            return factoryMethod.invoke(null);
                        } finally {
                            factoryMethod.setAccessible(false);
                        }
                    });
                } else {
                    configurationObject = factoryMethod.invoke(null);
                }
            } catch (final ExecutorException ex) {
                throw ex;
            } catch (final NoSuchMethodException ex) {
                throw new ExecutorException("Cannot get Configuration as factory class ["
                        + this.configurationFactory + "] is missing factory method of name ["
                        + FACTORY_METHOD + "].", ex);
            } catch (final PrivilegedActionException ex) {
                throw new ExecutorException("Cannot get Configuration as factory method ["
                        + this.configurationFactory + "]#["
                        + FACTORY_METHOD + "] threw an exception.", ex.getCause());
            } catch (final Exception ex) {
                throw new ExecutorException("Cannot get Configuration as factory method ["
                        + this.configurationFactory + "]#["
                        + FACTORY_METHOD + "] threw an exception.", ex);
            }

            if (!(configurationObject instanceof Configuration)) {
                throw new ExecutorException("Cannot get Configuration as factory method ["
                        + this.configurationFactory + "]#["
                        + FACTORY_METHOD + "] didn't return [" + Configuration.class + "] but ["
                        + (configurationObject == null ? "null" : configurationObject.getClass()) + "].");
            }

            return Configuration.class.cast(configurationObject);
        }

        private Log getLogger() {
            if (this.log == null) {
                this.log = LogFactory.getLog(this.getClass());
            }
            return this.log;
        }
    }

    /**
     * BaseExecutor的一个实现类，只有isClosed方法是有意义的
     */
    private static final class ClosedExecutor extends BaseExecutor {

        public ClosedExecutor() {
            super(null, null);
        }

        @Override
        public boolean isClosed() {
            return true;
        }

        @Override
        protected int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        protected List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        protected <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
            throw new UnsupportedOperationException("Not supported.");
        }
    }
}
