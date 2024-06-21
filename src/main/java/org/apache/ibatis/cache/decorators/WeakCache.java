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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Deque;
import java.util.LinkedList;

/**
 * Weak Reference cache decorator.
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 * 弱引用的淘汰策略：每次gc都会回收掉
 * 会用弱引用包装缓存对象，jvm就可以在内存不足时自动回收这些
 *
 * @author Clinton Begin
 */
public class WeakCache implements Cache {
    // 强引用对象列表
    private final Deque<Object> hardLinksToAvoidGarbageCollection;
    // 弱引用对象列表
    private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
    // 被装饰对象，实际存储缓存数据的地方
    private final Cache delegate;
    // 强引用数量
    private int numberOfHardLinks;

    public WeakCache(Cache delegate) {
        this.delegate = delegate;
        this.numberOfHardLinks = 256;
        this.hardLinksToAvoidGarbageCollection = new LinkedList<>();
        this.queueOfGarbageCollectedEntries = new ReferenceQueue<>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        removeGarbageCollectedItems();
        return delegate.getSize();
    }

    public void setSize(int size) {
        this.numberOfHardLinks = size;
    }

    /**
     * 添加一条缓存
     *
     * @param key   数据键
     * @param value 数据值
     */
    @Override
    public void putObject(Object key, Object value) {
        // 根据垃圾回收队列中被回收的元素，从缓存中将他们删除
        removeGarbageCollectedItems();
        // 向被装饰对象放入的值时弱引用包装后的数据
        delegate.putObject(key, new WeakEntry(key, value, queueOfGarbageCollectedEntries));
    }

    /**
     * 从缓存中获取数据
     * 数据可能被JVM删除了，所有需要判断
     *
     * @param key 获取缓存的键
     * @return 获取到的缓存数据
     */
    @Override
    public Object getObject(Object key) {
        Object result = null;
        @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
        WeakReference<Object> weakReference = (WeakReference<Object>) delegate.getObject(key);
        // 取到了弱引用的引用
        if (weakReference != null) {
            // 读取弱引用的对象
            result = weakReference.get();
            // 如果对象已经被清理了
            if (result == null) {
                // 删除该缓存
                delegate.removeObject(key);
            } else {
                //如果没被清理，将缓存数据写入强引用队列，防止其在使用过程中被清理。
                hardLinksToAvoidGarbageCollection.addFirst(result);
                // 如果强引用对象数据超出限制，删掉最后面那个
                if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
                    hardLinksToAvoidGarbageCollection.removeLast();
                }
            }
        }
        return result;
    }

    @Override
    public Object removeObject(Object key) {
        removeGarbageCollectedItems();
        return delegate.removeObject(key);
    }

    @Override
    public void clear() {
        hardLinksToAvoidGarbageCollection.clear();
        removeGarbageCollectedItems();
        delegate.clear();
    }

    /**
     * 每次插入缓存时，清理掉 被JVM自动回收掉的缓存
     */
    private void removeGarbageCollectedItems() {
        WeakEntry sv;
        // 遍历弱引用队列
        while ((sv = (WeakEntry) queueOfGarbageCollectedEntries.poll()) != null) {
            // 将该队列中的键从缓存中删除
            delegate.removeObject(sv.key);
        }
    }

    /**
     * 通过key属性保存键值，防止弱引用缓存被清理后，无法计算对应的键，
     */
    private static class WeakEntry extends WeakReference<Object> {
        private final Object key;

        private WeakEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
            super(value, garbageCollectionQueue);
            this.key = key;
        }
    }

}
