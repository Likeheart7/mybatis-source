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
package org.apache.ibatis.builder;

import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;

import java.util.List;

/**
 * @author Eduardo Macarron
 * 处理ResultMap标签的继承。
 */
/*
一个result-map标签继承的示例。girlUserMap继承userMap
  <resultMap id="userMap" type="User" autoMapping-"false">
    <id property="id" column="id" javaType="Integer" jdbcType="INTEGER" typeHandler="org.apache.ibatis.type.IntegerTypeHandler"/>
    <result property="name" column="name"/>
    <discriminator javaType="int" column="sex">
      <case value="0" resultMap="boyUserMap"/>
      <case value="1" resultMap="girlUserMap"/>
    </discriminator>
  </resultMap>
  <resultMap id="girlUserMap" type="Girl" extends="userMap">
    <result property="email" column="email"/>
  </resultMMap>
 */
public class ResultMapResolver {
    // 建造者辅助类，为本类提供addResultMap方法
    private final MapperBuilderAssistant assistant;
    // 下面是被解析的属性
    private final String id;  // ResultMap的id
    private final Class<?> type;  // ResultMap目标对象类型
    private final String extend;  // ResultMap的extends属性，标识继承来源
    private final Discriminator discriminator;  // ResultMap的discriminator节点，鉴别器
    private final List<ResultMapping> resultMappings; // ResultMap的属性映射列表
    private final Boolean autoMapping;  // ResultMapd额autoMapping属性，是否开启自动映射

    public ResultMapResolver(MapperBuilderAssistant assistant, String id, Class<?> type, String extend, Discriminator discriminator, List<ResultMapping> resultMappings, Boolean autoMapping) {
        this.assistant = assistant;
        this.id = id;
        this.type = type;
        this.extend = extend;
        this.discriminator = discriminator;
        this.resultMappings = resultMappings;
        this.autoMapping = autoMapping;
    }

    /**
     * ResultMap的继承是通过MapperBuilderAssistant#addResultMap来实现的
     */
    public ResultMap resolve() {
        return assistant.addResultMap(this.id, this.type, this.extend, this.discriminator, this.resultMappings, this.autoMapping);
    }

}
