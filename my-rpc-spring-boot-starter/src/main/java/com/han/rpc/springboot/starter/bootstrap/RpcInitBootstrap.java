package com.han.rpc.springboot.starter.bootstrap;

import com.han.rpc.RpcApplication;
import com.han.rpc.config.RpcConfig;
import com.han.rpc.server.tcp.VertxTcpServer;
import com.han.rpc.springboot.starter.annotation.EnableRpc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * RPC 框架启动
 * 获取 @EnableRpc 注解的属性，初始化 RPC 框架
 */
@Slf4j
public class RpcInitBootstrap implements ImportBeanDefinitionRegistrar {

    /**
     * Spring初始化时执行
     * @param importingClassMetadata
     * @param registry
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 获取 EnableRpc 注解属性
        boolean needServer = (boolean) importingClassMetadata.getAnnotationAttributes(EnableRpc.class.getName())
                .get("needServer");

        // RPC框架初始化（配置和注册中心）
        RpcApplication.init();
        // 全局配置
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();

        if (needServer) {
            // 启动服务器
            VertxTcpServer tcpServer = new VertxTcpServer();
            tcpServer.doStart(RpcApplication.getRpcConfig().getServerPort());
        } else {
            log.info("不启动server");
        }
    }
}
