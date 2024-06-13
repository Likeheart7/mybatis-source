/**
 * Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * References a generic type.
 * <p>
 * 类型参考器，用俩瓶那段TypeHandler用来处理的目标类型
 *
 * @param <T> the referenced type
 * @author Simone Tripodi
 * @since 3.1.0
 */
public abstract class TypeReference<T> {

    private final Type rawType;

    protected TypeReference() {
        rawType = getSuperclassTypeParameter(getClass());
    }

    /**
     * 取出类型TypeHandler的泛型参数，这个泛型参数的值，就是这个TypeHandler能够处理的类型
     *
     * @param clazz TypeHandler的具体实现类
     * @return 该TypeHandler能处理的目标类型
     */
    Type getSuperclassTypeParameter(Class<?> clazz) {
        // 获取具体实现类的带泛型的直接父类
        // getGenericSuperclass 返回 Type 而不是 Class 是为了能够表示泛型类型。通过 Type 接口，我们可以获取到完整的类型信息，包括泛型参数，
        Type genericSuperclass = clazz.getGenericSuperclass();
        // 如果genericSuperclass是class实例，最终获取到的genericSuperclass应当是一个Type类型
        if (genericSuperclass instanceof Class) {
            // try to climb up the hierarchy until meet something useful
            // 进入到这里，说明没有解析到足够上层， 使用父类递归调用本方法
            if (TypeReference.class != genericSuperclass) {
                return getSuperclassTypeParameter(clazz.getSuperclass());
            }
            // 说明clazz实现了TypeReference类，却没有使用泛型
            throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
                    + "Remove the extension or add a type parameter to it.");
        }

        // 说明genericSuperclass是泛型类，获取泛型的第一个参数
        Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
        // TODO remove this when Reflector is fixed to return Types
        if (rawType instanceof ParameterizedType) { //如果是参数化类型
            // 获取参数化类型的实际类型
            rawType = ((ParameterizedType) rawType).getRawType();
        }

        return rawType;
    }

    public final Type getRawType() {
        return rawType;
    }

    @Override
    public String toString() {
        return rawType.toString();
    }

}
