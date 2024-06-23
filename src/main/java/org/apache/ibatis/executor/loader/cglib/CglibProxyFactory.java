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
package org.apache.ibatis.executor.loader.cglib;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.apache.ibatis.executor.loader.*;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyCopier;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Clinton Begin
 * 基于CGLib实现的代理工厂
 */
public class CglibProxyFactory implements ProxyFactory {

    private static final String FINALIZE_METHOD = "finalize";
    private static final String WRITE_REPLACE_METHOD = "writeReplace";

    public CglibProxyFactory() {
        try {
            Resources.classForName("net.sf.cglib.proxy.Enhancer");
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot enable lazy loading because CGLIB is not available. Add CGLIB to your classpath.", e);
        }
    }

    /**
     * 创建一个普通代理对象，重写的父类方法
     *
     * @param target
     * @param lazyLoader
     * @param configuration
     * @param objectFactory
     * @param constructorArgTypes
     * @param constructorArgs
     * @return
     */
    @Override
    public Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        return EnhancedResultObjectProxyImpl.createProxy(target, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
    }

    /**
     * 创建一个反序列化的代理对象
     *
     * @param target
     * @param unloadedProperties
     * @param objectFactory
     * @param constructorArgTypes
     * @param constructorArgs
     * @return
     */
    public Object createDeserializationProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        return EnhancedDeserializationProxyImpl.createProxy(target, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }

    /**
     * 在创建EnhancedResultObjectProxyImpl和EnhancedDeserializationProxyImpl类时，都会在createProxy方法中调用本方法。本方法确保代理类拥有一个writeReplace方法
     * 校验代理类中是否包含writeReplace方法。如果没有就让代理类实现WriteReplaceInterface接口，得到其中的writeReplace方法，该方法会在序列化前调用，实现自定义操作
     *
     * @param type                被代理对象类型
     * @param callback            回调对象
     * @param constructorArgTypes 构造方法参数类型列表
     * @param constructorArgs     构造方法参数
     * @return 代理对象
     */
    static Object crateProxy(Class<?> type, Callback callback, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        Enhancer enhancer = new Enhancer();
        enhancer.setCallback(callback);
        // 创建的代理对象作为原对象的子类
        enhancer.setSuperclass(type);
        try {
            // 获取writeReplace方法，没找到会抛出NoSuchMethodException，进入catch块处理
            type.getDeclaredMethod(WRITE_REPLACE_METHOD);
            // ObjectOutputStream will call writeReplace of objects returned by writeReplace
            if (LogHolder.log.isDebugEnabled()) {
                LogHolder.log.debug(WRITE_REPLACE_METHOD + " method was found on bean " + type + ", make sure it returns this");
            }
        } catch (NoSuchMethodException e) {
            // 如果没找到writeReplace方法，设置到李磊继承WriteReplaceInterface接口，该接口包含一个writeReplace方法
            enhancer.setInterfaces(new Class[]{WriteReplaceInterface.class});
        } catch (SecurityException e) {
            // nothing to do here
        }
        Object enhanced;
        if (constructorArgTypes.isEmpty()) {
            enhanced = enhancer.create();
        } else {
            Class<?>[] typesArray = constructorArgTypes.toArray(new Class[constructorArgTypes.size()]);
            Object[] valuesArray = constructorArgs.toArray(new Object[constructorArgs.size()]);
            enhanced = enhancer.create(typesArray, valuesArray);
        }
        return enhanced;
    }

    /**
     * CGLib创建普通代理对象时，基于本类创建，本类就是代理类
     * 核心方法：重写的intercept
     */
    private static class EnhancedResultObjectProxyImpl implements MethodInterceptor {

        // 被代理类的类型
        private final Class<?> type;
        // 要懒加载的属性，触发加载后的会从中移除
        private final ResultLoaderMap lazyLoader;
        // 是否是aggressiveLazyLoading激进懒加载
        private final boolean aggressive;
        // 触发全局懒加载的方法（equals、hashCode、clone、toString），这四个方法名在Configuration中被初始化
        private final Set<String> lazyLoadTriggerMethods;
        // 对象工厂
        private final ObjectFactory objectFactory;
        // 被代理类构造函数的参数类型列表
        private final List<Class<?>> constructorArgTypes;
        // 被代理类构造函数的参数列表
        private final List<Object> constructorArgs;

        private EnhancedResultObjectProxyImpl(Class<?> type, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            this.type = type;
            this.lazyLoader = lazyLoader;
            this.aggressive = configuration.isAggressiveLazyLoading();
            this.lazyLoadTriggerMethods = configuration.getLazyLoadTriggerMethods();
            this.objectFactory = objectFactory;
            this.constructorArgTypes = constructorArgTypes;
            this.constructorArgs = constructorArgs;
        }

        public static Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            final Class<?> type = target.getClass();
            EnhancedResultObjectProxyImpl callback = new EnhancedResultObjectProxyImpl(type, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
            Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
            PropertyCopier.copyBeanProperties(type, target, enhanced);
            return enhanced;
        }

        /**
         * 代理类的拦截方法
         * <p>
         * 总结：
         * 对于非writeReplace方法和finalize方法。
         * 1. 如果设置了aggressiveLazyLoading属性为true，或调用的是toString、equals、hashCode、clone（触发全局加载），则加载所有未加载属性
         * 2. 如果调用的是setter方法设置对应属性，则该属性可以从懒加载属性列表删除了，因为已经有了新的值，旧的值加载已经没有意义。
         * 3. 如果调用的是getter方法，如果该属性尚未加载，则去加载该属性
         * 补：对于writeReplace方法，根据是否存在懒加载属性，决定是否通过CglibSerialStateHolder封装一下，以返回包含懒加载相关的信息。对于finalize方法，没有做任何处理
         *
         * @param enhanced    代理对象本身
         * @param method      被调用的方法
         * @param args        被调用方法的参数
         * @param methodProxy 用来调用父类的代理
         * @return 方法执行的返回值
         * @throws Throwable
         */
        @Override
        public Object intercept(Object enhanced, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            // 获取此次被调用的方法的名称
            final String methodName = method.getName();
            try {
                // 防止属性并发加载
                synchronized (lazyLoader) {
                    // 是writeReplace方法
                    if (WRITE_REPLACE_METHOD.equals(methodName)) {
                        // 创建一个原始对象
                        Object original;
                        // 是无参构造器，直接按类型创建
                        if (constructorArgTypes.isEmpty()) {
                            original = objectFactory.create(type);
                        } else { // 有参构造器，按类型、参数类型、参数值创建
                            original = objectFactory.create(type, constructorArgTypes, constructorArgs);
                        }
                        // 将被代理对象的属性拷贝到新创建的对象
                        PropertyCopier.copyBeanProperties(type, enhanced, original);
                        // 存在未加载的属性
                        if (lazyLoader.size() > 0) {
                            // 此时，不仅要返回原始对象，还需要返回未加载的属性信息，防止反序列化回来不知道哪些属性没加载。通过CglibSerialStateHolder进行封装后返回。
                            return new CglibSerialStateHolder(original, lazyLoader.getProperties(), objectFactory, constructorArgTypes, constructorArgs);
                        } else { // 没有未懒加载的属性，直接返回原对象进行序列化操作
                            return original;
                        }
                    } else {  // 调用的不是writeReplace方法
                        // 存在懒加载属性，且调用的不是finalize方法
                        if (lazyLoader.size() > 0 && !FINALIZE_METHOD.equals(methodName)) {
                            // 设置了aggressiveLazyLoading为true，或被调用的方法是能够触发所有懒加载属性全局加载的方法
                            if (aggressive || lazyLoadTriggerMethods.contains(methodName)) {
                                // 完成所有属性的加载
                                lazyLoader.loadAll();
                            } else if (PropertyNamer.isSetter(methodName)) { // 调用了属性的setter
                                // 先清除该属性的懒加载设置，已经不再需要懒加载了
                                final String property = PropertyNamer.methodToProperty(methodName);
                                lazyLoader.remove(property);
                            } else if (PropertyNamer.isGetter(methodName)) {  // 调用了属性的getter
                                final String property = PropertyNamer.methodToProperty(methodName);
                                // 如果该属性是懒加载属性，先加载属性，加载后会将属性从lazyLoader属性中删除
                                if (lazyLoader.hasLoader(property)) {
                                    lazyLoader.load(property);
                                }
                            }
                        }
                    }
                }
                // 触发被代理类的相应方法。
                return methodProxy.invokeSuper(enhanced, args);
            } catch (Throwable t) {
                throw ExceptionUtil.unwrapThrowable(t);
            }
        }
    }

    private static class EnhancedDeserializationProxyImpl extends AbstractEnhancedDeserializationProxy implements MethodInterceptor {

        private EnhancedDeserializationProxyImpl(Class<?> type, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                                 List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            super(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
        }

        public static Object createProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                         List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            final Class<?> type = target.getClass();
            EnhancedDeserializationProxyImpl callback = new EnhancedDeserializationProxyImpl(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
            Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
            PropertyCopier.copyBeanProperties(type, target, enhanced);
            return enhanced;
        }

        @Override
        public Object intercept(Object enhanced, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            final Object o = super.invoke(enhanced, method, args);
            return o instanceof AbstractSerialStateHolder ? o : methodProxy.invokeSuper(o, args);
        }

        @Override
        protected AbstractSerialStateHolder newSerialStateHolder(Object userBean, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                                                 List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            return new CglibSerialStateHolder(userBean, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
        }
    }

    private static class LogHolder {
        private static final Log log = LogFactory.getLog(CglibProxyFactory.class);
    }

}
