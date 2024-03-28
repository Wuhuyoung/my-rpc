package com.han.rpc.registry;

import com.han.rpc.config.RegistryConfig;
import com.han.rpc.model.ServiceMetaInfo;

import java.util.List;

/**
 * 注册中心
 */
public interface Registry {
    /**
     * 初始化
     * @param registryConfig
     */
    void init(RegistryConfig registryConfig);

    /**
     * 服务注册（服务端）
     * @param serviceMetaInfo
     */
    void register(ServiceMetaInfo serviceMetaInfo) throws Exception;

    /**
     * 服务注销（服务端）
     * @param serviceMetaInfo
     */
    void unRegister(ServiceMetaInfo serviceMetaInfo);

    /**
     * 服务发现（消费端）
     * @param serviceKey 服务键名
     * @return
     */
    List<ServiceMetaInfo> serviceDiscovery(String serviceKey);

    /**
     * 心跳检测（服务端）
     */
    void heartBeat();

    /**
     * 监听（消费端）
     * @param serviceNodeKey
     * @param serviceKey
     */
    void watch(String serviceNodeKey, String serviceKey);

    /**
     * 服务销毁
     */
    void destroy();
}
