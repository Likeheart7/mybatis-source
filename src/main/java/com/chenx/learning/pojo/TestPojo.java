package com.chenx.learning.pojo;

import java.util.Arrays;

public class TestPojo {
    String nameInfo;
    public static String address;


    public String getA() {
        return nameInfo;
    }

    public void setA(String a) {
        nameInfo = a;
    }

    public void say() {
        System.out.println("i am testpojo");
    }

    public static void main(String[] args) {
        Arrays.stream(SubTestPojo.class.getDeclaredMethods()).forEach(System.out::println);
    }
}

class SubTestPojo extends TestPojo {
    @Override
    public void say() {
        System.out.println("i am SubTestPojo");
    }
}
