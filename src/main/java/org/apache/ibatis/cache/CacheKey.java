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
package org.apache.ibatis.cache;

import org.apache.ibatis.reflection.ArrayUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author Clinton Begin
 * 本类作为缓存的键。实现了无碰撞、高效生成、高效比较的目的。
 * 创建该类的地方位于{@link org.apache.ibatis.executor.BaseExecutor#query}
 */
public class CacheKey implements Cloneable, Serializable {

    private static final long serialVersionUID = 1146682552656046210L;

    public static final CacheKey NULL_CACHE_KEY = new CacheKey() {

        @Override
        public void update(Object object) {
            throw new CacheException("Not allowed to update a null cache key instance.");
        }

        @Override
        public void updateAll(Object[] objects) {
            throw new CacheException("Not allowed to update a null cache key instance.");
        }
    };

    private static final int DEFAULT_MULTIPLIER = 37;
    private static final int DEFAULT_HASHCODE = 17;

    // 计算hashcode时的乘数
    private final int multiplier;
    // 计算出来的hash值。该值不同，两个键一定不同
    private int hashcode;
    // 求和校验值，如果两个CacheKey改制不同，那两个键一定不同
    private long checksum;
    // 更新次数，整个CacheKey更新次数。
    private int count;
    // 8/21/2017 - Sonarlint flags this as needing to be marked transient. While true if content is not serializable, this
    // is not always true and thus should not be marked transient.
    // 更新历史
    private List<Object> updateList;

    public CacheKey() {
        this.hashcode = DEFAULT_HASHCODE;
        this.multiplier = DEFAULT_MULTIPLIER;
        this.count = 0;
        this.updateList = new ArrayList<>();
    }

    public CacheKey(Object[] objects) {
        this();
        updateAll(objects);
    }

    public int getUpdateCount() {
        return updateList.size();
    }

    /**
     * 更新CacheKey
     *
     * @param object 此次更新的参数
     */
    public void update(Object object) {
        int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);

        // 增加更新次数
        count++;
        // 更新求和校验值
        checksum += baseHashCode;
        baseHashCode *= count;

        // 计算hashcode
        hashcode = multiplier * hashcode + baseHashCode;
        // 更新历史记录
        updateList.add(object);
    }

    public void updateAll(Object[] objects) {
        for (Object o : objects) {
            update(o);
        }
    }

    /**
     * 比较两个CacheKey是否相同
     *
     * @param object 要比较的对象
     * @return 比较结果
     */
    @Override
    public boolean equals(Object object) {
        // 比较地址
        if (this == object) {
            return true;
        }
        // 确认类型
        if (!(object instanceof CacheKey)) {
            return false;
        }

        final CacheKey cacheKey = (CacheKey) object;
        // 比较hashcode
        if (hashcode != cacheKey.hashcode) {
            return false;
        }
        // 比较求和校验值
        if (checksum != cacheKey.checksum) {
            return false;
        }
        // 比较更新次数
        if (count != cacheKey.count) {
            return false;
        }

        // 比较更新历史，保证绝对不会出现碰撞问题
        for (int i = 0; i < updateList.size(); i++) {
            Object thisObject = updateList.get(i);
            Object thatObject = cacheKey.updateList.get(i);
            if (!ArrayUtil.equals(thisObject, thatObject)) {
                return false;
            }
        }
        // 全都一样，则为同一个键
        return true;
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public String toString() {
        StringJoiner returnValue = new StringJoiner(":");
        returnValue.add(String.valueOf(hashcode));
        returnValue.add(String.valueOf(checksum));
        updateList.stream().map(ArrayUtil::toString).forEach(returnValue::add);
        return returnValue.toString();
    }

    @Override
    public CacheKey clone() throws CloneNotSupportedException {
        CacheKey clonedCacheKey = (CacheKey) super.clone();
        clonedCacheKey.updateList = new ArrayList<>(updateList);
        return clonedCacheKey;
    }

}
