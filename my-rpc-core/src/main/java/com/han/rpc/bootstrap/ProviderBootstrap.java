package com.han.rpc.bootstrap;

import com.han.rpc.RpcApplication;
import com.han.rpc.config.RpcConfig;
import com.han.rpc.model.ServiceMetaInfo;
import com.han.rpc.model.ServiceRegisterInfo;
import com.han.rpc.register.LocalRegistry;
import com.han.rpc.registry.Registry;
import com.han.rpc.registry.RegistryFactory;
import com.han.rpc.server.tcp.VertxTcpServer;

import java.util.List;

/**
 * 服务提供者初始化
 */
public class ProviderBootstrap {
    /**
     * 初始化
     * @param serviceMetaInfoList
     */
    public static void init(List<ServiceRegisterInfo<?>> serviceMetaInfoList) {
        // RPC框架初始化（配置和注册中心）
        RpcApplication.init();

        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());

        // 注册服务
        for (ServiceRegisterInfo<?> serviceRegisterInfo : serviceMetaInfoList) {
            // 本地注册
            LocalRegistry.register(serviceRegisterInfo.getServiceName(), serviceRegisterInfo.getImplClass());

            // 注册服务到注册中心
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceRegisterInfo.getServiceName());
            serviceMetaInfo.setServiceVersion("1.0");
            serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
            serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
            try {
                registry.register(serviceMetaInfo);
            } catch (Exception e) {
                throw new RuntimeException(serviceRegisterInfo.getServiceName() + "注册失败", e);
            }
        }

        // 启动服务器
        VertxTcpServer tcpServer = new VertxTcpServer();
        tcpServer.doStart(RpcApplication.getRpcConfig().getServerPort());
    }
}
