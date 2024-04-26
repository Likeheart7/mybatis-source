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

// 拦截器需要添加到mybatis-config.xml中来完成注册
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

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        RoutingStatementHandler target = (RoutingStatementHandler) invocation.getTarget();
        BoundSql boundSql = target.getBoundSql();
        String originSql = boundSql.getSql();
        System.out.println("原始sql： " + originSql);
        Field sql = BoundSql.class.getDeclaredField("sql");
        sql.setAccessible(true);
        sql.set(boundSql, originSql + " limit 1, 1");
        System.out.println(boundSql.getSql());
        System.out.println("查询开始执行");
        return invocation.proceed();
    }
}
