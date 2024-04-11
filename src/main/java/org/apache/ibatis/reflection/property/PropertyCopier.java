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
package org.apache.ibatis.reflection.property;

import org.apache.ibatis.reflection.Reflector;

import java.lang.reflect.Field;

/**
 * @author Clinton Begin
 * 用于属性拷贝，类似BeanUtils.copyProperties()，核心方法是{@link #copyBeanProperties(Class, Object, Object)}
 */
public final class PropertyCopier {

    private PropertyCopier() {
        // Prevent Instantiation of Static Class
    }

    /**
     * 完成对象属性拷贝
     *
     * @param type            对象类型
     * @param sourceBean      属性值来源的对象
     * @param destinationBean 属性值赋给的对象
     */
    public static void copyBeanProperties(Class<?> type, Object sourceBean, Object destinationBean) {
        Class<?> parent = type;
        // 知道parent是null结束，递归到是Object类
        while (parent != null) {
            final Field[] fields = parent.getDeclaredFields();
            for (Field field : fields) {
                try {
                    try {
                        // 通过将sourceBean的属性的值赋给DestinationBean
                        field.set(destinationBean, field.get(sourceBean));
                    } catch (IllegalAccessException e) {
                        if (Reflector.canControlMemberAccessible()) {
                            field.setAccessible(true);
                            field.set(destinationBean, field.get(sourceBean));
                        } else {
                            throw e;
                        }
                    }
                } catch (Exception e) {
                    // Nothing useful to do, will only fail on final fields, which will be ignored.
                    //          只有在给final修饰的成员变量拷贝值时才会来到这里，该类属性的值在拷贝时被忽略
//          其余各种异常进入这里都被忽略
                }
            }
//            当前类的属性拷贝完之后，将parent转为其父类，对其从父类继承的属性进行拷贝，直到父类为null(只有Object的父类是null)
            parent = parent.getSuperclass();
        }
    }

}
