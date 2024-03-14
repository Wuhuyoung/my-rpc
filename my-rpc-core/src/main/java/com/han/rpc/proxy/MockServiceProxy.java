package com.han.rpc.proxy;

import com.github.javafaker.Faker;
import com.github.javafaker.Number;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Mock服务代理（JDK动态代理）
 */
@Slf4j
public class MockServiceProxy implements InvocationHandler {

    /**
     * 调用代理
     * @param proxy
     * @param method
     * @param args
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> returnType = method.getReturnType();
        log.info("mock invoke: {}", method.getName());
        return getDefaultType(returnType);
    }

    /**
     * 生成指定类型的默认值对象
     * @param type
     * @return
     */
    private Object getDefaultType(Class<?> type) {
        if (type.isPrimitive()) {
            Faker faker = new Faker();
            Number number = faker.number();
            if (type == boolean.class) {
                return false;
            } else if (type == int.class) {
                return number.randomDigit();
            } else if (type == long.class) {
                return number.randomNumber();
            } else if (type == short.class) {
                return (short) number.randomNumber(4, false);
            } else if (type == byte.class) {
                return (byte) number.randomNumber(2, false);
            } else if (type == double.class) {
                return number.randomDouble(2, 0, 1000);
            } else if (type == char.class) {
                return faker.name().firstName().charAt(0);
            }
        }
        return null;
    }
}
