package com.han.rpc.proxy;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.han.rpc.RpcApplication;
import com.han.rpc.config.RpcConfig;
import com.han.rpc.constant.RpcConstant;
import com.han.rpc.fault.retry.RetryStrategy;
import com.han.rpc.fault.retry.RetryStrategyFactory;
import com.han.rpc.fault.tolerant.TolerantStrategy;
import com.han.rpc.fault.tolerant.TolerantStrategyFactory;
import com.han.rpc.loadbalancer.LoadBalancer;
import com.han.rpc.loadbalancer.LoadBalancerFactory;
import com.han.rpc.model.RpcRequest;
import com.han.rpc.model.RpcResponse;
import com.han.rpc.model.ServiceMetaInfo;
import com.han.rpc.protocol.ProtocolConstant;
import com.han.rpc.protocol.ProtocolMessage;
import com.han.rpc.protocol.ProtocolMessageDecoder;
import com.han.rpc.protocol.ProtocolMessageEncoder;
import com.han.rpc.protocol.ProtocolMessageSerializerEnum;
import com.han.rpc.protocol.ProtocolMessageStatusEnum;
import com.han.rpc.protocol.ProtocolMessageTypeEnum;
import com.han.rpc.registry.Registry;
import com.han.rpc.registry.RegistryFactory;
import com.han.rpc.serializer.JdkSerializer;
import com.han.rpc.serializer.Serializer;
import com.han.rpc.serializer.SerializerFactory;
import com.han.rpc.server.tcp.VertxTcpClient;
import io.netty.util.concurrent.CompleteFuture;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 服务代理（JDK动态代理）
 */
public class ServiceProxy implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1.构造请求
        String serviceName = method.getDeclaringClass().getName();
        RpcRequest rpcRequest = RpcRequest.builder()
                .serviceName(serviceName)
                .methodName(method.getName())
                .parameterTypes(method.getParameterTypes())
                .args(args)
                .build();
        // 2.将请求序列化
        // 编码时会进行序列化，这里不用再单独序列化了
        // byte[] bytes = serializer.serialize(rpcRequest);

        // 3.从注册中心获取服务提供者请求地址
        RpcResponse rpcResponse = null;
        try {
            RpcConfig rpcConfig = RpcApplication.getRpcConfig();
            Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceName);
            serviceMetaInfo.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);

            List<ServiceMetaInfo> serviceMetaInfoList = registry.serviceDiscovery(serviceMetaInfo.getServiceKey());
            if (CollUtil.isEmpty(serviceMetaInfoList)) {
                throw new RuntimeException("暂无服务地址");
            }

            // 负载均衡
            LoadBalancer loadBalancer = LoadBalancerFactory.getInstance(rpcConfig.getLoadBalancer());
            // 将调用方法名(请求路径)作为请求参数
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("methodName", rpcRequest.getMethodName());
            ServiceMetaInfo selectedServiceMetaInfo = loadBalancer.select(requestParams, serviceMetaInfoList);

            // 4.发送TCP请求
            // 使用重试机制
            try {
                RetryStrategy retryStrategy = RetryStrategyFactory.getInstance(rpcConfig.getRetryStrategy());
                rpcResponse = retryStrategy.doRetry(() ->
                        VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo, 5000L, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                // 容错机制（重试多次仍报错时触发）
                TolerantStrategy tolerantStrategy = TolerantStrategyFactory.getInstance(rpcConfig.getTolerantStrategy());
                tolerantStrategy.doTolerant(null, e);
            }
            return rpcResponse.getData();
        } catch (Exception e) {
            throw new RuntimeException("调用失败");
        }
    }

    /*@Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 指定序列化器
        final Serializer serializer = SerializerFactory.getInstance(RpcApplication.getRpcConfig().getSerializer());
        // 1.构造请求
        String serviceName = method.getDeclaringClass().getName();
        RpcRequest rpcRequest = RpcRequest.builder()
                .serviceName(serviceName)
                .methodName(method.getName())
                .parameterTypes(method.getParameterTypes())
                .args(args)
                .build();
        try {
            // 2.将请求序列化
            byte[] bytes = serializer.serialize(rpcRequest);
            // 3.发送请求
            // 从注册中心获取服务提供者请求地址
            RpcConfig rpcConfig = RpcApplication.getRpcConfig();
            Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceName);
            serviceMetaInfo.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);

            List<ServiceMetaInfo> serviceMetaInfoList = registry.serviceDiscovery(serviceMetaInfo.getServiceKey());
            if (CollUtil.isEmpty(serviceMetaInfoList)) {
                throw new RuntimeException("暂无服务地址");
            }

            // 暂时选择第一个服务地址
            ServiceMetaInfo selectedServiceMetaInfo = serviceMetaInfoList.get(0);

            try (HttpResponse httpResponse = HttpRequest.post(selectedServiceMetaInfo.getServiceAddress())
                    .body(bytes)
                    .execute()) {
                byte[] result = httpResponse.bodyBytes();
                // 4.将结果反序列化
                RpcResponse rpcResponse = serializer.deserialize(result, RpcResponse.class);
                return rpcResponse.getData();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }*/
}
