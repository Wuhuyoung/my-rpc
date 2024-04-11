package com.han.rpc.springboot.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class SpringbootConsumerApplicationTests {

    @Resource
    private ConsumerExample consumerExample;

    @Test
    void testUserService() {
        consumerExample.testUserService();
    }

}
