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
package org.apache.ibatis.plugin;

import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Clinton Begin
 * 继承InvocationHandler，实现基于反射的代理类。
 */
public class Plugin implements InvocationHandler {

    // 被代理对象
    private final Object target;
    // 拦截器
    private final Interceptor interceptor;
    // 拦截器要拦截的所有类，以及类中对应的方法
    private final Map<Class<?>, Set<Method>> signatureMap;

    private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
        this.target = target;
        this.interceptor = interceptor;
        this.signatureMap = signatureMap;
    }

    /**
     * 如果一个类将会被拦截器拦截，会通过此方法为该类从创建一个代理对象。否则不做任何操作
     *
     * @param target      被代理对象
     * @param interceptor 拦截器
     * @return 用来替换被代理对象的代理对象
     */
    public static Object wrap(Object target, Interceptor interceptor) {
        // 获取自定义Interceptor实现类上的@Signature注解信息，得到要拦截的所有类型和具体方法
        Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
        // 获取被代理对象的类型
        Class<?> type = target.getClass();
        // 逐级寻找被代理对象类型的父类。将父类中需要被拦截的也找出来
        Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
        // 只要父类中有一个是需要被拦截的，说明被代理对象是需要被拦截的
        if (interfaces.length > 0) {
            // 根据本类，创建代理对象并返回。
            return Proxy.newProxyInstance(
                    type.getClassLoader(),
                    interfaces,
                    new Plugin(target, interceptor, signatureMap));
        }
        return target;
    }

    /**
     * 需要被拦截的方法的对象会被wrap()方法替换成代理对象，后面执行方法就会进入本方法
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // 获取当前对象所属类所有需要拦截的方法。
            Set<Method> methods = signatureMap.get(method.getDeclaringClass());
            // 如果当前方法需要被拦截器处理，执行intercept()方法进行拦截处理
            // intercept()方法就是插件自定义的处理逻辑
            if (methods != null && methods.contains(method)) {
                return interceptor.intercept(new Invocation(target, method, args));
            }
            // 如果当前方法不需要被代理，则调用target对象执行原来的逻辑
            return method.invoke(target, args);
        } catch (Exception e) {
            throw ExceptionUtil.unwrapThrowable(e);
        }
    }

    /**
     * 获取拦截器要拦截的所有类和其中的要拦截的方法
     *
     * @param interceptor 拦截器
     * @return 参数中的拦截器要拦截的所有类的所有要被拦截的方法
     */
    private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
        // 获取@Intercepts注解
        Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
        // issue #251
        if (interceptsAnnotation == null) {
            throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
        }

        // 取出@Intercepts注解的value属性，是一个Signature数组
        Signature[] sigs = interceptsAnnotation.value();
        // 将Signature分解为一个Map，键是其type属性，值是method属性 + args属性对应的Method类
        Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
        for (Signature sig : sigs) {
            Set<Method> methods = signatureMap.computeIfAbsent(sig.type(), k -> new HashSet<>());
            try {
                Method method = sig.type().getMethod(sig.method(), sig.args());
                methods.add(method);
            } catch (NoSuchMethodException e) {
                throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
            }
        }
        return signatureMap;
    }

    private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
        Set<Class<?>> interfaces = new HashSet<>();
        while (type != null) {
            for (Class<?> c : type.getInterfaces()) {
                if (signatureMap.containsKey(c)) {
                    interfaces.add(c);
                }
            }
            type = type.getSuperclass();
        }
        return interfaces.toArray(new Class<?>[0]);
    }

}
