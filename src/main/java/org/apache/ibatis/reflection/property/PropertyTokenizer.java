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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 * 解析由“.”和“[]”构成的表达式，类似user[uId].name。PropertyTokenizer 继承了 Iterator 接口，可以迭代处理嵌套多层表达式。
 * 一个属性分词器，将变量名，索引名，属性名拆分出来
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
    // user[uId].name的拆分结果为
    //  user
    private String name;
    //  user[uId]
    private final String indexedName;
    // uId
    private String index;
    // name
    private final String children;

    public PropertyTokenizer(String fullname) {
        int delim = fullname.indexOf('.');
        // 如果不是-1，说明存在.，即存在children
        if (delim > -1) {
            // 获取user[uId]
            name = fullname.substring(0, delim);
            // 获取name
            children = fullname.substring(delim + 1);
        } else {
            name = fullname;
            children = null;
        }
        // 拆出user[uId]
        indexedName = name;
        delim = name.indexOf('[');
        // 不为-1说明包含了[]索引取值
        if (delim > -1) {
            // 拿到uId
            index = name.substring(delim + 1, name.length() - 1);
            // 拿到user
            name = name.substring(0, delim);
        }
    }

    public String getName() {
        return name;
    }

    public String getIndex() {
        return index;
    }

    public String getIndexedName() {
        return indexedName;
    }

    public String getChildren() {
        return children;
    }

    @Override
    public boolean hasNext() {
        return children != null;
    }

    @Override
    public PropertyTokenizer next() {
        return new PropertyTokenizer(children);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
    }
}
