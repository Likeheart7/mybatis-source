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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * 事务装饰器
 * 事务操作中产生的数据需要在事务提交时写入缓存，而在事务回滚时直接销毁。TransactionalCache装饰器就为缓存提供了这一功能
 */
public class TransactionalCache implements Cache {

    private static final Log log = LogFactory.getLog(TransactionalCache.class);

    // 二级缓存真正存储数据的地方，实际上在存在二级缓存时，该值就是MappedStatement类的cache属性，在query方法开头，Cache cache = ms.getCache();，最终传给了这里
    private final Cache delegate;
    private boolean clearOnCommit;  // 如果为true，则只要事务结束，就会直接将暂时保存的数据销毁掉
    private final Map<Object, Object> entriesToAddOnCommit; // 保存事务中产生的数据，在事务提交时一并交给缓存，或在回滚时一并销毁
    // 缓存查询未命中的键。因为在BlockingCache装饰器下，未命中的缓存键会被上锁，防止多线程同时向数据库请求一样的查询
    private final Set<Object> entriesMissedInCache;

    public TransactionalCache(Cache delegate) {
        this.delegate = delegate;
        this.clearOnCommit = false;
        this.entriesToAddOnCommit = new HashMap<>();
        this.entriesMissedInCache = new HashSet<>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    /**
     * 获取缓存
     *
     * @param key 键
     * @return 获取到的缓存数据
     */
    @Override
    public Object getObject(Object key) {
        // issue #116
        // 从缓存中读取对应数据
        Object object = delegate.getObject(key);
        // 缓存未命中，记录下来
        if (object == null) {
            entriesMissedInCache.add(key);
        }
        // issue #146
        // 如果该属性为true，直接返回null
        if (clearOnCommit) {
            return null;
        } else {
            return object;
        }
    }

    /**
     * 插入缓存
     *
     * @param key    键
     * @param object 缓存数据
     */
    @Override
    public void putObject(Object key, Object object) {
        // 放到entriesToAddOnCommit中暂存
        entriesToAddOnCommit.put(key, object);
    }

    @Override
    public Object removeObject(Object key) {
        return null;
    }

    @Override
    public void clear() {
        clearOnCommit = true;
        entriesToAddOnCommit.clear();
    }

    /**
     * 提交事务
     */
    public void commit() {
        if (clearOnCommit) {
            // 清理缓存
            delegate.clear();
        }
        // 将暂存的数据写入缓存
        flushPendingEntries();
        // 清理环境
        reset();
    }

    /**
     * 回滚事务
     */
    public void rollback() {
        // 删除未命中的缓存
        unlockMissedEntries();
        reset();
    }

    // 清理环境，回到初始状态
    private void reset() {
        clearOnCommit = false;
        entriesToAddOnCommit.clear();
        entriesMissedInCache.clear();
    }

    /**
     * 将暂存的数据写入缓存中
     */
    private void flushPendingEntries() {
        // 暂存数据写入
        for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
            delegate.putObject(entry.getKey(), entry.getValue());
        }
        // 未命中的键，在缓存中写入null值
        // question：未命中的键本身查询结果不就是null才进入entriesMissedInCache中的，为什么这里还要插入null值？
        for (Object entry : entriesMissedInCache) {
            if (!entriesToAddOnCommit.containsKey(entry)) {
                delegate.putObject(entry, null);
            }
        }
    }

    /**
     * 删除未命中的数据
     */
    private void unlockMissedEntries() {
        for (Object entry : entriesMissedInCache) {
            try {
                delegate.removeObject(entry);
            } catch (Exception e) {
                log.warn("Unexpected exception while notifiying a rollback to the cache adapter. "
                        + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
            }
        }
    }

}
