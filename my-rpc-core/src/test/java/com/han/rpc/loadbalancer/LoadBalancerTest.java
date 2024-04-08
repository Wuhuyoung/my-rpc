package com.han.rpc.loadbalancer;

import com.han.rpc.model.ServiceMetaInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 负载均衡器测试
 */
public class LoadBalancerTest {
    LoadBalancer loadBalancer = new ConsistentHashLoadBalancer();

    @Test
    public void testLoadBalancer() {
        // 请求参数
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("methodName", "getName");
        // 服务列表
        ArrayList<ServiceMetaInfo> serviceMetaInfos = new ArrayList<>();
        ServiceMetaInfo serviceMetaInfo1 = new ServiceMetaInfo();
        serviceMetaInfo1.setServiceName("myService");
        serviceMetaInfo1.setServiceHost("localhost");
        serviceMetaInfo1.setServiceVersion("1.0");
        serviceMetaInfo1.setServicePort(1234);

        ServiceMetaInfo serviceMetaInfo2 = new ServiceMetaInfo();
        serviceMetaInfo2.setServiceName("myService");
        serviceMetaInfo2.setServiceHost("localhost");
        serviceMetaInfo2.setServiceVersion("1.0");
        serviceMetaInfo2.setServicePort(4321);

        serviceMetaInfos.add(serviceMetaInfo1);
        serviceMetaInfos.add(serviceMetaInfo2);

        // 4次请求
        ServiceMetaInfo metaInfo1 = loadBalancer.select(requestParams, serviceMetaInfos);
        System.out.println(metaInfo1);
        Assertions.assertNotNull(metaInfo1);
        ServiceMetaInfo metaInfo2 = loadBalancer.select(requestParams, serviceMetaInfos);
        System.out.println(metaInfo2);
        Assertions.assertNotNull(metaInfo2);
        ServiceMetaInfo metaInfo3 = loadBalancer.select(requestParams, serviceMetaInfos);
        System.out.println(metaInfo3);
        Assertions.assertNotNull(metaInfo3);
        ServiceMetaInfo metaInfo4 = loadBalancer.select(requestParams, serviceMetaInfos);
        System.out.println(metaInfo4);
        Assertions.assertNotNull(metaInfo4);
    }
}
