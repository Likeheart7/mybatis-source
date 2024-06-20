package com.chenx.learning.dao.provider;

import org.apache.ibatis.jdbc.SQL;

public class CustomerMapperProvider {
    /**
     * 为@*Provider提供sql字符串的方法，一般选择通过{@link SQL 类来构建}
     *
     * @return
     */
    public String findCustomerById() {
        return new SQL()
                .SELECT("*")
                .FROM("t_customer")
                .WHERE("id = #{id}")
                .toString(); // 真正调用拼接的地方
    }
}
