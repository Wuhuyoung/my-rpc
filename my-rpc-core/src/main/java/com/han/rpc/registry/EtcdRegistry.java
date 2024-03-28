package com.han.rpc.registry;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.json.JSONUtil;
import com.han.rpc.config.RegistryConfig;
import com.han.rpc.model.ServiceMetaInfo;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.watch.WatchEvent;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Etcd 服务注册中心
 */
@Slf4j
public class EtcdRegistry implements Registry {

    private Client client;
    private KV kvClient;

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
    private String ETCD_ROOT_PATH = "/rpc/";

    /**
     * 初始化
     * @param registryConfig
     */
    @Override
    public void init(RegistryConfig registryConfig) {
        client = Client.builder()
                .endpoints(registryConfig.getAddress())
                // 注册中心的超时时间
                .connectTimeout(Duration.ofMillis(registryConfig.getTimeout()))
                .build();
        kvClient = client.getKVClient();
        // 心跳检测
        heartBeat();
    }

    /**
     * 服务注册（服务端）
     * @param serviceMetaInfo
     */
    @Override
    public void register(ServiceMetaInfo serviceMetaInfo) throws Exception {
        // 创建Lease和KV客户端
        Lease leaseClient = client.getLeaseClient();
        // 创建一个30秒的租约(服务30秒过期)
        long leaseId = leaseClient.grant(30).get().getID();
        // 设置要存储的键值对
        String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
        ByteSequence key = ByteSequence.from(registerKey, StandardCharsets.UTF_8);
        ByteSequence value = ByteSequence.from(JSONUtil.toJsonStr(serviceMetaInfo), StandardCharsets.UTF_8);

        // 存储
        PutOption putOption = PutOption.builder().withLeaseId(leaseId).build();
        kvClient.put(key, value, putOption);

        // 添加节点信息到本地缓存
        localRegisterNodeKeySet.add(registerKey);
    }

    /**
     * 服务注销（服务端）
     * @param serviceMetaInfo
     */
    @Override
    public void unRegister(ServiceMetaInfo serviceMetaInfo) {
        String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
        ByteSequence key = ByteSequence.from(registerKey, StandardCharsets.UTF_8);
        CompletableFuture<DeleteResponse> future = kvClient.delete(key);
        // todo 这里可能需要执行join方法才会最终删除？
        future.join();
        // 从本地缓存中移除节点信息
        localRegisterNodeKeySet.remove(registerKey);
    }

    /**
     * 服务发现（消费端）
     * @param serviceKey 服务键名
     * @return
     */
    @Override
    public List<ServiceMetaInfo> serviceDiscovery(String serviceKey) {
        // 优先从缓存获取服务
        List<ServiceMetaInfo> cachedServiceMetaInfoList = serviceCache.readCache(serviceKey);
        if (cachedServiceMetaInfoList != null) {
            return cachedServiceMetaInfoList;
        }

        // 前缀搜索，结尾一定要加 /
        String searchPrefix = ETCD_ROOT_PATH + serviceKey + "/";
        try {
            GetOption getOption = GetOption.builder().isPrefix(true).build();
            List<KeyValue> keyValueList = kvClient.get(ByteSequence.from(searchPrefix, StandardCharsets.UTF_8),
                    getOption)
                    .get()
                    .getKvs();
            List<ServiceMetaInfo> serviceMetaInfoList = keyValueList.stream().map(kv -> {
                String key = kv.getKey().toString(StandardCharsets.UTF_8);
                // 监听key的变化
                watch(key, serviceKey);
                String value = kv.getValue().toString(StandardCharsets.UTF_8);
                return JSONUtil.toBean(value, ServiceMetaInfo.class);
            }).collect(Collectors.toList());

            // 写入消费端服务缓存
            serviceCache.writeCache(serviceKey, serviceMetaInfoList);
            return serviceMetaInfoList;
        } catch (Exception e) {
            throw new RuntimeException("获取服务列表失败", e);
        }
    }

    /**
     * 心跳检测（服务端）
     */
    @Override
    public void heartBeat() {
        // 每10秒重新注册服务，相当于续期了
        CronUtil.schedule("*/10 * * * * *", new Task() {
            @Override
            public void execute() {
                // 遍历每个节点进行续期
                for (String key : localRegisterNodeKeySet) {
                    try {
                        List<KeyValue> keyValueList = kvClient.get(ByteSequence.from(key, StandardCharsets.UTF_8))
                                .get()
                                .getKvs();
                        // 该节点已过期，需重新启动节点才能重新注册
                        if (CollUtil.isEmpty(keyValueList)) {
                            continue;
                        }
                        // 未过期，重新注册（相当于续期）
                        KeyValue keyValue = keyValueList.get(0);
                        String value = keyValue.getValue().toString(StandardCharsets.UTF_8);
                        ServiceMetaInfo serviceMetaInfo = JSONUtil.toBean(value, ServiceMetaInfo.class);
                        register(serviceMetaInfo);
                    } catch (Exception e) {
                        throw new RuntimeException(key + "续签失败", e);
                    }
                }
            }
        });

        // 支持秒级别定时任务
        CronUtil.setMatchSecond(true);
        CronUtil.start();
    }

    /**
     * 监听（消费端）
     * @param serviceNodeKey
     * @param serviceKey
     */
    @Override
    public void watch(String serviceNodeKey, String serviceKey) {
        boolean newWatch = watchingKeySet.add(serviceNodeKey);
        if (!newWatch) {
            return;
        }
        // 之前未被监听，开启监听
        Watch watchClient = client.getWatchClient();
        watchClient.watch(ByteSequence.from(serviceNodeKey, StandardCharsets.UTF_8), response -> {
            for (WatchEvent event : response.getEvents()) {
                switch (event.getEventType()) {
                    // key删除时触发
                    case DELETE:
                        // 清理注册服务缓存
                        // 注意这里存储的是serviceKey而不是serviceNodeKey
                        serviceCache.deleteCache(serviceKey);
                        break;
                    case PUT:
                    default:
                        break;
                }
            }
        });
    }

    /**
     * 服务销毁
     */
    @Override
    public void destroy() {
        log.info("注册中心：当前节点下线");
        // 下线节点（主动下线）
        for (String key : localRegisterNodeKeySet) {
            try {
                CompletableFuture<DeleteResponse> future = kvClient.delete(ByteSequence.from(key, StandardCharsets.UTF_8));
                future.join();
            } catch (Exception e) {
                throw new RuntimeException(key + "节点下线失败", e);
            }
        }
        // 释放资源
        if (client != null) {
            client.close();
        }
        if (kvClient != null) {
            kvClient.close();
        }
    }
}
