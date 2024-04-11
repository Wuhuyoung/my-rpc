package com.han.rpc.provider;

import com.han.rpc.RpcApplication;
import com.han.rpc.common.service.UserService;
import com.han.rpc.register.LocalRegistry;
import com.han.rpc.server.VertxHttpServer;

/**
 * 简单服务提供者示例
 */
public class EasyProviderExample {
    public static void main(String[] args) {
        // RPC框架初始化
        RpcApplication.init();
        // 注册服务
        LocalRegistry.register(UserService.class.getName(), UserServiceImpl.class);
        // 启动服务器
        VertxHttpServer httpServer = new VertxHttpServer();
        httpServer.doStart(RpcApplication.getRpcConfig().getServerPort());
    }
}
