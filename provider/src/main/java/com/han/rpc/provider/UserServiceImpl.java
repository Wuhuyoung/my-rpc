package com.han.rpc.provider;

import com.han.rpc.common.model.User;
import com.han.rpc.common.service.UserService;

/**
 * 用户服务实现类
 */
public class UserServiceImpl implements UserService {
    public User getUser(User user) {
        System.out.println("用户名为" + user.getName());
        return user;
    }
}
