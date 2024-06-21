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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lru (least recently used) cache decorator.
 * 最近最少使用的淘汰策略
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

    private final Cache delegate;
    private Map<Object, Object> keyMap; // 保存缓存数据的键，实际使用的LinkedHashMap，为了使用他的排序功能
    private Object eldestKey;   // 记录最近最少使用的键

    public LruCache(Cache delegate) {
        this.delegate = delegate;
        setSize(1024);  //默认缓存数量
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    public void setSize(final int size) {
        keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
            private static final long serialVersionUID = 4267176411845948333L;

            /**
             * 重写了removeEldestEntry方法，该方法在每次向LinkedHashMap放入数据时触发。
             */
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
                // 如果缓存键数量满了，记录eldestKey
                boolean tooBig = size() > size;
                if (tooBig) {
                    eldestKey = eldest.getKey();
                }
                return tooBig;
            }
        };
    }

    @Override
    public void putObject(Object key, Object value) {
        delegate.putObject(key, value);
        cycleKeyList(key);
    }

    @Override
    public Object getObject(Object key) {
        // 访问一下当前被访问的键，会将它前移
        keyMap.get(key); // touch
        return delegate.getObject(key);
    }

    @Override
    public Object removeObject(Object key) {
        return delegate.removeObject(key);
    }

    @Override
    public void clear() {
        delegate.clear();
        keyMap.clear();
    }

    /**
     * 每次插入缓存时调用该方法，将新插入的键放入keyMap并删除eldestKey这个键对应的对象
     *
     * @param key 新插入的键
     */
    private void cycleKeyList(Object key) {
        keyMap.put(key, key);
        if (eldestKey != null) {
            delegate.removeObject(eldestKey);
            eldestKey = null;
        }
    }

}
