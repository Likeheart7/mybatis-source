package com.chenx.learning.plugin;

import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Properties;

// 拦截器需要添加到mybatis-config.xml中来完成注册
// 每个@Signature都声明了当前拦截器要拦截的方法，表示拦截type类型中的method方法，参数类型列表是args，多个@Signature方法表示拦截多个方法
@Intercepts({
        // @Signature 注解用来指定 DemoPlugin 插件实现类要拦截的目标方法信息，可以有多个
//        @Signature(type = Executor.class, method = "query", args = {
//                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
//        }),
//        @Signature(type = Executor.class, method = "close", args = {boolean.class})
        @Signature(method = "prepare", type = StatementHandler.class, args = {Connection.class, Integer.class})}
)
public class DemoPlugin implements Interceptor {
    private int logLevel;

    /**
     * 被拦截的方法会转到来执行这个方法
     * 给sql加上limit 1, 1子句
     *
     * @param invocation
     * @return
     * @throws Throwable
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        RoutingStatementHandler target = (RoutingStatementHandler) invocation.getTarget();
        BoundSql boundSql = target.getBoundSql();
        String originSql = boundSql.getSql();
        System.out.println("原始sql： " + originSql);
        Field sql = BoundSql.class.getDeclaredField("sql");
        sql.setAccessible(true);
//        sql.set(boundSql, originSql + " limit 1, 1");
        System.out.println(boundSql.getSql());
        System.out.println("查询开始执行");
        // 执行原有方法并返回。
        return invocation.proceed();
    }

    /**
     * 为拦截器设置属性
     *
     * @param properties 配置属性
     */
    @Override
    public void setProperties(Properties properties) {
        Interceptor.super.setProperties(properties);
    }
}
