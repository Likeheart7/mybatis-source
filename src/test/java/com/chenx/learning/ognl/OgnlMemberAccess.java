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
package com.chenx.learning.ognl;

import ognl.MemberAccess;
import org.apache.ibatis.reflection.Reflector;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.util.Map;

/**
 * MyBatis提供的一个MemberAccess的实现
 */
class OgnlMemberAccess implements MemberAccess {

    // 当前环境下，是否能通过反射修改对象属性的可访问性
    private final boolean canControlMemberAccessible;

    OgnlMemberAccess() {
        this.canControlMemberAccessible = Reflector.canControlMemberAccessible();
    }

    @Override
    public Object setup(Map context, Object target, Member member, String propertyName) {
        Object result = null;
        // 如果允许修改属性的可访问性
        if (isAccessible(context, target, member, propertyName)) {
            AccessibleObject accessible = (AccessibleObject) member;
            // 如果属性原本不可访问
            if (!accessible.isAccessible()) {
                result = Boolean.FALSE;
                // 允许属性访问
                accessible.setAccessible(true);
            }
        }
        return result;
    }

    /**
     * 将属性的可访问性恢复到指定状态
     * 因为线程不安全，该方法在该版本清空了
     *
     * @param context      环境上下文
     * @param target       目标对象
     * @param member       目标成员
     * @param propertyName 属性名称
     * @param state        指定的状态
     */
    @Override
    public void restore(Map context, Object target, Member member, String propertyName,
                        Object state) {
        // Flipping accessible flag is not thread safe. See #1648
    }

    /**
     * 判断对象属性是否可访问
     *
     * @param context      环境上下文
     * @param target       目标对象
     * @param member       目标成员
     * @param propertyName 属性名称
     * @return 是否可访问
     */
    @Override
    public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
        return canControlMemberAccessible;
    }

}
