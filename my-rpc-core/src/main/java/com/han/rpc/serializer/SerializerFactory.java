package com.han.rpc.serializer;

import com.han.rpc.spi.SpiLoader;

/**
 * 序列化器工厂（用于获取序列化器对象）
 */
public class SerializerFactory {

    static {
        // 不需要一次性加载所有类，每次getInstance时再动态去加载
//        SpiLoader.load(Serializer.class);
    }

    /**
     * 默认序列化器
     */
    private static final Serializer DEFAULT_SERIALIZER = new JdkSerializer();

    /**
     * 获取实例
     * @param key
     * @return
     */
    public static Serializer getInstance(String key) {
        return SpiLoader.getInstance(Serializer.class, key);
    }
}
