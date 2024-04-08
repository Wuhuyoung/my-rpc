package com.han.rpc.loadbalancer;

import cn.hutool.core.collection.CollUtil;
import com.han.rpc.model.ServiceMetaInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 最少活跃数负载均衡器（消费端）
 */
/*
  1.使用一个Map存储每个服务的活跃数
  2.提供方法，给每个服务增加活跃数或减少活跃数，在调用前+1，调用后-1
*/
public class LeastActiveLoadBalancer implements LoadBalancer {
    private final Map<ServiceMetaInfo, Integer> active = new ConcurrentHashMap<>();

    @Override
    public ServiceMetaInfo select(Map<String, Object> requestParams, List<ServiceMetaInfo> serviceMetaInfoList) {
        if (CollUtil.isEmpty(serviceMetaInfoList)) {
            return null;
        }
        // 只有一个服务
        if (serviceMetaInfoList.size() == 1) {
            return serviceMetaInfoList.get(0);
        }
        for (ServiceMetaInfo serviceMetaInfo : serviceMetaInfoList) {
            if (!active.containsKey(serviceMetaInfo)) {
                active.put(serviceMetaInfo, 0);
            }
        }
        // 遍历得到最小活跃数的服务节点
        int minActiveCount = Integer.MAX_VALUE;
        ServiceMetaInfo selectedService = null;
        for (Map.Entry<ServiceMetaInfo, Integer> entry : active.entrySet()) {
            if (entry.getValue() < minActiveCount) {
                minActiveCount = entry.getValue();
                selectedService = entry.getKey();
            }
        }
        return selectedService;
    }

    public void addServiceActiveCount(ServiceMetaInfo serviceMetaInfo) {
        active.put(serviceMetaInfo, active.get(serviceMetaInfo) + 1);
    }

    public void reduceServiceActiveCount(ServiceMetaInfo serviceMetaInfo) {
        active.put(serviceMetaInfo, active.get(serviceMetaInfo) - 1);
    }
}
