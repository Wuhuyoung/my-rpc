package com.han.rpc;

import com.han.rpc.config.RegistryConfig;
import com.han.rpc.config.RpcConfig;
import com.han.rpc.registry.Registry;
import com.han.rpc.registry.RegistryFactory;
import com.han.rpc.utils.ConfigUtils;
import lombok.extern.slf4j.Slf4j;

import static com.han.rpc.constant.RpcConstant.DEFAULT_CONFIG_PREFIX;

/**
 * RPC框架应用
 * 相当于holder，存放了项目全局用到的变量，使用double-check锁单例模式实现
 */
@Slf4j
public class RpcApplication {

    private static volatile RpcConfig rpcConfig;
    /**
     * 获取配置
     * @return
     */
    public static RpcConfig getRpcConfig() {
        if (rpcConfig == null) {
            synchronized (RpcApplication.class) {
                if (rpcConfig == null) {
                    init();
                }
            }
        }
        return rpcConfig;
    }

    /**
     * 初始化
     */
    public static void init() {
        RpcConfig rpcConfig = null;
        try {
            rpcConfig = ConfigUtils.loadConfig(RpcConfig.class, DEFAULT_CONFIG_PREFIX);
        } catch (Exception e) {
            // 配置加载失败，使用默认配置
            rpcConfig = new RpcConfig();
        }
        init(rpcConfig);
    }

    /**
     * 初始化，可以自定义配置类
     * @param newRpcConfig
     */
    public static void init(RpcConfig newRpcConfig) {
        rpcConfig = newRpcConfig;
        log.info("rpc init, config = {}", rpcConfig.toString());
        // 注册中心初始化
        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
        Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());
        registry.init(registryConfig);
        log.info("registry init, config = {}", registryConfig.toString());

        // 创建并注册Shutdown Hook，JVM退出时执行操作，清理资源
        Runtime.getRuntime().addShutdownHook(new Thread(registry::destroy));
    }
}
