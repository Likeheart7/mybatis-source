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
package org.apache.ibatis.datasource;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Clinton Begin
 * 数据源得工厂方法接口，各种数据源实现这个接口
 */
public interface DataSourceFactory {
    /**
     * 用于设置工厂的属性
     *
     * @param props
     */
    void setProperties(Properties props);

    /**
     * 获取具体数据源实例
     *
     * @return 返回数据源
     */
    DataSource getDataSource();

}
