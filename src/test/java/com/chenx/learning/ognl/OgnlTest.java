package com.chenx.learning.ognl;

import com.chenx.learning.pojo.Customer;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

public class OgnlTest {
    @Test
    public void testOgnl() throws OgnlException {
        // ognl依赖包的没有提供默认MemberAccess实现，复制作者提供的默认实现，或Mybatis在scripting.xmltags包中提供的实现，并在调用方法时传入。
//        OgnlContext context = new OgnlContext(null, null, new DefaultMemberAccess(true));
        OgnlContext context = new OgnlContext(null, null, new OgnlMemberAccess());
        Customer c1 = new Customer(1L, "陈星", "110");
        Customer c2 = new Customer(2L, "陈生", "120");
        HashMap<String, Customer> map = new HashMap<>();
        map.put("custom1", c1);
        map.put("custom2", c2);
        // 通过OGNL查询属性
        // 读取根对象属性值
        System.out.println("通过ognl得到的name值：" + Ognl.getValue("name", context, map.get("custom1")));
        // 设置根对象属性
        Ognl.getValue("phone = 1111110000", context, map.get("custom2"));
        System.out.println("修改后的phone：" + Ognl.getValue("phone", context, map.get("custom2")));
    }
}
