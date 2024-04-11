package com.han.rpc.springboot.starter.bootstrap;

import com.han.rpc.RpcApplication;
import com.han.rpc.config.RpcConfig;
import com.han.rpc.model.ServiceMetaInfo;
import com.han.rpc.register.LocalRegistry;
import com.han.rpc.registry.Registry;
import com.han.rpc.registry.RegistryFactory;
import com.han.rpc.springboot.starter.annotation.RpcService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Rpc服务提供者启动
 */
public class RpcProviderBootstrap implements BeanPostProcessor {

    /**
     * bean初始化后执行，获取到所有包含@RpcService的类，注册服务
     *
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        RpcService rpcService = beanClass.getAnnotation(RpcService.class);

        if (rpcService != null) {
            // 需要注册服务
            // 1.获取服务信息
            Class<?> interfaceClass = rpcService.interfaceClass();
            //   默认值处理
            if (interfaceClass == void.class) {
                interfaceClass = beanClass.getInterfaces()[0];
            }
            String serviceVersion = rpcService.serviceVersion();
            String serviceName = interfaceClass.getName();

            // 2.注册服务
            //   本地注册
            LocalRegistry.register(serviceName, beanClass);

            //   注册到注册中心
            RpcConfig rpcConfig = RpcApplication.getRpcConfig();
            Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceName);
            serviceMetaInfo.setServiceVersion(serviceVersion);
            serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
            serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
            try {
                registry.register(serviceMetaInfo);
            } catch (Exception e) {
                throw new RuntimeException(serviceName + "注册失败", e);
            }
        }

        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
