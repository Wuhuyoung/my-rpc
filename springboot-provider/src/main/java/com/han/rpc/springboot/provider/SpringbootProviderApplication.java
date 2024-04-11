package com.han.rpc.springboot.provider;

import com.han.rpc.springboot.starter.annotation.EnableRpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRpc
public class SpringbootProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootProviderApplication.class, args);
    }

}
