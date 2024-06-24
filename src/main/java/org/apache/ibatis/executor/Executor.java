/**
 * Copyright 2009-2015 the original author or authors.
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

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.SQLException;
import java.util.List;

/**
 * @author Clinton Begin
 * 执行器的顶级父接口，Mybatis的所有数据库操作都通过调用这些方法实现
 */
public interface Executor {

    ResultHandler NO_RESULT_HANDLER = null;

    /**
     * 更新，包括insert、update、delete
     */
    int update(MappedStatement ms, Object parameter) throws SQLException;

    /**
     * 查询，返回结果是List
     */
    <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

    /**
     * 查询，返回结果是List
     */
    <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;

    /**
     * 查询游标。
     */
    <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

    /**
     * 清理缓存
     */
    List<BatchResult> flushStatements() throws SQLException;

    /**
     * 提交事务
     */
    void commit(boolean required) throws SQLException;

    /**
     * 事务回滚
     */
    void rollback(boolean required) throws SQLException;

    /**
     * 创建当前查询的缓存键值
     */
    CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

    /**
     * 本地缓存是否有指定值
     */
    boolean isCached(MappedStatement ms, CacheKey key);

    /**
     * 清除缓存
     */
    void clearLocalCache();

    /**
     * 懒加载
     */
    void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

    /**
     * 获取事务
     */
    Transaction getTransaction();

    /**
     * 关闭执行器
     */
    void close(boolean forceRollback);

    /**
     * 执行器是否关闭
     */
    boolean isClosed();

    /**
     * 设置执行器包装
     */
    void setExecutorWrapper(Executor executor);

}
