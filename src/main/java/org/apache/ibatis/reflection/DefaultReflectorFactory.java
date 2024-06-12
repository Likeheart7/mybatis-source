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
package org.apache.ibatis.reflection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ReflectorFactory默认实现，用来缓存Reflector，提升Reflector的初始速度。
 * 核心方法是{@link #findForClass(Class)}
 */
public class DefaultReflectorFactory implements ReflectorFactory {
    // 默认是允许Reflector缓存的
    private boolean classCacheEnabled = true;
    // 存储缓存的Reflector对象
    private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<>();

    public DefaultReflectorFactory() {
    }

    @Override
    public boolean isClassCacheEnabled() {
        return classCacheEnabled;
    }

    /**
     * 用于设置是否允许缓存Reflector
     *
     * @param classCacheEnabled true：允许缓存， false：禁止缓存
     */
    @Override
    public void setClassCacheEnabled(boolean classCacheEnabled) {
        this.classCacheEnabled = classCacheEnabled;
    }

    /**
     * 核心方法
     *
     * @param type Reflector解析的类型
     */
    @Override
    public Reflector findForClass(Class<?> type) {
        // 如果允许缓存，就从reflectorMap获取返回，如果里面没有就创建一个然后返回，这样下次使用就可以从reflectorMap中拿，提高速度
        if (classCacheEnabled) {
            // synchronized (type) removed see issue #461
            return reflectorMap.computeIfAbsent(type, Reflector::new);
        } else {
            // 如果不允许缓存，就新建一个
            return new Reflector(type);
        }
    }

}
