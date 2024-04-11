package com.han.rpc.fault.tolerant;

import com.han.rpc.model.RpcResponse;

import java.util.Map;

/**
 * 转移到其他服务节点 - 容错策略
 */
public class FailOverTolerantStrategy implements TolerantStrategy {
    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        // todo 调用其他可用的服务节点
        return null;
    }
}
