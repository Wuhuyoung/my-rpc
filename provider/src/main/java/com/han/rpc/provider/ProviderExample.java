package com.han.rpc.provider;

import com.han.rpc.bootstrap.ProviderBootstrap;
import com.han.rpc.common.service.UserService;
import com.han.rpc.model.ServiceRegisterInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务提供者示例
 */
public class ProviderExample {
    public static void main(String[] args) {
        // 要注册的服务
        List<ServiceRegisterInfo<?>> serviceRegisterInfoList = new ArrayList<>();
        ServiceRegisterInfo<UserService> service =
                new ServiceRegisterInfo<>(UserService.class.getName(), UserServiceImpl.class);
        serviceRegisterInfoList.add(service);

        // 服务提供者初始化
        ProviderBootstrap.init(serviceRegisterInfoList);
    }
}
