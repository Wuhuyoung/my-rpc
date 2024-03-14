package com.han.rpc.consumer;

import com.han.rpc.config.RpcConfig;
import com.han.rpc.utils.ConfigUtils;

import static com.han.rpc.constant.RpcConstant.DEFAULT_CONFIG_PREFIX;


/**
 * 服务消费者示例
 */
public class ConsumerExample {
    public static void main(String[] args) {
        // 测试配置文件的读取
        RpcConfig rpcConfig = ConfigUtils.loadConfig(RpcConfig.class, DEFAULT_CONFIG_PREFIX);
        System.out.println(rpcConfig);
    }
}
