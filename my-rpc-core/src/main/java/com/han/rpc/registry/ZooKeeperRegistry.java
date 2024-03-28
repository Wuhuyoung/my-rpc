package com.han.rpc.registry;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.han.rpc.config.RegistryConfig;
import com.han.rpc.model.ServiceMetaInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * zookeeper 服务注册中心
 * 操作文档：<a href="https://curator.apache.org/docs/getting-started">Apache Curator</a>
 * 代码示例：<a href="https://github.com/apache/curator/blob/master/curator-examples/src/main/java/discovery/DiscoveryExample.java">DiscoveryExample.java</a>
 * 监听 key 示例：<a href="https://github.com/apache/curator/blob/master/curator-examples/src/main/java/cache/CuratorCacheExample.java">CuratorCacheExample.java</a>
 */
@Slf4j
public class ZooKeeperRegistry implements Registry {
    private CuratorFramework client;
    private ServiceDiscovery<ServiceMetaInfo> serviceDiscovery;

    /**
     * 本机注册的节点key集合（用于维护续期，服务端）
     */
    private final Set<String> localRegisterNodeKeySet = new HashSet<>();

    /**
     * 注册中心服务缓存（消费端）
     */
    private final RegistryServiceCache serviceCache = new RegistryServiceCache();

    /**
     * 正在监听的key集合（消费端）
     */
    private final Set<String> watchingKeySet = new ConcurrentHashSet<>();

    /**
     * 根节点
     */
    private static final String ZK_ROOT_PATH = "/rpc/zk";

    /**
     * 初始化
     * @param registryConfig
     */
    @Override
    public void init(RegistryConfig registryConfig) {
        // 构建 client 实例
        client = CuratorFrameworkFactory
                .builder()
                .connectString(registryConfig.getAddress())
                .retryPolicy(new ExponentialBackoffRetry(Math.toIntExact(registryConfig.getTimeout()), 3))
                .build();

        // 构建 serviceDiscovery 实例
        serviceDiscovery = ServiceDiscoveryBuilder
                .builder(ServiceMetaInfo.class)
                .client(client)
                .basePath(ZK_ROOT_PATH)
                .serializer(new JsonInstanceSerializer<>(ServiceMetaInfo.class))
                .build();

        try {
            // 启动 client 和 serviceDiscovery
            client.start();
            serviceDiscovery.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 服务注册（服务端）
     * @param serviceMetaInfo
     */
    @Override
    public void register(ServiceMetaInfo serviceMetaInfo) throws Exception {
        // 注册到zk里
        serviceDiscovery.registerService(buildServiceInstance(serviceMetaInfo));

        // 保存节点信息到本地缓存，用于续期
        String registerKey = ZK_ROOT_PATH + "/" + serviceMetaInfo.getServiceNodeKey();
        localRegisterNodeKeySet.add(registerKey);
    }

    /**
     * 服务注销（服务端）
     * @param serviceMetaInfo
     */
    @Override
    public void unRegister(ServiceMetaInfo serviceMetaInfo) {
        // 删除zk中的节点
        try {
            serviceDiscovery.unregisterService(buildServiceInstance(serviceMetaInfo));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 从本地缓存中移除
        String registerKey = ZK_ROOT_PATH + "/" + serviceMetaInfo.getServiceNodeKey();
        localRegisterNodeKeySet.remove(registerKey);
    }

    /**
     * 服务发现（消费端）
     * @param serviceKey 服务键名
     * @return
     */
    @Override
    public List<ServiceMetaInfo> serviceDiscovery(String serviceKey) {
        // 优先从缓存中获取
        List<ServiceMetaInfo> cachedServiceMetaInfoList = serviceCache.readCache(serviceKey);
        if (cachedServiceMetaInfoList != null) {
            return cachedServiceMetaInfoList;
        }

        try {
            // 从zk中查询
            Collection<ServiceInstance<ServiceMetaInfo>> serviceInstanceList = serviceDiscovery.queryForInstances(serviceKey);
            List<ServiceMetaInfo> serviceMetaInfoList = serviceInstanceList
                                .stream()
                                .map(ServiceInstance::getPayload)
                                .collect(Collectors.toList());

            // 保存到消费端服务缓存中
            serviceCache.writeCache(serviceKey, serviceMetaInfoList);
            return serviceMetaInfoList;
        } catch (Exception e) {
            throw new RuntimeException("获取服务列表失败", e);
        }
    }

    /**
     * 心跳检测（服务端）
     * 不需要心跳机制，建立了临时节点，如果服务器故障，则临时节点直接丢失
     * zk和服务端会建立一个socket长连接，如果服务器故障，长连接断开，zk就会移除该节点信息
     */
    @Override
    public void heartBeat() { }

    /**
     * 监听（消费端）
     * @param serviceNodeKey
     * @param serviceKey
     */
    @Override
    public void watch(String serviceNodeKey, String serviceKey) {
        String watchKey = ZK_ROOT_PATH + "/" + serviceNodeKey;
        boolean newWatch = watchingKeySet.add(watchKey);
        if (!newWatch) {
            return;
        }
        CuratorCache curatorCache = CuratorCache.build(client, watchKey);
        curatorCache.start();
        curatorCache.listenable().addListener(
                CuratorCacheListener
                        .builder()
                .forDeletes(childData -> serviceCache.deleteCache(serviceKey))
                .forChanges(((oldNode, newNode) -> serviceCache.deleteCache(serviceKey)))
                .build()
        );
    }

    @Override
    public void destroy() {
        log.info("注册中心：当前节点下线");
        // 下线节点，这一步可以不做，因为都是临时节点，服务下线，自然就被删掉了
        for (String key : localRegisterNodeKeySet) {
            try {
                client.delete().guaranteed().forPath(key);
            } catch (Exception e) {
                throw new RuntimeException(key + "当前节点下线失败", e);
            }
        }
        // 释放资源
        if (client != null) {
            client.close();
        }
    }

    private ServiceInstance<ServiceMetaInfo> buildServiceInstance(ServiceMetaInfo serviceMetaInfo) {
        String serviceAddress = serviceMetaInfo.getServiceAddress();
        try {
            return ServiceInstance
                    .<ServiceMetaInfo>builder()
                    .id(serviceAddress) // 唯一标识
                    .name(serviceMetaInfo.getServiceKey())
                    .address(serviceAddress)
                    .payload(serviceMetaInfo)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
