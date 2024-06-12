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
package org.apache.ibatis.reflection.factory;

import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.Reflector;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Clinton Begin
 * ObjectFactory接口的默认实现，用来创建对象
 * 核心方法{@link #create(Class)} 和 {@link #create(Class, List, List)}
 */
public class DefaultObjectFactory implements ObjectFactory, Serializable {

    private static final long serialVersionUID = -8855120656740914948L;

    /**
     * 指定类型使用无参构造器创建
     */
    @Override
    public <T> T create(Class<T> type) {
        return create(type, null, null);
    }

    /**
     * 指定类型，参数类型，参数值使用对应构造器创建
     *
     * @param type                要创建的对象的类型
     * @param constructorArgTypes 参数类型
     * @param constructorArgs     参数值
     * @return 创建的新对象
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        // 创建前，先判断是否是一些特定的集合接口类型
        Class<?> classToCreate = resolveInterface(type);
        // we know types are assignable
        return (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);
    }

    /**
     * 供create方法调用，通过反射找到与参数匹配的构造方法，调用该方法生成实例。是真正生成实例的方法
     *
     * @param type                要创建的对象的类型
     * @param constructorArgTypes 参数类型
     * @param constructorArgs     参数值
     * @return 创建的新对象
     */
    private <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        try {
            // 找到的构造方法
            Constructor<T> constructor;
            // 如果没有传递参数或参数列表，就调用无参构造器
            if (constructorArgTypes == null || constructorArgs == null) {
                constructor = type.getDeclaredConstructor();
                try {
                    // 根据无参构造器生成实例
                    return constructor.newInstance();
                } catch (IllegalAccessException e) {  // 如果产生异常，通过设置构造器访问权限再尝试一次
                    if (Reflector.canControlMemberAccessible()) {
                        constructor.setAccessible(true);
                        return constructor.newInstance();
                    } else {
                        throw e;
                    }
                }
            }
            // 根据传入的参数类型获取指定的有参构造器
            constructor = type.getDeclaredConstructor(constructorArgTypes.toArray(new Class[0]));
            try {
                // 根据输入的参数值创建对象实例并返回
                return constructor.newInstance(constructorArgs.toArray(new Object[0]));
            } catch (IllegalAccessException e) {  // 一样的通过修改访问修饰符再尝试一次
                if (Reflector.canControlMemberAccessible()) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(constructorArgs.toArray(new Object[0]));
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            // 收集所有参数类型
            String argTypes = Optional.ofNullable(constructorArgTypes).orElseGet(Collections::emptyList)
                    .stream().map(Class::getSimpleName).collect(Collectors.joining(","));
            // 收集所有参数
            String argValues = Optional.ofNullable(constructorArgs).orElseGet(Collections::emptyList)
                    .stream().map(String::valueOf).collect(Collectors.joining(","));
            throw new ReflectionException("Error instantiating " + type + " with invalid types (" + argTypes + ") or values (" + argValues + "). Cause: " + e, e);
        }
    }

    /**
     * 当传入类型是接口时，找到一个符合该接口的实现，返回其实例。
     */
    protected Class<?> resolveInterface(Class<?> type) {
        Class<?> classToCreate;
        // 对于List、Collection、Iterable接口，返回ArrayList
        if (type == List.class || type == Collection.class || type == Iterable.class) {
            classToCreate = ArrayList.class;
            // 对于Map类型，返回HashMap
        } else if (type == Map.class) {
            classToCreate = HashMap.class;
            // 对于SortedSet返回TreeSet
        } else if (type == SortedSet.class) { // issue #510 Collections Support
            classToCreate = TreeSet.class;
            // 如果时Set类型返回HashSet
        } else if (type == Set.class) {
            classToCreate = HashSet.class;
        } else {
            classToCreate = type;
        }
        return classToCreate;
    }

    @Override
    public <T> boolean isCollection(Class<T> type) {
        return Collection.class.isAssignableFrom(type);
    }

}
