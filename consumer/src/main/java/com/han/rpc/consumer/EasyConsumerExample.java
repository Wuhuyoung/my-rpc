package com.han.rpc.consumer;

import com.han.rpc.common.model.User;
import com.han.rpc.common.service.UserService;
import com.han.rpc.proxy.ServiceProxyFactory;

/**
 * 简单服务消费者示例
 */
public class EasyConsumerExample {
    public static void main(String[] args) {
        // 需要获取userService的代理对象
        UserService userService = ServiceProxyFactory.getProxy(UserService.class);
        User user = new User();
        user.setName("小明");

        User newUser = userService.getUser(user);
        if (newUser != null) {
            System.out.println("newUser:" + newUser.getName());
        } else {
            System.out.println("user 为 null");
        }
    }
}
