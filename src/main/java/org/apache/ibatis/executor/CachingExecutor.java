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
package org.apache.ibatis.executor;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.SQLException;
import java.util.List;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * 二级缓存的具体实现，是一个装饰器类，装饰器其他执行器。
 * 二级缓存全局开关：这个全局开关是 mybatis-config.xml 配置文件中的 cacheEnabled 配置项
 * 二级缓存命名空间级别开关：cache标签或cache-ref标签
 * 语句级别：通过标签的useCache属性，默认是true
 */
public class CachingExecutor implements Executor {

    // 被装饰的实际执行器。
    private final Executor delegate;
    // 事务缓存管理器
    private final TransactionalCacheManager tcm = new TransactionalCacheManager();

    public CachingExecutor(Executor delegate) {
        this.delegate = delegate;
        delegate.setExecutorWrapper(this);
    }

    @Override
    public Transaction getTransaction() {
        return delegate.getTransaction();
    }

    @Override
    public void close(boolean forceRollback) {
        try {
            // issues #499, #524 and #573
            if (forceRollback) {
                tcm.rollback();
            } else {
                tcm.commit();
            }
        } finally {
            delegate.close(forceRollback);
        }
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    /**
     * insert、update、delete操作调用的方法，这些操作嗲用flushCacheIfRequired始终返回true，也即是说这些方法的调用一定会清除二级缓存，防止缓存旧数据
     *
     * @param ms              映射语句
     * @param parameterObject 参数对象
     * @return 数据库操作执行结果
     * @throws SQLException
     */
    @Override
    public int update(MappedStatement ms, Object parameterObject) throws SQLException {
        // update类型的操作默认会清除缓存
        flushCacheIfRequired(ms);
        return delegate.update(ms, parameterObject);
    }

    @Override
    public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
        flushCacheIfRequired(ms);
        return delegate.queryCursor(ms, parameter, rowBounds);
    }

    @Override
    public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
        // 获取boundSql
        BoundSql boundSql = ms.getBoundSql(parameterObject);
        // 创建相应的cacheKey
        CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
        return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
    }

    /**
     * 数据库查询的核心方法
     *
     * @param ms              映射语句
     * @param parameterObject 参数对象
     * @param rowBounds       分页限制
     * @param resultHandler   结果处理器
     * @param key             缓存的键
     * @param boundSql        查询语句
     * @param <E>             结果类型
     * @return 查询结果
     * @throws SQLException
     */
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
            throws SQLException {
        // 获取该命名空间使用的二级缓存，可能是无缓存、本命名空间缓存、通过cache-ref标签引用的其他命名空间的缓存
        Cache cache = ms.getCache();
        // 判断是否开启二级缓存，根据映射文件是否包设置cache、cache-ref标签，没有设置就是null
        if (cache != null) {
            // 根据select标签的的配置决定是否清空二级缓存，在没有显式配置标签的flushCache属性为true的情况下不会情况缓存。
            flushCacheIfRequired(ms);
            // 该语句使用缓存并且没有输出结果处理器
            if (ms.isUseCache() && resultHandler == null) {
                // 是否包含输出参数的CALLABLE语句，二级缓存不支持这个。
                ensureNoOutParams(ms, boundSql);
                // 查询二级缓存
                @SuppressWarnings("unchecked")
                List<E> list = (List<E>) tcm.getObject(cache, key);
                //二级缓存未命中，交给被包装的执行器执行，缓存执行结果
                if (list == null) {
                    list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
                    tcm.putObject(cache, key, list); // issue #578 and #116
                }
                return list;
            }
        }
        // 如果未开启二级缓存，直接通过被装饰的Executor对象查询结果对象
        return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
    }

    @Override
    public List<BatchResult> flushStatements() throws SQLException {
        return delegate.flushStatements();
    }

    @Override
    public void commit(boolean required) throws SQLException {
        delegate.commit(required);
        tcm.commit();
    }

    @Override
    public void rollback(boolean required) throws SQLException {
        try {
            delegate.rollback(required);
        } finally {
            if (required) {
                tcm.rollback();
            }
        }
    }

    private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
        if (ms.getStatementType() == StatementType.CALLABLE) {
            for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
                if (parameterMapping.getMode() != ParameterMode.IN) {
                    throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
                }
            }
        }
    }

    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
        return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
    }

    @Override
    public boolean isCached(MappedStatement ms, CacheKey key) {
        return delegate.isCached(ms, key);
    }

    @Override
    public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
        delegate.deferLoad(ms, resultObject, property, key, targetType);
    }

    @Override
    public void clearLocalCache() {
        delegate.clearLocalCache();
    }

    /**
     * 根据标签的flushCache属性的值，判断是否要清除二级缓存
     *
     * @param ms 映射语句
     */
    private void flushCacheIfRequired(MappedStatement ms) {
        Cache cache = ms.getCache();
        // 如果缓存不为null，且该语句配置了flushCache为true，则先删掉缓存
        if (cache != null && ms.isFlushCacheRequired()) {
            tcm.clear(cache);
        }
    }

    @Override
    public void setExecutorWrapper(Executor executor) {
        throw new UnsupportedOperationException("This method should not be called");
    }

}
