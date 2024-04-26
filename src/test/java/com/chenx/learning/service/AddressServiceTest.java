package com.chenx.learning.service;

import com.chenx.learning.dao.AddressMapper;
import com.chenx.learning.util.DaoUtils;
import org.junit.jupiter.api.Test;

public class AddressServiceTest {
    @Test
    public void testBatch() {
        Integer result = DaoUtils.execute(sqlSession -> {
            AddressMapper mapper = sqlSession.getMapper(AddressMapper.class);
            return mapper.updateBatch();
        });
        System.out.println(result);
    }
}
