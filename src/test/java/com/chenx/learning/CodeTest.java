package com.chenx.learning;

import com.chenx.learning.pojo.Address;
import com.chenx.learning.pojo.TestPojo;
import org.apache.ibatis.reflection.Reflector;
import org.junit.jupiter.api.Test;

public class CodeTest {
    @Test
    public void testAddGetMethods() {
        new Reflector(Address.class);
    }
}
