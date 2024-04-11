package com.han.rpc.springboot.starter.bootstrap;

import com.han.rpc.proxy.ServiceProxyFactory;
import com.han.rpc.springboot.starter.annotation.RpcReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;

/**
 * Rpc服务消费者启动
 */
public class RpcConsumerBootstrap implements BeanPostProcessor {
    /**
     * bean初始化后执行，注入服务
     *
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        // 反射获取对象所有属性
        Field[] fields = beanClass.getDeclaredFields();
        for (Field field : fields) {
            RpcReference rpcReference = field.getAnnotation(RpcReference.class);
            if (rpcReference != null) {
                // 为属性生成代理对象
                Class<?> interfaceClass = rpcReference.interfaceClass();
                if (interfaceClass == void.class) {
                    interfaceClass = field.getType();
                }
                Object proxy = ServiceProxyFactory.getProxy(interfaceClass);
                try {
                    field.setAccessible(true);
                    field.set(bean, proxy);
                    field.setAccessible(false);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("为字段注入代理对象失败", e);
                }
            }
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
