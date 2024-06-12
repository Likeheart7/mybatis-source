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
package org.apache.ibatis.reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * @author Clinton Begin
 * 一个异常拆包工具，将InvocationTargetException和UndeclaredThrowableException内包装的真正异常拆出来返回
 */
public class ExceptionUtil {

    private ExceptionUtil() {
        // Prevent Instantiation
    }

    /**
     * 拆出包装的真正异常
     * InvocationTargetException 是 Java 反射机制中的异常，当使用反射调用方法时，如果被调用的方法本身抛出了异常，Java 会把这个异常封装在 InvocationTargetException 中。
     * UndeclaredThrowableException 是 Java 动态代理机制中的异常，当通过动态代理调用方法时，如果方法抛出一个非检查异常（unchecked exception），
     * Java 会把这个异常封装在 UndeclaredThrowableException 中
     */
    public static Throwable unwrapThrowable(Throwable wrapped) {
        Throwable unwrapped = wrapped;
        /*
        代理类在进行反射操作时发生异常，于是异常被包装成 InvocationTargetException。
        InvocationTargetException显然没有在共同接口或者父类方法中声明过，
        于是又被包装成了UndeclaredThrowableException。这样，真正的异常就被包装了两层
        这也是为什么在ExceptionUtil的unwrapThrowable方法中存在一个“while （true）”死循环来持续拆包
         */
        while (true) {
            if (unwrapped instanceof InvocationTargetException) {
                unwrapped = ((InvocationTargetException) unwrapped).getTargetException();
            } else if (unwrapped instanceof UndeclaredThrowableException) {
                unwrapped = ((UndeclaredThrowableException) unwrapped).getUndeclaredThrowable();
            } else {
                return unwrapped;
            }
        }
    }

}
