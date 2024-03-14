package com.han.rpc.provider;

import com.han.rpc.RpcApplication;
import com.han.rpc.common.service.UserService;
import com.han.rpc.register.LocalRegister;
import com.han.rpc.server.VerxHttpServer;

/**
 * 简单服务提供者示例
 */
public class EasyProviderExample {
    public static void main(String[] args) {
        // RPC框架初始化
        RpcApplication.init();
        // 注册服务
        LocalRegister.register(UserService.class.getName(), UserServiceImpl.class);
        // 启动服务器
        VerxHttpServer httpServer = new VerxHttpServer();
        httpServer.doStart(RpcApplication.getRpcConfig().getServerPort());
    }
}
