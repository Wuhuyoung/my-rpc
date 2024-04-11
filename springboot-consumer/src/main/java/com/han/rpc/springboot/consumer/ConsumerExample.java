package com.han.rpc.springboot.consumer;

import com.han.rpc.common.model.User;
import com.han.rpc.common.service.UserService;
import com.han.rpc.springboot.starter.annotation.RpcReference;
import org.springframework.stereotype.Service;

@Service
public class ConsumerExample {

    @RpcReference
    private UserService userService;

    public void testUserService() {
        User user = new User();
        user.setName("张三");
        User serviceUser = userService.getUser(user);
        if (serviceUser != null) {
            System.out.println("获取到用户:" + serviceUser);
        } else {
            System.out.println("调用服务出错");
        }
    }
}
