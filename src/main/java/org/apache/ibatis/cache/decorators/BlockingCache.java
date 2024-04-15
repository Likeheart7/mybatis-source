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
import org.apache.ibatis.cache.CacheException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>Simple blocking decorator
 *
 * <p>Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * <p>By its nature, this implementation can cause deadlock when used incorrecly.
 *
 * @author Eduardo Macarron
 * 在原有的缓存上加了阻塞
 */
public class BlockingCache implements Cache {

    // 指定一个线程阻塞的超时时间
    private long timeout;
    private final Cache delegate;
    // 为每个key分配一个CountDownLatch对象来做并发控制
    private final ConcurrentHashMap<Object, CountDownLatch> locks;

    public BlockingCache(Cache delegate) {
        this.delegate = delegate;
        this.locks = new ConcurrentHashMap<>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    @Override
    public void putObject(Object key, Object value) {
        try {
            delegate.putObject(key, value);
        } finally {
            // 这里是在命中缓存失败后，查库再插入缓存时释放查询缓存getObject()方法申请的锁
            releaseLock(key);
        }
    }

    @Override
    public Object getObject(Object key) {
        // 首先去获取锁
        acquireLock(key);
        Object value = delegate.getObject(key);
        // 如果没命中缓存不会释放锁，等到从数据库拿到数据在放入缓存时才释放，防止其他线程在同一时间也因为没有命中缓存去数据库查询结果
        if (value != null) {
            releaseLock(key);
        }
        return value;
    }

    @Override
    public Object removeObject(Object key) {
        // despite of its name, this method is called only to release locks
        releaseLock(key);
        return null;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    /**
     * 获取锁的方法
     *
     * @param key 缓存的键
     */
    private void acquireLock(Object key) {
        // 初始化一个新的CountDownLatch
        CountDownLatch newLatch = new CountDownLatch(1);

        while (true) {
            // 如果没有就将这个新的CountDownLatch放进去，返回null，如果有就直接返回该key对应的CountDownLatch对象
            CountDownLatch latch = locks.putIfAbsent(key, newLatch);
            // 说明里面没有，就是拿到锁了
            if (latch == null) {
                break;
            }
            // 走到这里说明当前已经有其他线程在访问
            try {
                if (timeout > 0) {
                    // 将当前线程阻塞在另一个在操作缓存的线程的CountDownLatch对象之上，
                    // 这样可以在哪个线程操作完，调用latch.countdown()后唤醒自己
                    boolean acquired = latch.await(timeout, TimeUnit.MILLISECONDS);
                    // 超时了还没获取到，抛出异常
                    if (!acquired) {
                        throw new CacheException(
                                "Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
                    }
                } else { //如果没有设置超时时间，就一直等，等到另一个线程释放
                    latch.await();
                }
            } catch (InterruptedException e) {
                throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
            }
        }
    }

    private void releaseLock(Object key) {
        CountDownLatch latch = locks.remove(key);
        if (latch == null) {
            throw new IllegalStateException("Detected an attempt at releasing unacquired lock. This should never happen.");
        }
        latch.countDown();
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
