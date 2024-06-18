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
package org.apache.ibatis.builder.annotation;

import org.apache.ibatis.builder.BuilderException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The interface that resolve an SQL provider method via an SQL provider class.
 *
 * <p> This interface need to implements at an SQL provider class and
 * it need to define the default constructor for creating a new instance.
 * 作用是从@XxxProvider注解的type属性指向的类中找出method属性指定的方法。核心方法{@link ProviderMethodResolver#resolveMethod}
 *
 * @author Kazuki Shimizu
 * @since 3.5.1
 */
public interface ProviderMethodResolver {

    /**
     * 从@XxxProvider注解的type属性所指向的类中找出method属性所指定的方法
     * <p> The default implementation return a method that matches following conditions.
     * <ul>
     *   <li>Method name matches with mapper method</li>
     *   <li>Return type matches the {@link CharSequence}({@link String}, {@link StringBuilder}, etc...)</li>
     * </ul>
     * If matched method is zero or multiple, it throws a {@link BuilderException}.
     *
     * @param context a context for SQL provider 包含@XxxProvider注解中的type属性和method属性
     * @return an SQL provider method 找出的指定方法
     * @throws BuilderException Throws when cannot resolve a target method
     */
    default Method resolveMethod(ProviderContext context) {
        // 找出同名的方法，因方法重载可能有多个
        // 这里的getClass方法是调用该默认方法的对象的getClass
        List<Method> sameNameMethods = Arrays.stream(getClass().getMethods())
                .filter(m -> m.getName().equals(context.getMapperMethod().getName()))
                .collect(Collectors.toList());
        // 一个都没找到，抛出异常
        if (sameNameMethods.isEmpty()) {
            throw new BuilderException("Cannot resolve the provider method because '"
                    + context.getMapperMethod().getName() + "' not found in SqlProvider '" + getClass().getName() + "'.");
        }
        // 根据返回类型过滤，返回类型必须是CharSequence或子类
        List<Method> targetMethods = sameNameMethods.stream()
                .filter(m -> CharSequence.class.isAssignableFrom(m.getReturnType()))
                .collect(Collectors.toList());
        // 只剩一个，就是该方法
        if (targetMethods.size() == 1) {
            return targetMethods.get(0);
        }
        // 一个都没剩，找不到方法，抛出异常
        if (targetMethods.isEmpty()) {
            throw new BuilderException("Cannot resolve the provider method because '"
                    + context.getMapperMethod().getName() + "' does not return the CharSequence or its subclass in SqlProvider '"
                    + getClass().getName() + "'.");
            // 剩了不止一个，无法确定是哪个方法。抛出异常
        } else {
            throw new BuilderException("Cannot resolve the provider method because '"
                    + context.getMapperMethod().getName() + "' is found multiple in SqlProvider '" + getClass().getName() + "'.");
        }
    }

}
