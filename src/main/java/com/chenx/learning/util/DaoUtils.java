package com.chenx.learning.util;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

public class DaoUtils {
    private static final SqlSessionFactory factory;

    static {
        String resource = "mybatis-config.xml";
        InputStream inputStream = null;
        try {
            inputStream = Resources.getResourceAsStream(resource);
        } catch (IOException e) {
            System.err.println("read mybatis-config.xml failed");
            e.printStackTrace();
            System.exit(1);
        }
//        在build方法内部关闭了inputStream，无需在此处手动关闭
//        根据加载的mybatis-config.xml配置文件，创建SqlSessionFactory对象
//        build方法内，会触发MyBatis加载的全流程
        factory = new SqlSessionFactoryBuilder()
                .build(inputStream);
    }

    public static <R> R execute(Function<SqlSession, R> function) {
//        通过sqlSessionFactory获取一个数据库连接
        SqlSession session = factory.openSession();
        try {
            R apply = function.apply(session);
//            事务提交
            session.commit();
            return apply;
        } catch (Throwable t) {
//            出现异常回滚事务
            session.rollback();
            System.out.println("execute error");
            throw t;
        } finally {
            session.close();
        }
    }
}
