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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Clinton Begin
 * 在 Reflector对象的初始化过程中，所有属性的 getter/setter方法都会被封装成 MethodInvoker对象，
 * 没有 getter/setter 的字段也会生成对应的 Get/SetFieldInvoker对象
 */
public interface Invoker {
    /**
     * 调用底层封装的Method方法
     *
     * @param target 调用方法的对象
     * @param args   方法惨呼
     * @return 返回值
     */
    Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

    /**
     * 对于GetFieldInvoker和SetFieldInvoker来说，获取目标属性的类型
     * 对于其他方法来说，如果方法有且只有一个输入参数时，获取该参数的类型，否则获取的是返回值的类型
     */
    Class<?> getType();
}
