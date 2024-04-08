package com.han.rpc.loadbalancer;

import com.han.rpc.spi.SpiLoader;

/**
 * 负载均衡器工厂（用于获取负载均衡器对象）
 */
public class LoadBalancerFactory {

    static {
        // 不需要一次性加载所有类，每次getInstance时再动态去加载
//        SpiLoader.load(LoadBalancer.class);
    }

    /**
     * 默认负载均衡器
     */
    private static final LoadBalancer DEFAULT_LOAD_BALANCER = new RoundRobinLoadBalancer();

    /**
     * 获取实例
     * @param key
     * @return
     */
    public static LoadBalancer getInstance(String key) {
        return SpiLoader.getInstance(LoadBalancer.class, key);
    }
}
