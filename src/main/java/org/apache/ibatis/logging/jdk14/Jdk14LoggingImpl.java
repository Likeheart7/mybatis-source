/**
 * Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.logging.jdk14;

import org.apache.ibatis.logging.Log;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Clinton Begin
 * 内部维护了 java.util.logging.Logger类，来实现作为适配器的功能
 */
public class Jdk14LoggingImpl implements Log {

    // 具体的日志对象
    private final Logger log;

    //  构造该类的时候就给log属性赋值，是一个java.util.logging.Logger的实例。
    public Jdk14LoggingImpl(String clazz) {
        log = Logger.getLogger(clazz);
    }


    /* ====  下面的方法就是将Log接口的方法的实现，通过委托给java.util.logging.Logger的具体方法来实现日志的输出 ==== */
    @Override
    public boolean isDebugEnabled() {
        return log.isLoggable(Level.FINE);
    }

    @Override
    public boolean isTraceEnabled() {
        return log.isLoggable(Level.FINER);
    }

    @Override
    public void error(String s, Throwable e) {
        log.log(Level.SEVERE, s, e);
    }

    @Override
    public void error(String s) {
        log.log(Level.SEVERE, s);
    }

    @Override
    public void debug(String s) {
        log.log(Level.FINE, s);
    }

    @Override
    public void trace(String s) {
        log.log(Level.FINER, s);
    }

    @Override
    public void warn(String s) {
        log.log(Level.WARNING, s);
    }

}
