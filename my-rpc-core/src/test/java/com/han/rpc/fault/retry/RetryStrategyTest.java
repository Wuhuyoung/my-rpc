package com.han.rpc.fault.retry;

import com.han.rpc.model.RpcResponse;
import org.junit.jupiter.api.Test;

public class RetryStrategyTest {

    RetryStrategy strategy = new FixedIntervalRetryStrategy();

    @Test
    public void testRetryStrategy() {
        try {
            RpcResponse rpcResponse = strategy.doRetry(() -> {
                System.out.println("测试重试");
                throw new RuntimeException("模拟重试失败");
            });
            System.out.println("response = " + rpcResponse);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("重试多次失败，停止重试...");
        }
    }
}
