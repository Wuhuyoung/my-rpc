package com.han.rpc.registry;

import com.han.rpc.model.ServiceMetaInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册中心服务本地缓存
 */
public class RegistryServiceCache {

    /**
     * 服务缓存
     */
    private Map<String, List<ServiceMetaInfo>> serviceCache = new HashMap<>();

    /**
     * 写缓存
     * @param serviceKey
     * @param newServiceCache
     */
    public void writeCache(String serviceKey, List<ServiceMetaInfo> newServiceCache) {
        serviceCache.put(serviceKey, newServiceCache);
    }

    /**
     * 读缓存
     * @param serviceKey
     * @return
     */
    public List<ServiceMetaInfo> readCache(String serviceKey) {
        return serviceCache.get(serviceKey);
    }

    /**
     * 删除某个key的缓存
     * @param serviceKey
     */
    public void deleteCache(String serviceKey) {
        List<ServiceMetaInfo> remove = serviceCache.remove(serviceKey);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        serviceCache.clear();
    }
}
