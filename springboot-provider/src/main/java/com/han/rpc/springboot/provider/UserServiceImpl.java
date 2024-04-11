package com.han.rpc.springboot.provider;

import com.han.rpc.common.model.User;
import com.han.rpc.common.service.UserService;
import com.han.rpc.springboot.starter.annotation.RpcService;
import org.springframework.stereotype.Service;

@Service
@RpcService
public class UserServiceImpl implements UserService {
    @Override
    public User getUser(User user) {
        System.out.println("springboot.provider.UserServiceImpl 用户名：" + user.getName());
        return user;
    }
}
