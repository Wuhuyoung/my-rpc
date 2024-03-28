package com.han.rpc.config;

import com.han.rpc.serializer.SerializerKey;
import lombok.Data;

/**
 * RPC框架配置
 */
@Data
public class RpcConfig {
    /**
     * 名称
     */
    private String name = "my-rpc";

    /**
     * 版本
     */
    private String version = "1.0";

    /**
     * 服务器主机名
     */
    private String serverHost = "localhost";

    /**
     * 服务器端口号
     */
    private Integer serverPort = 8080;

    /**
     * 模拟调用
     */
    private boolean mock = false;

    /**
     * 序列化器
     */
    private String serializer = SerializerKey.JDK;

    /**
     * 注册中心配置
     */
    private RegistryConfig registryConfig;
}
