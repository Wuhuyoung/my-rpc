# 手写 RPC 框架 my-rpc

<p align="center">
 <img src="https://img.shields.io/badge/Java-11-blue" alt="Coverage Status">
 <img src="https://img.shields.io/badge/vertx--core-4.5.1-blue" alt="Downloads">
 <img src="https://img.shields.io/badge/guava--retrying-2.0.0-blue" alt="Downloads">
 <img src="https://img.shields.io/badge/jetcd--core-0.7.7-blue" alt="Downloads">
    <img src="https://img.shields.io/badge/license-MIT-green" alt="Downloads">
</p>

## 介绍

基于 Java + Etcd + Vert.x + 自定义协议实现。开发者可以引入 Spring Boot Starter，通过注解和配置文件快速使用框架，像调用本地方法一样轻松调用远程服务；还支持通过 SPI 机制动态扩展序列化器、负载均衡器、重试和容错策略等。

## 项目架构图

![image-20240415181551832](https://typora-1314662469.cos.ap-shanghai.myqcloud.com/img/202404151958254.png)

## 源码目录

- my-rpc-core：RPC 框架核心代码
- my-rpc-easy：RPC 框架简易版
- common：示例代码公用模块
- consumer：示例服务消费者
- provider：示例服务提供者
- springboot-consumer：示例服务消费者（SpringBoot 框架）
- springboot-provider：示例服务提供者（SpringBoot 框架）
- my-rpc-spring-boot-starter：注解驱动的 RPC 框架，可在 Spring Boot 项目中快速使用

## 如何使用本项目

Java 版本必须是11及以上

### 非 Spring Boot 项目

#### 服务提供端

1、引入依赖

```xml
<dependency>
    <groupId>com.han.rpc</groupId>
    <artifactId>my-rpc-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

2、启动 Etcd

这里我本地启动 Etcd，端口2379

3、编写配置文件（application.yml 或 application.yaml）

```yaml
rpc:
  name: my-rpc
  version: 1.0
  serverHost: localhost
  serverPort: 9090
  mock: false
  # 序列化器，支持jdk、json、kryo、hessian，可自定义扩展
  serializer: kryo
  # 注册中心，支持etcd、zookeeper，可自定义扩展
  registryConfig:
    registry: etcd
    address: http://localhost:2379
```

4、编写要提供的服务

```java
/**
 * 用户服务实现类
 */
public class UserServiceImpl implements UserService {
    public User getUser(User user) {
        System.out.println("用户名为" + user.getName());
        return user;
    }
}
```

5、编写服务提供者示例，运行后会在指定端口启动一个服务器

```java
/**
 * 服务提供者示例
 */
public class ProviderExample {
    public static void main(String[] args) {
        // 要注册的服务
        List<ServiceRegisterInfo<?>> serviceRegisterInfoList = new ArrayList<>();
        ServiceRegisterInfo<UserService> service =
                new ServiceRegisterInfo<>(UserService.class.getName(), UserServiceImpl.class);
        serviceRegisterInfoList.add(service);

        // 服务提供者初始化
        ProviderBootstrap.init(serviceRegisterInfoList);
    }
}
```

6、利用 SPI 机制自行扩展（这里以扩展 序列化器 为例）

1. 假设实现 Protobuf 序列化器，创建一个类 `ProtobufSerializer` 实现 `com.han.rpc.serializer.Serializer` 接口，实现其中的 `serialize` 和 `deserialize` 方法。

   如果是注册中心、负载均衡器、重试策略、容错策略的自定义，则分别实现 `Registry`、`LoadBalancer`、`RetryStrategy`、`TolerantStrategy` 接口

2. 在 my-rpc-core 的 META-INF/rpc/custom 目录下创建名为 com.han.rpc.serializer.Serializer 的文件，内容为 `key=实现类的全路径`

   ```
   protobuf=com.han.rpc.serializer.ProtobufSerializer
   ```

3. 在服务提供者和服务消费者的配置文件中修改序列化器为自定义的

   ```yaml
   rpc:
     name: my-rpc
     version: 1.0
     serverHost: localhost
     serverPort: 9090
     mock: false
     # 序列化器，支持jdk、json、kryo、hessian，可自定义扩展
     serializer: protobuf # 自定义序列化器
     registryConfig:
       registry: etcd
       address: http://localhost:2379
   ```

#### 服务消费端

1、引入依赖

```xml
<dependency>
    <groupId>com.han.rpc</groupId>
    <artifactId>my-rpc-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

2、编写配置文件（application.yml 或 application.yaml）

```yaml
rpc:
  name: my-rpc
  version: 1.0
  mock: false
  # 序列化器，支持jdk、json、kryo、hessian，可自定义扩展
  serializer: kryo
  # 负载均衡策略，支持roundRobin、random、consistentHash(一致性Hash)，可自定义扩展
  loadBalancer: roundRobin
  # 重试策略，支持no、fixedInterval、exponentialBackoff(指数退避)，可自定义扩展
  retryStrategy: fixedInterval
  # 容错策略，支持failFast、failSafe，可自定义扩展
  tolerantStrategy: failFast
  # 注册中心，支持etcd、zookeeper，可自定义扩展
  registryConfig:
    registry: etcd
    address: http://localhost:2379
```

3、编写服务消费者示例

```java
/**
 * 简单服务消费者示例
 */
public class EasyConsumerExample {
    public static void main(String[] args) {
        // 服务消费者初始化
        ConsumerBootstrap.init();

        // 需要获取userService的代理对象
        UserService userService = ServiceProxyFactory.getProxy(UserService.class);
        User user = new User();
        user.setName("小明");
        // 通过RPC调用远程方法
        User newUser = userService.getUser(user);
    }
}
```



### Spring Boot 项目

#### 服务提供端

1、引入依赖

```xml
<dependency>
    <groupId>com.han.rpc</groupId>
    <artifactId>my-rpc-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

2、编写配置文件

```yaml
rpc:
  name: my-rpc
  version: 1.0
  serverHost: localhost
  serverPort: 9090
  mock: false
  serializer: kryo
  registryConfig:
    registry: etcd
    address: http://localhost:2379
```

3、在服务实现类上加上 `@RpcService` 注解

```java
@Service
@RpcService
public class UserServiceImpl implements UserService {
    @Override
    public User getUser(User user) {
        System.out.println("springboot.provider.UserServiceImpl 用户名：" + user.getName());
        return user;
    }
}
```

4、启动类上加上 `@EnableRpc` 注解

```java
@SpringBootApplication
@EnableRpc
public class SpringbootProviderApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringbootProviderApplication.class, args);
    }
}
```

#### 服务消费端

1、引入依赖

```xml
<dependency>
    <groupId>com.han.rpc</groupId>
    <artifactId>my-rpc-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

2、编写配置文件

```yaml
rpc:
  name: my-rpc
  version: 2.0
  mock: false
  serializer: kryo
  loadBalancer: roundRobin
  retryStrategy: fixedInterval
  tolerantStrategy: failFast
  registryConfig:
    registry: etcd
    address: http://localhost:2379
```

3、在 Spring 的 bean 中引入 UserService 属性并加上 `@RpcReference` 的注解

```java
@Service
public class ConsumerExample {

    @RpcReference
    private UserService userService;

    public void testUserService() {
        User user = new User();
        user.setName("张三");
        User serviceUser = userService.getUser(user);
    }
}
```

4、启动类上添加 `@EnableRpc(needServer = false)` 的注解

```java
@SpringBootApplication
@EnableRpc(needServer = false)
public class SpringbootConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringbootConsumerApplication.class, args);
    }
}
```

5、启动服务提供端和消费端，就能正常发起 RPC 调用



## 项目简介

| 需求             | 设计方案                                                     |
| ---------------- | ------------------------------------------------------------ |
| 核心架构         | 包括消费方调用、序列化器、网络服务器、请求处理器、注册中心、负载均衡器、重试策略、容错策略等模块 |
| 全局配置加载     | 使用双检锁单例模式维护全局配置对象，并通过 snakeyaml 实现多环境 yaml 配置文件的加载 |
| 接口 Mock        | 通过 JDK 动态代理 + 工厂模式实现，为指定服务接口类生成返回模拟数据的 Mock 服务对象，便于开发者测试 |
| 多种序列化器实现 | 定义序列化器接口，实现了基于 JSON、Kryo 和 Hessian 的序列化器，并通过 ThreadLocal 解决了 Kryo 序列化器的线程安全问题 |
| 消费方调用       | 基于 JDK 动态代理 + 工厂模式实现消费方调用模块，为指定服务接口类生成可发送 TCP 请求的代理对象，实现远程方法的无感知调用 |
| 可扩展设计       | 使用工厂模式 + 单例模式简化创建和获取序列化器对象的操作。并通过扫描资源路径 + 反射自实现了 SPI 机制，用户可通过编写配置的方式扩展和指定自己的序列化器 |
| 注册中心         | 基于 Etcd 云原生中间件实现了高可用的分布式注册中心，利用其层级结构和 Jetcd 的 KvClient 存储服务和节点信息，并支持通过 SPI 机制扩展 |
| 注册中心优化     | 利用定时任务和 Etcd Key 的 TTL 实现服务提供者的心跳检测和续期机制，节点下线一定时间后自动移除注册信息 |
| 消费者服务缓存   | 使用本地对象维护已获取到的服务提供者节点缓存，提高性能；并通过 Etcd 的 Watch 机制，监听节点的过期并自动更新缓存。 |
| 自定义协议       | 由于 HTTP 协议头信息较多，基于 Vert.x TCP 服务器 + 类 Dubbo 的紧凑型消息结构（字节数组）自实现了 RPC 协议，提升网络传输性能 |
| 半包粘包         | 基于 Vert.x 的 RecordParser 完美解决半包粘包问题，并使用装饰者模式封装了 TcpBufferHandlerWrapper 类，一行代码即可对原有的请求处理器进行增强，提高代码的可维护性 |
| 负载均衡器       | 为提高服务提供者集群处理能力，实现了一致性 Hash、轮询、随机等不同算法的负载均衡器，并通过 SPI 机制支持开发者自行扩展 |
| 重试机制         | 为提高消费端调用的稳定性，基于 Guava Retrying 实现了包括 fixedWait 等多种重试策略，并通过 SPI 机制支持开发者自行扩展 |
| 容错机制         | 为提高系统的稳定性和可用性，设计实现了 FailOver、FailBack、FailSafe、FailFast 等多种重试策略，并通过 SPI 机制支持开发者自行扩展。 |
| 注解驱动         | 为降低开发者的使用成本，封装了服务提供者和消费者启动类；并开发了基于注解驱动的 Spring Boot Starter，一个注解就能快速注册 Bean 为服务、以及注入服务调用代理对象 |

