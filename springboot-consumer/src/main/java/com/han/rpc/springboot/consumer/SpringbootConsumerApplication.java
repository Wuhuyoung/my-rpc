package com.han.rpc.springboot.consumer;

import com.han.rpc.springboot.starter.annotation.EnableRpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRpc(needServer = false)
public class SpringbootConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootConsumerApplication.class, args);
    }

}
