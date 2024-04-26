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
package org.apache.ibatis.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 该注解用来指定 DemoPlugin 插件实现类要拦截的目标方法信息
 * The annotation that indicate the method signature.
 *
 * @author Clinton Begin
 * @see Intercepts
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Signature {
    /**
     * Returns the java type.
     * 要拦截的类
     *
     * @return the java type
     */
    Class<?> type();

    /**
     * Returns the method name.
     * 要拦截的方法
     *
     * @return the method name
     */
    String method();

    /**
     * Returns java types for method argument.
     * 拦截方法的参数列表
     *
     * @return java types for method argument
     */
    Class<?>[] args();
}
