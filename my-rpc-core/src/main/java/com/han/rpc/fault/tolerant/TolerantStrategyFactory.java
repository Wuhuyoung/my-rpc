package com.han.rpc.fault.tolerant;

import com.han.rpc.spi.SpiLoader;

/**
 * 容错策略工厂（用于获取容错策略对象）
 */
public class TolerantStrategyFactory {

    static {
        // 不需要一次性加载所有类，每次getInstance时再动态去加载
//        SpiLoader.load(TolerantStrategy.class);
    }

    /**
     * 默认容错策略
     */
    private static final TolerantStrategy DEFAULT_TOLERANT_STRATEGY = new FailFastTolerantStrategy();

    /**
     * 获取实例
     * @param key
     * @return
     */
    public static TolerantStrategy getInstance(String key) {
        return SpiLoader.getInstance(TolerantStrategy.class, key);
    }
}
