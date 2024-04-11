package com.chenx.learning.serivce;


import com.chenx.learning.dao.AddressMapper;
import com.chenx.learning.dao.CustomerMapper;
import com.chenx.learning.pojo.Address;
import com.chenx.learning.pojo.Customer;
import com.chenx.learning.util.DaoUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.List;

public class CustomerService {
    /**
     * register a user
     *
     * @param name  username
     * @param phone user's phone number
     * @return
     */
    public long register(String name, String phone) {
//        参数校验
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name is empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(phone), "phone is empty");
        return DaoUtils.execute(sqlSession -> {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            Customer customer = new Customer();
            customer.setName(name);
            customer.setPhone(phone);
            int affected = mapper.save(customer);
            if (affected <= 0) {
                throw new RuntimeException("Save customer failed...");
            }
            return customer.getId();
        });
    }

    /**
     * 给指定用户添加一个地址
     *
     * @param customerId 用户id
     * @param street     街道
     * @param city       城市
     * @param country    国家
     * @return 添加的地址对应的id
     */
    public long addAddress(long customerId, String street, String city, String country) {
//        参数校验，可以放在Controller层做
        Preconditions.checkArgument(customerId > 0, "customer id [" + customerId + "] is illegal");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(street), "street is empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(city), "city is empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(country), "country is empty");
//        执行添加地址的业务逻辑
        return DaoUtils.execute(sqlSession -> {
            AddressMapper mapper = sqlSession.getMapper(AddressMapper.class);
            Address address = new Address();
            address.setCity(city);
            address.setCustomerId(customerId);
            address.setStreet(street);
            address.setCountry(country);
            int affected = mapper.save(address, customerId);
            if (affected <= 0) throw new RuntimeException("Save Address failed");
            return address.getId();
        });
    }

    /**
     * 根据customerId获取所有地址
     *
     * @param customerId 用户Id
     * @return 所有和该用户关联的地址
     */
    public List<Address> findAllAddress(long customerId) {
        Preconditions.checkArgument(customerId > 0, "customerId [" + customerId + "] is illegal");
        return DaoUtils.execute(sqlSession -> {
            AddressMapper mapper = sqlSession.getMapper(AddressMapper.class);
            return mapper.findAll(customerId);
        });
    }


    /**
     * 根据customerId获取指定用户
     *
     * @param id customerId
     * @return id对应的Customer信息
     */
    public Customer find(long id) {
        Preconditions.checkArgument(id > 0, "customerId [" + id + "] is illegal");
        return DaoUtils.execute(sqlSession -> {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            return mapper.find(id);
        });
    }

    /**
     * 根据customerId获取用户信息，包括用户的地址
     *
     * @param id customerId
     * @return id对应的Customer信息
     */
    public Customer findWithAddress(long id) {
        Preconditions.checkArgument(id > 0, "customerId [" + id + "] is illegal");
        return DaoUtils.execute(sqlSession -> {
            CustomerMapper mapper = sqlSession.getMapper(CustomerMapper.class);
            return mapper.findWithAddress(id);
        });
    }
}
