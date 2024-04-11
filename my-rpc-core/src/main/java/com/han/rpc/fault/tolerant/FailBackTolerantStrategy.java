package com.han.rpc.fault.tolerant;

import com.han.rpc.model.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 降级到其他服务 - 容错策略
 */
@Slf4j
public class FailBackTolerantStrategy implements TolerantStrategy {
    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        // todo 获取降级的服务并调用
        return null;
    }
}
