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
package org.apache.ibatis.cache;

import org.apache.ibatis.cache.decorators.TransactionalCache;
import org.apache.ibatis.session.SqlSession;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Clinton Begin
 * 一个事务可能涉及多个缓存，本类是管理同一事务中的多个缓存的
 */
public class TransactionalCacheManager {

    // 保存了Cache和对应的存放二级缓存的TransactionalCache
    private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

    public void clear(Cache cache) {
        getTransactionalCache(cache).clear();
    }

    public Object getObject(Cache cache, CacheKey key) {
        return getTransactionalCache(cache).getObject(key);
    }

    public void putObject(Cache cache, CacheKey key, Object value) {
        getTransactionalCache(cache).putObject(key, value);
    }

    /**
     * 在事务提交时触发所有相关事务缓存的提交
     * 一般情况下调用来源如下：
     * 手动调用{@link SqlSession#commit()}, 实际上调用的实现是{@link org.apache.ibatis.session.defaults.DefaultSqlSession#commit} -->
     * {@link org.apache.ibatis.executor.CachingExecutor#commit(boolean)} --> 本方法 --> {@link TransactionalCache#commit()}
     * 所以会触发将事务缓存管理器的内容全都提交到缓存中
     */
    public void commit() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.commit();
        }
    }

    /**
     * 在事务回滚时触发所有相关事务缓存的回滚
     */
    public void rollback() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.rollback();
        }
    }

    private TransactionalCache getTransactionalCache(Cache cache) {
        return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
    }

}
