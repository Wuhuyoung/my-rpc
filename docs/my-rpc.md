# my-rpc

## 一、简易版RPC框架

### 介绍

专业定义：RPC（Remote Procedure Call）即远程过程调用，是一种计算机通信协议，它允
许程序在不同的计算机之间进行通信和交互，就像本地调用一样。

RPC 允许一个程序（称为服务消费者）像调用自己程序的方法一样，调用另一个程序（称为服务提供者）的接口，而不需要了解数据的传输处理过程、底层网络通信的细节等。这些都会由 RPC 框架帮你完成，使得开发者可以轻松调用远程服务，快速开发分布式系统。

### RPC框架架构图

![image-20240313164218237](https://typora-1314662469.cos.ap-shanghai.myqcloud.com/img/202403131642344.png)

## 二、全局配置加载

在 RPC 框架运行的过程中，会涉及到很多的配置信息，比如注册中心的地址、序列化方式、网络服务器端口号等等。

之前的简易版 RPC 项目中，我们是在程序里硬编码了这些配置，不利于维护。

而且 RPC 框架是需要被其他项目作为服务提供者或者服务消费者引入的，我们应当允许引入框架的项目通过编写配置文件来**自定义配置**。并且一般情况下，服务提供者和服务消费者需要编写相同的 RPC 配置。

因此，我们需要一套全局配置加载功能。能够让 RPC 框架轻松地从配置文件中读取配置，并且维护一个全局配置对象，便于框架快速获取到一致的配置。

### 配置加载

这里使用 hutool 的工具类来读取 配置文件中的信息

先创建配置类 RpcConfig，然后创建一个工具类来读取配置类并返回配置对象 RpcConfig

```java
/**
 * 配置工具类
 */
public class ConfigUtils {
    /**
     * 加载配置对象
     * @param tClass
     * @param prefix
     * @param <T>
     * @return
     */
    public static <T> T loadConfig(Class<T> tClass, String prefix) {
        return loadConfig(tClass, prefix, "");
    }

    /**
     * 加载配置对象，支持区分环境
     * @param tClass
     * @param prefix
     * @param environment
     * @param <T>
     * @return
     */
    public static <T> T loadConfig(Class<T> tClass, String prefix, String environment) {
        StringBuilder configFile = new StringBuilder("application");
        if (StrUtil.isNotBlank(environment)) {
            configFile.append("-")
                    .append(environment);
        }
        configFile.append(".properties");
        Props props = new Props(configFile.toString());
        return props.toBean(tClass, prefix);
    }
}
```

### 维护全局配置对象

我们需要在项目启动时读取配置文件，生成配置对象，之后直接从对象中获取配置信息即可

这里为了保证配置对象的唯一性，使用了单例模式的 Double-check 锁来实现

```java
/**
 * RPC框架应用
 * 相当于holder，存放了项目全局用到的变量，使用double-check锁单例模式实现
 */
@Slf4j
public class RpcApplication {

    private static volatile RpcConfig rpcConfig;
    /**
     * 获取配置
     * @return
     */
    public static RpcConfig getRpcConfig() {
        if (rpcConfig == null) {
            synchronized (RpcApplication.class) {
                if (rpcConfig == null) {
                    init();
                }
            }
        }
        return rpcConfig;
    }

    /**
     * 初始化
     */
    public static void init() {
        RpcConfig rpcConfig = null;
        try {
            rpcConfig = ConfigUtils.loadConfig(RpcConfig.class, DEFAULT_CONFIG_PREFIX);
        } catch (Exception e) {
            // 配置加载失败，使用默认配置
            rpcConfig = new RpcConfig();
        }
        init(rpcConfig);
    }

    /**
     * 初始化，可以自定义配置类
     * @param newRpcConfig
     */
    public static void init(RpcConfig newRpcConfig) {
        rpcConfig = newRpcConfig;
        log.info("rpc init, config = {}", rpcConfig.toString());
    }
}
```

以后 RPC 框架内只需要写一行代码，就能正确加载到配置：

```java
RpcConfig rpc = RpcApplication.getRpcConfig();
```



### 扩展

1）支持读取 application.yml、application.yaml 等不同格式的配置文件

这里使用了 snakeyaml 三方依赖来读取 yaml / yml 文件，可以脱离 Spring 环境单独使用。其实在 SpringBoot 的底层，也是借助了 SnakeYml 来进行的 yml 的解析操作。参考：https://blog.csdn.net/chengxuyuanlaow/article/details/127564511

1. 在rpc-core引入依赖

```xml
<!--解析yml文件-->
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>1.33</version>
</dependency>
```

2. 在 loadConfig 方法中添加读取 yml 文件的逻辑

```java
/**
     * 加载配置对象，支持区分环境
     * @param tClass
     * @param prefix
     * @param environment
     * @param <T>
     * @return
     */
public static <T> T loadConfig(Class<T> tClass, String prefix, String environment) {
    StringBuilder configFile = new StringBuilder("application");
    if (StrUtil.isNotBlank(environment)) {
        configFile.append("-")
            .append(environment);
    }
    String fileName = configFile.toString();
    if (FileUtil.exist(fileName + ".yml")) {
        fileName += ".yml";
    } else if (FileUtil.exist(fileName + ".yaml")) {
        fileName += ".yaml";
    } else {
        fileName += ".properties";
        Props props = new Props(fileName, CharsetUtil.CHARSET_UTF_8);
        // 自动监听配置文件的变更并加载
        props.autoLoad(true);
        return props.toBean(tClass, prefix);
    }
    // 使用snakeyaml解析yaml文件
    Yaml yaml = new Yaml();
    Map<String, Object> load = yaml.load(ConfigUtils.class.getClassLoader().getResourceAsStream(fileName));

    T config = null;
    try {
        Map<String, Object> configMap = (Map<String, Object>) load.get(prefix);
        config = BeanUtil.fillBeanWithMap(configMap, tClass.newInstance(), false);
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
    return config;
}
```

2）支持监听配置文件的变更，并自动更新配置对象

使用 Hutool 的 props.autoLoad() 可以实现配置文件变更的监听和自动加载

```java
Props props = new Props(fileName, CharsetUtil.CHARSET_UTF_8);
// 自动监听配置文件的变更并加载
props.autoLoad(true);
return props.toBean(tClass, prefix);
```

3）配置文件支持中文

注意编码问题，使用 UTF-8 编码方式即可

```java
Props props = new Props(fileName, CharsetUtil.CHARSET_UTF_8);
```

4）配置分组。后续随着配置项的增多，可以考虑对配置项进行分组

可以通过嵌套配置类实现



## 三、接口 Mock

### 需求分析

RPC 框架的核心功能是调用其他远程服务。但是在实际开发和测试过程中，有时可能无法直接访问真实的远程服务，或者访问真实的远程服务可能会产生不可控的影响，例如网络延迟、服务不稳定等。在这种情况下，就需要使用 mock 服务来模拟远程服务的行为，随便返回一个值，以便进行接口的测试、开发和调试。

### 设计方案

使用动态代理创建一个模拟对象，调用方法时返回固定值

### 开发实现

1. 配置一个开关，在配置文件中新增一个 mock 字段，默认是 false

```java
@Data
public class RpcConfig {
	...
        
    /**
     * 模拟调用
     */
    private boolean mock = false;
}
```

2. 新增一个 MockServiceProxy 来生成 Mock 代理对象

```java
@Slf4j
public class MockServiceProxy implements InvocationHandler {

    /**
     * 调用代理
     * @param proxy
     * @param method
     * @param args
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> returnType = method.getReturnType();
        log.info("mock invoke: {}", method.getName());
        return getDefaultType(returnType);
    }

    /**
     * 生成指定类型的默认值对象
     * @param type
     * @return
     */
    private Object getDefaultType(Class<?> type) {
        if (type.isPrimitive()) {
            Faker faker = new Faker();
            Number number = faker.number();
            if (type == boolean.class) {
                return false;
            } else if (type == int.class) {
                return number.randomDigit();
            } else if (type == long.class) {
                return number.randomNumber();
            } else if (type == short.class) {
                return (short) number.randomNumber(4, false);
            } else if (type == byte.class) {
                return (byte) number.randomNumber(2, false);
            } else if (type == double.class) {
                return number.randomDouble(2, 0, 1000);
            } else if (type == char.class) {
                return faker.name().firstName().charAt(0);
            }
        }
        return null;
    }
}
```

这里使用了 Faker 第三方依赖伪造数据，来生成默认值，需要导入依赖

```xml
<!--构造测试数据-->
<dependency>
    <groupId>com.github.javafaker</groupId>
    <artifactId>javafaker</artifactId>
    <version>1.0.2</version>
</dependency>
```

3. 需要在服务代理工厂新增获取 mock 代理对象的方法

```java
/**
 * 服务代理工厂（用于创建代理对象）
 */
public class ServiceProxyFactory {
    /**
     * 根据服务类获取代理对象
     * @param serviceClass
     * @param <T>
     * @return
     */
    public static <T> T getProxy(Class<T> serviceClass) {
        // 根据配置mock来区分创建哪种代理对象
        if (RpcApplication.getRpcConfig().isMock()) {
            return getMockProxy(serviceClass);
        }
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new ServiceProxy()
        );
    }

    /**
     * 根据服务类获取Mock代理对象
     * @param serviceClass
     * @param <T>
     * @return
     */
    private static <T> T getMockProxy(Class<T> serviceClass) {
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new MockServiceProxy()
        );
    }
}
```



## 四、SPI动态配置序列化器

### 需求分析

序列化器的作用：无论是请求或响应，都会涉及参数的传输。而 Java 对象是存活在 JVM 虚拟机中的，如果想在其他位置存储并访问、或者在网络中进行传输，就需要进行序列化和反序列化。

现在想要实现三个问题：

1. 有没有更好的序列化器实现方式？
2. 如何让使用框架的开发者指定使用的序列化器？
3. 如何让使用框架的开发者自己定制序列化器？

### 设计方案

#### 1、序列化器实现方案

主流序列化方式：JSON、Hessian、Kryo、Protobuf

这里实现 JSON、Hessian、Kryo 这三种序列化器

#### 2、动态配置

之前我们是在代码中硬编码了序列化器，比如：

```java
Serializer serializer = new JdkSerializer();
```

理想情况下，应该可以通过配置文件来指定使用的序列化器。在使用序列化器时，根据配置来获取不同的序列化器实例即可。

我们只需要定义一个 `序列化器名称 => 序列化器实现类对象` 的 Map，然后根据名称从 Map 中获取对象即可

#### 3、自定义序列化器

如果开发者不想使用框架内置的序列化器，想要自己定义一个新的序列化器实现，怎么办呢？

思路很简单：只要我们的 RPC 框架能够读取到用户自定义的类路径，然后加载这个类，作为 Serializer 序列化器接口的实现即可

这里我们可以使用 Java 的 **SPI** 机制实现。

##### 1、什么是 SPI？

SPI（Service Provider Interface）服务提供接口是 Java 的机制，主要用于实现模块化开发和插件化扩展。

SPI 机制允许服务提供者通过特定的配置文件将自己的实现注册到系统中，然后系统通过反射机制动态加载这些实现，而不需要修改原始框架的代码，从而实现了系统的解耦、提高了可扩展性。

##### 2、如何实现 SPI？

- 系统实现

其实 Java 内已经提供了 SPI 机制相关的 API 接口，可以直接使用，这种方式最简单。

1）首先在 resources 资源目录下创建 META-INF/services 目录，并且创建一个名称为要实现的接口的空文件，比如 com.han.rpc.serializer.Serializer

2）在文件中填写自己定制的接口实现类的完整类路径

```
com.han.rpc.serializer.JsonSerializer
```

3）直接使用系统内置的 ServiceLoader 动态加载指定接口的实现类，代码如下：

```java
// 指定序列化器
Serializer serializer = null;
ServiceLoader<Serializer> serviceLoader = ServiceLoader.load(Serializer.class);
for (Serializer service : serviceLoader) {
    serializer = service;
}
```

上述代码能够获取到所有文件中编写的实现类对象，选择一个使用即可。

- 自定义实现

为了能让用户通过配置快速指定序列化器，我们需要自定义实现 SPI 机制，只要能够根据配置加载到类即可

### 开发实现

#### 1、序列化器实现

建议直接参考网上代码、或者利用 AI 生成即可

#### 2、RpcConfig 中补充序列化器配置

```java
public class RpcConfig {
    ...

    /**
     * 序列化器
     */
    private String serializer = SerializerKey.JDK;
}
```

#### 3、自定义序列化器

我们使用自定义的 SPI 机制实现，支持用户自定义序列化器并指定键名。

1）指定 SPI 配置目录

系统内置的 SPI 机制会加载 resources 资源目录下的 META-INF/services 目录，那我们自定义的序列化器可以如法炮制，改为读取 META-INF/rpc 目录

我们还可以将 SPI 配置再分为系统内置 SPI 和用户自定义 SPI，即目录如下：

- 用户自定义 SPI：META-INF/rpc/custom。用户可以在该目录下新建配置，加载自定义的实现类。
- 系统内置 SPI：META-INF/rpc/system。RPC 框架自带的实现类，比如我们之前开发好的 JdkSerializer

这样一来，所有接口的实现类都可以通过 SPI 动态加载，不用在代码中硬编码 Map 来维护实现类了。

让我们编写一个系统扩展配置文件，内容为我们之前写好的序列化器。

文件名称为 com.han.rpc.serializer.Serializer

```
jdk=com.han.rpc.serializer.JdkSerializer
json=com.han.rpc.serializer.JsonSerializer
kryo=com.han.rpc.serializer.KryoSerializer
hessian=com.han.rpc.serializer.HessianSerializer
```

2）编写 SpiLoader 加载器。

相当于一个工具类，提供了读取配置并加载实现类的方法。

关键实现如下：

1. 用 Map 来存储已加载的配置信息 `键名 => 实现类`
2. 扫描指定路径，读取每个配置文件，获取到 `键名 => 实现类` 信息并存储在 Map 中。
3. 定义获取实例方法，根据用户传入的接口和键名，从 Map 中找到对应的实现类，然后通过反射获取到实现类对象。可以维护一个对象实例缓存，创建过一次的对象从缓存中读取即可。

完整代码如下：

```java
/**
 * SPI加载器（支持键值对映射）
 */
@Slf4j
public class SpiLoader {
    /**
     * 存储已加载的类：接口名 => (key => 实现类)
     */
    private static final Map<String, Map<String, Class<?>>> loaderClass = new ConcurrentHashMap<>();

    /**
     * 对象缓存实例（避免重复new）：类路径 => 对象实例，单例模式（饿汉式）
     */
    private static final Map<String, Object> instanceCache = new ConcurrentHashMap<>();

    /**
     * 系统SPI目录
     */
    private static final String RPC_SYSTEM_SPI_DIR = "META-INF/rpc/system/";

    /**
     * 用户SPI目录
     */
    private static final String RPC_CUSTOM_SPI_DIR = "META-INF/rpc/custom/";

    /**
     * 扫描路径
     */
    private static final String[] SCAN_DIRS = new String[]{RPC_SYSTEM_SPI_DIR, RPC_CUSTOM_SPI_DIR};

    /**
     * 动态加载的类列表
     */
    private static final List<Class<?>> LOAD_CLASS_LIST = Arrays.asList(Serializer.class);

    /**
     * 加载所有类型
     */
    public static void loadAll() {
        log.info("加载所有SPI");
        for (Class<?> clazz : LOAD_CLASS_LIST) {
            load(clazz);
        }
    }

    /**
     * 加载某个类型
     *
     * @param loadClass
     */
    public static void load(Class<?> loadClass) {
        log.info("加载类型为 {} 的SPI", loadClass.getName());
        // 扫描路径，用户自定义的SPI优先级高于系统SPI
        HashMap<String, Class<?>> keyClassMap = new HashMap<>();
        for (String scanDir : SCAN_DIRS) {
            // 并非通过文件路径获取，因为如果框架作为依赖被引入，是无法得到正确的文件路径的
            List<URL> resources = ResourceUtil.getResources(scanDir + loadClass.getName());
            // 读取每个资源文件
            for (URL resource : resources) {
                try {
                    InputStreamReader inputStreamReader = new InputStreamReader(resource.openStream());
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        String[] split = line.split("=");
                        if (split.length > 1) {
                            String key = split[0];
                            String className = split[1];
                            keyClassMap.put(key, Class.forName(className));
                        }
                    }
                } catch (Exception e) {
                    log.error("SPI resource load error", e);
                }
            }
        }
        loaderClass.put(loadClass.getName(), keyClassMap);
    }

    /**
     * 加载某个类型的某个key
     *
     * @param loadClass
     */
    public static void loadKey(Class<?> loadClass, String key) {
        log.info("加载类型为 {} 的SPI，key 为 {} ", loadClass.getName(), key);
        Map<String, Class<?>> keyClassMap = loaderClass.getOrDefault(loadClass.getName(), new HashMap<>());
        // 扫描路径，用户自定义的SPI优先级高于系统SPI
        for (String scanDir : SCAN_DIRS) {
            // 并非通过文件路径获取，因为如果框架作为依赖被引入，是无法得到正确的文件路径的
            List<URL> resources = ResourceUtil.getResources(scanDir + loadClass.getName());
            // 读取每个资源文件
            for (URL resource : resources) {
                try {
                    InputStreamReader inputStreamReader = new InputStreamReader(resource.openStream());
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        String[] split = line.split("=");
                        if (split.length > 1) {
                            String classKey = split[0];
                            String className = split[1];
                            if (key.equals(classKey)) {
                                keyClassMap.put(key, Class.forName(className));
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("SPI resource load error", e);
                }
            }
        }
        loaderClass.put(loadClass.getName(), keyClassMap);
    }

    /**
     * 获取某个接口的实例
     *
     * @param tClass
     * @param key
     * @param <T>
     * @return
     */
    public static <T> T getInstance(Class<T> tClass, String key) {
        String tClassName = tClass.getName();
        Map<String, Class<?>> keyClassMap = loaderClass.get(tClassName);
        if (keyClassMap == null || !keyClassMap.containsKey(key)) {
            // 只有获取对应的实例才会加载，采用单例模式（Double-check锁）
            synchronized (SpiLoader.class) {
                if (keyClassMap == null || !keyClassMap.containsKey(key)) {
                    loadKey(tClass, key);
                }
            }
            keyClassMap = loaderClass.get(tClassName);
        }
        if (keyClassMap == null) {
            throw new RuntimeException(String.format("SpiLoader 未加载 %s 类型", tClassName));
        }
        if (!keyClassMap.containsKey(key)) {
            throw new RuntimeException(String.format("SpiLoader的 %s 不存在 key=%s 的类型", tClassName, key));
        }
        // 获取到要加载的实例类型
        Class<?> implClass = keyClassMap.get(key);
        String implClassName = implClass.getName();
        // 从实例缓存中获取实例
        if (!instanceCache.containsKey(implClassName)) {
            try {
                instanceCache.put(implClassName, implClass.newInstance());
            } catch (InstantiationException | IllegalAccessException e) {
                String errorMsg = String.format("%s 类实例化失败", implClassName);
                throw new RuntimeException(errorMsg, e);
            }
        }
        return (T) instanceCache.get(implClassName);
    }
}
```

注意，上述代码中获取配置文件是使用了 `ResourceUtil.getResources` ，而不是通过文件路径获取。因为如果框架作为依赖被引入，是无法得到正确文件路径的。

上述代码中，虽然提供了 load 和 loadAll 方法，扫描所有路径下的文件进行加载，但其实没必要使用。

推荐直接使用 getInstance 方法获取某个类对应 key 的实现类，使用 DCL 单例模式支持懒加载，只有获取实例时才加载对应的类，并存放到缓存中

#### 4、定义序列化器工厂

我们定义一个序列化器工厂来获得序列化器对象，使用 SPI 加载指定的序列化器对象

```java
/**
 * 序列化器工厂（用于获取序列化器对象）
 */
public class SerializerFactory {

    static {
        // 不需要一次性加载所有类，每次getInstance时再动态去加载
//        SpiLoader.load(Serializer.class);
    }

    /**
     * 默认序列化器
     */
    private static final Serializer DEFAULT_SERIALIZER = new JdkSerializer();

    /**
     * 获取实例
     * @param key
     * @return
     */
    public static Serializer getInstance(String key) {
        return SpiLoader.getInstance(Serializer.class, key);
    }
}
```

之后就可以调用如下代码来获取对象了：

```java
final Serializer serializer = SerializerFactory.getInstance(RpcApplication.getRpcConfig().getSerializer());
```

### 测试

1. SPI 加载测试
   1. 测试存在 key 和 不存在 key 的情况
   2. 测试 key 相同时，自定义配置能否覆盖系统配置
2. 完整测试
   - 修改消费者和生产者示例项目中的配置文件，指定不同的序列化器
   - 然后依次启动生产者和消费者，验证能否正常完成 RPC 请求和响应
3. 自定义序列化器
   1. 写一个类实现 Serializer 接口
   2. 在 custom 目录下编写 SPI 配置文件，加载自己写的类



## 五、注册中心

### 需求分析

RPC 框架的一个核心模块是注册中心，目的是帮助服务消费者获取到服务提供者的调用地址，而不是将调用地址硬编码到项目中。

![image-20240325214548362](https://typora-1314662469.cos.ap-shanghai.myqcloud.com/img/202404111630145.png)

我们先来实现一个具有基本功能的注册中心，跑通注册中心的流程，之后再优化。

### 设计方案

#### 注册中心核心能力

我们先明确注册中心的几个实现关键（核心能力）：

1. 数据分布式存储：集中的注册信息数据存储、读取和共享
2. 服务注册：服务提供者上报服务信息到注册中心
3. 服务发现：服务消费者从注册中心拉取服务信息
4. 心跳检测：定期检查服务提供者的存活状态
5. 服务注销：手动剔除节点、或者自动剔除失效节点
6. 更多优化点：比如注册中心本身的容错、服务消费者缓存等。

#### 技术选型

主流的注册中心实现中间件有 ZooKeeper、Redis 等。这里我们使用一种更新颖的、更适合存储元信息（注册信息）的云原生中间件 Etcd，来实现注册中心。

#### Etcd

**Etcd介绍**

GitHub：https://github.com/etcd-io/etcd

Etcd 在其数据模型和组织结构上更接近于 ZooKeeper 和对象存储，而不是 Redis。它使用层次化的键值对来存储数据，支持类似于文件系统路径的层次结构，能够很灵活地单 key 查询、按前缀查询、按范围查询。

**Etcd 的核心数据结构包括：**

1. Key（键）：Etcd 中的基本数据单元，类似于文件系统中的文件名。每个键都唯一标识一个值，并且可以包含子键，形成类似于路径的层次结构。
2. Value（值）：与键关联的数据，可以是任意类型的数据，通常是字符串形式。

**Etcd 有很多核心特性**，其中，应用较多的特性是：

1. Lease（租约）：用于对键值对进行 TTL 超时设置，即设置键值对的过期时间。当租约过期时，相关的键值对将被自动删除。
2. Watch（监听）：可以监视特定键的变化，当键的值发生变化时，会触发相应的通知。

有了这些特性，我们就能够实现注册中心的服务提供者节点过期和监听了。

此外，Etcd 的一大优势就是能够保证数据的强一致性。

**Etcd 如何保证数据一致性？**

从表层来看，Etcd 支持事务操作，能够保证数据一致性。

从底层来看，Etcd 使用 Raft 一致性算法来保证数据的一致性。

Raft 是一种分布式一致性算法，它确保了分布式系统中的所有节点在任何时间点都能达成一致的数据视图。

> 具体来说，Raft 算法通过选举机制选举出一个领导者（Leader）节点，领导者负责接收客户端的写请求，并将写操作复制到其他节点上。当客户端发送写请求时，领导者首先将写操作写入自己的日志中，并将写操作的日志条目分发给其他节点，其他节点收到日志后也将其写入自己的日志中。一旦**大多数节点**（即半数以上的节点）都将该日志条目成功写入到自己的日志中，该日志条目就被视为已提交，领导者会向客户端发送成功响应。在领导者发送成功响应后，该写操作就被视为已提交，从而保证了数据的一致性。
>
> 如果领导者节点宕机或失去联系，Raft 算法会在其他节点中选**举出新的领导者**，从而保证系统的可用性和一致性。新的领导者会继续接收客户端的写请求，并负责将写操作复制到其他节点上，从而保持数据的一致性。

**Etcd 安装**

进入 Etcd 官方的下载页：https://github.com/etcd-io/etcd/releases

安装完成后，会得到 3 个脚本：

- etcd：etcd 服务本身
- etcdctl：客户端，用于操作 etcd，比如读写数据
- etcdutl：备份恢复工具

执行 etcd 脚本后，可以启动 etcd 服务，服务默认占用 2379 和 2380 端口，作用分别如下：

- 2379：提供 HTTP API 服务，和 etcdctl 交互
- 2380：集群中节点间通讯

**Etcd 可视化工具**

etcdkeeper：️https://github.com/evildecay/etcdkeeper/

**Etcd Java 客户端**

etcd 主流的 Java 客户端是 jetcd：https://github.com/etcd-io/jetcd

注意，Java 版本必须大于 11！

用法非常简单，就像 curator 能够操作 ZooKeeper、jedis 能够操作 Redis 一样

1）首先在项目中引入 jetcd：

```xml
<dependency>
    <groupId>io.etcd</groupId>
    <artifactId>jetcd-core</artifactId>
    <version>0.7.7</version>
</dependency>
```

2）按照官方文档的示例写 Demo 即可

常用的客户端和作用如下，仅作了解即可：

1. kvClient：用于对 etcd 中的键值对进行操作。通过 kvClient 可以进行设置值、获取值、删除值、列出目录等操作。
2. leaseClient：用于管理 etcd 的租约机制。租约是 etcd 中的一种时间片，用于为键值对分配生存时间，并在租约到期时自动删除相关的键值对。通过 leaseClient 可以创建、获取、续约和撤销租约。
3. watchClient：用于监视 etcd 中键的变化，并在键的值发生变化时接收通知。
4. clusterClient：用于与 etcd 集群进行交互，包括添加、移除、列出成员、设置选举、获取集群的健康状态、获取成员列表信息等操作。
5. authClient：用于管理 etcd 的身份验证和授权。通过 authClient 可以添加、删除、列出用户、角色等身份信息，以及授予或撤销用户或角色的权限。
6. maintenanceClient：用于执行 etcd 的维护操作，如健康检查、数据库备份、成员维护、数据库快照、数据库压缩等。
7. lockClient：用于实现分布式锁功能，通过 lockClient 可以在 etcd 上创建、获取、释放锁，能够轻松实现并发控制。
8. electionClient：用于实现分布式选举功能，可以在 etcd 上创建选举、提交选票、监视选举结果等。

绝大多数情况下，用前 3 个客户端就足够了。

#### 存储结构设计

1. key 如何设计？

   由于一个服务可能有多个服务提供者（负载均衡）

   1. 可以使用层级结构，将服务理解为文件夹、服务对应的多个节点理解为文件夹下的文件，可以用前缀查询方式查询到某个服务的所有节点，例如 `/业务前缀/服务名/服务节点地址`，比如`/rpc/service/localhost:8080`和`/rpc/service/256.167.108.56:8090`
   2. 也可以使用列表结构，将所有服务以列表的形式整体作为value

   选择哪种存储结构呢？这个也会跟我们的技术选型有关。对于 ZooKeeper 和 Etcd 这种支持层级查询的中间件，用第一种结构会更清晰；对于 Redis，由于本身就支持列表数据结构，可以选择第二种结构。

2. value 如何设计？

   存储服务节点的信息

3. key 什么时候过期？

   30 秒过期

### 开发实现

#### 1、注册中心开发

1）服务信息定义（服务端）

```java
/**
 * 服务元信息（注册信息）
 */
@Data
public class ServiceMetaInfo {
    /**
     * 服务名称
     */
    private String serviceName;
    /**
     * 服务版本号
     */
    private String serviceVersion = "1.0";
    /**
     * 服务主机名
     */
    private String serviceHost;
    /**
     * 服务端口号
     */
    private int servicePort;
    /**
     * 服务分组（暂未实现）
     */
    private String serviceGroup = "default";

    /**
     * 获取服务注册节点键名
     * @return
     */
    public String getServiceNodeKey() {
        return String.format("%s/%s", getServiceKey(), getServiceAddress());
    }

    /**
     * 获取服务键名
     * @return
     */
    public String getServiceKey() {
        // 后续可扩展服务分组
//        return String.format("%s:%s:%s", serviceName, serviceVersion, serviceGroup);
        return String.format("%s:%s", serviceName, serviceVersion);
    }

    /**
     * 获取完整服务地址
     * @return
     */
    public String getServiceAddress() {
        if (!StringUtils.contains(serviceHost, "http")) {
            return String.format("http://%s:%s", serviceHost, servicePort);
        }
        return String.format("%s:%s", serviceHost, servicePort);
    }
}
```

2）注册中心配置

```java
/**
 * Rpc框架注册中心配置
 */
@Data
public class RegistryConfig {
    /**
     * 注册中心类别
     */
    private String registry = "etcd";
    /**
     * 注册中心地址
     */
    private String address = "http://localhost:2379";
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;

    /**
     * 超时时间（毫秒）
     */
    private Long timeout = 10000L;
}
```

3）注册中心接口

遵循可扩展设计，我们先写一个注册中心接口，后续可以实现多种不同的注册中心，并且和序列化器一样，可以使用 SPI 机制动态加载。

注册中心接口代码如下，主要是提供了初始化、注册服务、注销服务、服务发现（获取服务节点列表）、服务销毁等方法。

```java
/**
 * 注册中心
 */
public interface Registry {
    /**
     * 初始化
     * @param registryConfig
     */
    void init(RegistryConfig registryConfig);

    /**
     * 服务注册（服务端）
     * @param serviceMetaInfo
     */
    void register(ServiceMetaInfo serviceMetaInfo) throws Exception;

    /**
     * 服务注销（服务端）
     * @param serviceMetaInfo
     */
    void unRegister(ServiceMetaInfo serviceMetaInfo);

    /**
     * 服务发现（消费端）
     * @param serviceKey 服务键名
     * @return
     */
    List<ServiceMetaInfo> serviceDiscovery(String serviceKey);

    /**
     * 服务销毁
     */
    void destroy();
}
```

4）Etcd 注册中心实现

```java
/**
 * Etcd服务注册中心
 */
public class EtcdRegistry implements Registry {

    private Client client;
    private KV kvClient;
    /**
     * 根节点
     */
    private String ETCD_ROOT_PATH = "/rpc/";

    @Override
    public void init(RegistryConfig registryConfig) {
        client = Client.builder()
                .endpoints(registryConfig.getAddress())
                // 注册中心的超时时间
                .connectTimeout(Duration.ofMillis(registryConfig.getTimeout()))
                .build();
        kvClient = client.getKVClient();
    }

    @Override
    public void register(ServiceMetaInfo serviceMetaInfo) throws Exception {
        // 创建Lease和KV客户端
        Lease leaseClient = client.getLeaseClient();
        // 创建一个30秒的租约(服务30秒过期)
        long leaseId = leaseClient.grant(30).get().getID();
        // 设置要存储的键值对
        String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
        ByteSequence key = ByteSequence.from(registerKey, StandardCharsets.UTF_8);
        ByteSequence value = ByteSequence.from(JSONUtil.toJsonStr(serviceMetaInfo), StandardCharsets.UTF_8);

        // 存储
        PutOption putOption = PutOption.builder().withLeaseId(leaseId).build();
        kvClient.put(key, value, putOption);
    }

    @Override
    public void unRegister(ServiceMetaInfo serviceMetaInfo) {
        ByteSequence key = ByteSequence.from(ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey(), StandardCharsets.UTF_8);
        kvClient.delete(key);
    }

    @Override
    public List<ServiceMetaInfo> serviceDiscovery(String serviceKey) {
        // 前缀搜索，结尾一定要加 /
        String searchPrefix = ETCD_ROOT_PATH + serviceKey + "/";
        try {
            GetOption getOption = GetOption.builder().isPrefix(true).build();
            List<KeyValue> keyValueList = kvClient.get(ByteSequence.from(searchPrefix, StandardCharsets.UTF_8),
                    getOption)
                    .get()
                    .getKvs();
            return keyValueList.stream().map(kv -> {
                String value = kv.getValue().toString(StandardCharsets.UTF_8);
                return JSONUtil.toBean(value, ServiceMetaInfo.class);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("获取服务列表失败", e);
        }
    }

    @Override
    public void destroy() {
        System.out.println("注册中心：当前节点下线");
        if (client != null) {
            client.close();
        }
        if (kvClient != null) {
            kvClient.close();
        }
    }
}
```

#### 2、支持配置和扩展

一个成熟的 RPC 框架可能会支持多个注册中心，像序列化器一样，我们的需求是，让开发者能够填写配置来指定使用的注册中心，并且支持自定义注册中心，让框架更易用、更利于扩展。

要实现这点，开发方式和序列化器也是一样的，都可以使用工厂创建对象、使用 SPI 动态加载自定义的注册中心。

1）注册中心常量

```java
/**
 * 注册中心键名常量
 */
public interface RegistryKeys {
    String ETCD = "etcd";
    String ZOOKEEPER = "zookeeper";
}
```

2）使用工厂模式，支持根据 key 从 SPI 获取注册中心对象

```java
/**
 * 注册中心工厂（用于获取注册中心对象）
 */
public class RegistryFactory {

    static {
        // 不需要一次性加载所有类，每次getInstance时再动态去加载
//        SpiLoader.load(Registry.class);
    }

    /**
     * 默认注册中心
     */
    private static final Registry DEFAULT_REGISTRY = new EtcdRegistry();

    /**
     * 获取实例
     * @param key
     * @return
     */
    public static Registry getInstance(String key) {
        return SpiLoader.getInstance(Registry.class, key);
    }
}
```

3）在 META-INF 的 rpc/system 目录下编写注册中心接口的 SPI 配置文件，文件名为 com.han.rpc.registry.Registry

```
etcd=com.han.rpc.registry.EtcdRegistry
```

4）最后，我们需要一个位置来初始化注册中心。由于服务提供者和服务消费者都需要和注册中心建立连接，是一个 RPC 框架启动必不可少的环节，所以可以将初始化流程放在 `RpcApplication` 类中

```java
/**
     * 初始化，可以自定义配置类
     * @param newRpcConfig
     */
    public static void init(RpcConfig newRpcConfig) {
        rpcConfig = newRpcConfig;
        log.info("rpc init, config = {}", rpcConfig.toString());
        // 注册中心初始化
        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
        Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());
        registry.init(registryConfig);
        log.info("registry init, config = {}", registryConfig.toString());
    }
```

#### 3、完成调用流程

下面我们要改造服务消费者调用服务的代码，跑通整个动态获取节点并调用的流程。

1）服务消费者需要先从注册中心获取节点信息，再得到调用地址并执行。修改服务代理 ServiceProxy 类，更改调用逻辑

修改的部分代码如下：

```java
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

// todo 暂时选择第一个服务地址
ServiceMetaInfo selectedServiceMetaInfo = serviceMetaInfoList.get(0);

try (HttpResponse httpResponse = HttpRequest.post(selectedServiceMetaInfo.getServiceAddress())
     .body(bytes)
     .execute()) {
    byte[] result = httpResponse.bodyBytes();
    // 4.将结果反序列化
    RpcResponse rpcResponse = serializer.deserialize(result, RpcResponse.class);
    return rpcResponse.getData();
}
```

注意，从注册中心获取到的服务节点地址可能是多个。上述代码中，我们为了方便，暂时先取第一个，之后会对这里的代码进行优化。

### 测试

#### 1、注册中心测试

首先验证注册中心能否正常完成服务注册、注销、服务发现。

编写单元测试类 `RegistryTest`，服务注册后，打开 EtcdKeeper 可视化界面，能够看到注册成功的服务节点信息

#### 2、完整流程测试

在 provider 模块下新增服务提供者示例类，需要初始化 RPC 框架并且将服务手动注册到注册中心上。

```java
/**
 * 服务提供者示例
 */
public class ProviderExample {
    public static void main(String[] args) {
        // RPC框架初始化
        RpcApplication.init();
        // 注册服务
        LocalRegister.register(UserService.class.getName(), UserServiceImpl.class);

        // 注册服务到注册中心
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName(UserService.class.getName());
        serviceMetaInfo.setServiceVersion("1.0");
        serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
        serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
        try {
            registry.register(serviceMetaInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 启动服务器
        VertxHttpServer httpServer = new VertxHttpServer();
        httpServer.doStart(RpcApplication.getRpcConfig().getServerPort());
    }
}
```

服务消费者的代码不用改动，我们依然是先启动提供者、再启动消费者，验证流程能否正常跑通。



## 六、注册中心优化

### 需求分析

目前系统仅仅是处于可用的程度，还有很多需要解决的问题和可优化点：

1. 数据一致性：服务提供者如果下线了，注册中心需要即时更新，剔除下线节点。否则消费者可能会调用到已经下线的节点。
2. 性能优化：服务消费者每次都需要从注册中心获取服务，可以使用缓存进行优化。
3. 高可用性：保证注册中心本身不会宕机。
4. 可扩展性：实现更多其他种类的注册中心。

### 心跳检测和续期机制

#### 心跳检测介绍

心跳检测（俗称 heartBeat）是一种用于监测系统是否正常工作的机制。它通过定期发送心跳信号（请求）来检测目标系统的状态。

如果接收方在一定时间内没有收到心跳信号或者未能正常响应请求，就会认为目标系统故障或不可用，从而触发相应的处理或告警机制。

#### 方案设计

1）从心跳检测的概念来看，实现心跳检测一般需要 2 个关键：定时、网络请求。

但是使用 Etcd 实现心跳检测会更简单一些，因为 Etcd 自带了 key 过期机制，我们不妨换个思路：给节点注册信息一个 “生命倒计时”，让节点定期 **续期**，重置 **自己的** 倒计时。如果节点已宕机，一直不续期，Etcd 就会对 key 进行过期删除。

1. 服务提供者向 Etcd 注册自己的服务信息，并在注册时设置 TTL（生存时间）。
2. Etcd 在接收到服务提供者的注册信息后，会自动维护服务信息的 TTL，并在 TTL 过期时删除该服务信息。
3. 服务提供者定期请求 Etcd 续签自己的注册信息，重写 TTL。

需要注意的是，续期时间一定要小于过期时间，允许一次容错的机会。

2）每个服务提供者都需要找到自己注册的节点、续期自己的节点，但问题是，怎么找到当前服务提供者项目自己的节点呢？

那就充分利用本地的特性，在服务提供者本地维护一个 **已注册节点集合**，注册时添加节点 key 到集合中，只需要续期集合内的 key 即可。

#### 开发实现

1）给注册中心 Registry 接口补充心跳检测方法，代码如下：

```java
/**
  * 心跳检测（服务端）
  */
void heartBeat();
```

2）维护续期节点集合。

在 EtcdRegistry 定义一个本机注册的节点 key 集合，用于维护续期：

```java
/**
  * 本机注册的节点key集合（用于维护续期，服务端）
  */
private final Set<String> localRegisterNodeKeySet = new HashSet<>();
```

在服务注册时，需要将节点添加到集合中，代码如下：

```java
public void register(ServiceMetaInfo serviceMetaInfo) throws Exception {
    // 创建Lease和KV客户端
    Lease leaseClient = client.getLeaseClient();
    // 创建一个30秒的租约(服务30秒过期)
    long leaseId = leaseClient.grant(30).get().getID();
    // 设置要存储的键值对
    String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
    ByteSequence key = ByteSequence.from(registerKey, StandardCharsets.UTF_8);
    ByteSequence value = ByteSequence.from(JSONUtil.toJsonStr(serviceMetaInfo), StandardCharsets.UTF_8);

    // 存储
    PutOption putOption = PutOption.builder().withLeaseId(leaseId).build();
    kvClient.put(key, value, putOption);

    // 添加节点信息到本地缓存
    localRegisterNodeKeySet.add(registerKey);
}
```

同理，在服务注销时，也要从集合中移除对应节点：

```java
// 从本地缓存中移除节点信息
localRegisterNodeKeySet.remove(registerKey);
```

3）在 EtcdRegistry 中实现 heartBeat 方法。

可以使用 Hutool 工具类的 CronUtil 实现定时任务，对所有集合中的节点执行 **重新注册** 操作，这是一个小 trick，就相当于续签了。

```java
/**
     * 心跳检测（服务端）
     */
@Override
public void heartBeat() {
    // 每10秒重新注册服务，相当于续期了
    CronUtil.schedule("*/10 * * * * *", new Task() {
        @Override
        public void execute() {
            // 遍历每个节点进行续期
            for (String key : localRegisterNodeKeySet) {
                try {
                    List<KeyValue> keyValueList = kvClient.get(ByteSequence.from(key, StandardCharsets.UTF_8))
                        .get()
                        .getKvs();
                    // 该节点已过期，需重新启动节点才能重新注册
                    if (CollUtil.isEmpty(keyValueList)) {
                        continue;
                    }
                    // 未过期，重新注册（相当于续期）
                    KeyValue keyValue = keyValueList.get(0);
                    String value = keyValue.getValue().toString(StandardCharsets.UTF_8);
                    ServiceMetaInfo serviceMetaInfo = JSONUtil.toBean(value, ServiceMetaInfo.class);
                    register(serviceMetaInfo);
                } catch (Exception e) {
                    throw new RuntimeException(key + "续签失败", e);
                }
            }
        }
    });

    // 支持秒级别定时任务
    CronUtil.setMatchSecond(true);
    CronUtil.start();
}
```

采用这种实现方案的好处是，即时 Etcd 注册中心的数据出现了丢失，通过心跳检测机制也会重新注册节点信息。

4）开启 heartBeat。

在注册中心初始化的 init 方法中，调用 heartBeat 方法即可。

```java
public void init(RegistryConfig registryConfig) {
    client = Client.builder()
        .endpoints(registryConfig.getAddress())
        // 注册中心的超时时间
        .connectTimeout(Duration.ofMillis(registryConfig.getTimeout()))
        .build();
    kvClient = client.getKVClient();
    // 心跳检测
    heartBeat();
}
```

#### 测试

启动服务提供端，使用可视化工具观察节点底部的过期时间，当 TTL 到 20 左右的时候，又会重置为 30，说明心跳检测和续期机制正常执行。

### 服务节点下线机制

当服务提供者节点宕机时，应该从注册中心移除掉已注册的节点，否则会影响消费端调用。所以我们需要设计一套服务节点下线机制。

#### 方案设计

服务节点下线又分为：

- 主动下线：服务提供者项目正常退出时，主动从注册中心移除注册信息。
- 被动下线：服务提供者项目异常推出时，利用 Etcd 的 key 过期机制自动移除。

被动下线已经可以利用 Etcd 的机制实现了，我们主要开发主动下线。问题是，怎么在 Java 项目正常退出时，执行某个操作呢？其实，非常简单，利用 JVM 的 ShutdownHook 就能实现。

JVM 的 ShutdownHook 是 Java 虚拟机提供的一种机制，允许开发者在 JVM 即将关闭之前执行一些清理工作或其他必要的操作，例如关闭数据库连接、释放资源、保存临时数据等。

1）完善 Etcd 注册中心的 destory 方法，补充下线节点的逻辑

```java
public void destroy() {
    log.info("注册中心：当前节点下线");
    // 下线节点（主动下线）
    for (String key : localRegisterNodeKeySet) {
        try {
            CompletableFuture<DeleteResponse> future = kvClient.delete(ByteSequence.from(key, StandardCharsets.UTF_8));
            future.join();
        } catch (Exception e) {
            throw new RuntimeException(key + "节点下线失败", e);
        }
    }
    // 释放资源
    if (client != null) {
        client.close();
    }
    if (kvClient != null) {
        kvClient.close();
    }
}
```

2）在 RpcApplication 的 init 方法中，注册 Shutdown Hook，当程序正常退出时会执行注册中心的 destroy 方法。

```java
public static void init(RpcConfig newRpcConfig) {
    rpcConfig = newRpcConfig;
    log.info("rpc init, config = {}", rpcConfig.toString());
    // 注册中心初始化
    RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
    Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());
    registry.init(registryConfig);
    log.info("registry init, config = {}", registryConfig.toString());

    // 创建并注册Shutdown Hook，JVM退出时执行操作，清理资源
    Runtime.getRuntime().addShutdownHook(new Thread(registry::destroy));
}
```

#### 测试

测试方法很简单：

1. 启动服务提供者，然后观察服务是否成功被注册
2. 正常停止服务提供者，然后观察服务信息是否被删除（比如调用System.exit(0)）

### 消费端服务缓存

正常情况下，服务节点信息列表的更新频率是不高的，所以在服务消费者从注册中心获取到服务节点信息列表后，完全可以 **缓存在本地**，下次就不用再请求注册中心获取了，能够提高性能。

1、增加本地缓存

本地缓存的实现很简单，用一个列表来存储服务信息即可，提供操作列表的基本方法，包括：写缓存、读缓存、清空缓存。

```java
/**
 * 注册中心服务本地缓存
 */
public class RegistryServiceCache {

    /**
     * 服务缓存
     */
    private Map<String, List<ServiceMetaInfo>> serviceCache = new HashMap<>();

    /**
     * 写缓存
     * @param serviceKey
     * @param newServiceCache
     */
    public void writeCache(String serviceKey, List<ServiceMetaInfo> newServiceCache) {
        serviceCache.put(serviceKey, newServiceCache);
    }

    /**
     * 读缓存
     * @param serviceKey
     * @return
     */
    public List<ServiceMetaInfo> readCache(String serviceKey) {
        return serviceCache.get(serviceKey);
    }

    /**
     * 删除某个key的缓存
     * @param serviceKey
     */
    public void deleteCache(String serviceKey) {
        List<ServiceMetaInfo> remove = serviceCache.remove(serviceKey);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        serviceCache.clear();
    }
}
```

2、使用本地缓存

1）修改 EtcdRegistry 的代码，使用本地缓存对象：

```java
/**
     * 注册中心服务缓存（消费端）
     */
private final RegistryServiceCache serviceCache = new RegistryServiceCache();
```

2）修改服务发现逻辑，优先从缓存获取服务；如果没有缓存，再从注册中心获取，并且设置到缓存中。

```java
public List<ServiceMetaInfo> serviceDiscovery(String serviceKey) {
    // 优先从缓存获取服务
    List<ServiceMetaInfo> cachedServiceMetaInfoList = serviceCache.readCache(serviceKey);
    if (cachedServiceMetaInfoList != null) {
        return cachedServiceMetaInfoList;
    }

    // 前缀搜索，结尾一定要加 /
    String searchPrefix = ETCD_ROOT_PATH + serviceKey + "/";
    try {
        GetOption getOption = GetOption.builder().isPrefix(true).build();
        List<KeyValue> keyValueList = kvClient.get(ByteSequence.from(searchPrefix, StandardCharsets.UTF_8),
                                                   getOption)
            .get()
            .getKvs();
        List<ServiceMetaInfo> serviceMetaInfoList = keyValueList.stream().map(kv -> {
            String key = kv.getKey().toString(StandardCharsets.UTF_8);
            // 监听key的变化
            watch(key, serviceKey);
            String value = kv.getValue().toString(StandardCharsets.UTF_8);
            return JSONUtil.toBean(value, ServiceMetaInfo.class);
        }).collect(Collectors.toList());

        // 写入消费端服务缓存
        serviceCache.writeCache(serviceKey, serviceMetaInfoList);
        return serviceMetaInfoList;
    } catch (Exception e) {
        throw new RuntimeException("获取服务列表失败", e);
    }
}
```

3、服务缓存更新 - 监听机制

当服务注册信息发生变更（比如节点下线）时，需要即时更新消费端缓存。问题是，怎么知道服务注册信息什么时候发生变更呢？

这就需要我们使用 Etcd 的 watch 监听机制，当监听的某个 key 发生修改或删除时，就会触发事件来通知监听者。

什么时候去创建 watch 监听器呢？

我们首先要明确 watch 监听是服务消费者还是服务提供者执行的。由于我们的目标是更新缓存，缓存是在服务消费端维护和使用的，所以也应该是服务消费端去 watch。也就是说，只有服务消费者执行的方法中，可以创建 watch 监听器，那么比较合适的位置就是服务发现方法（serviceDiscovery）。可以对本次获取到的所有服务节点 key 进行监听。

还需要防止重复监听同一个 key，可以通过定义一个已监听 key 的集合来实现。

1）Registry 注册中心接口补充监听 key 的方法，代码如下：

```java
public interface Registry {
    /**
     * 监听（消费端）
     * @param serviceNodeKey
     * @param serviceKey
     */
    void watch(String serviceNodeKey, String serviceKey);
}
```

2）EtcdRegistry 类中，新增监听 key 的集合。

```java
/**
     * 正在监听的key集合（消费端）
     */
private final Set<String> watchingKeySet = new ConcurrentHashSet<>();
```

3）在 EtcdRegistry 类中实现监听 key 的方法

通过调用 Etcd 的 watchClient 实现监听，如果出现了 DELETE key 删除事件，则清理服务注册缓存。

注意，即使 key 在注册中心被删除后再重新设置，之前的监听依旧生效。所以我们只监听首次加入到监听集合的 key，防止重复。

```java
/**
     * 监听（消费端）
     * @param serviceNodeKey
     * @param serviceKey
     */
@Override
public void watch(String serviceNodeKey, String serviceKey) {
    boolean newWatch = watchingKeySet.add(serviceNodeKey);
    if (!newWatch) {
        return;
    }
    // 之前未被监听，开启监听
    Watch watchClient = client.getWatchClient();
    watchClient.watch(ByteSequence.from(serviceNodeKey, StandardCharsets.UTF_8), response -> {
        for (WatchEvent event : response.getEvents()) {
            switch (event.getEventType()) {
                    // key删除时触发
                case DELETE:
                    // 清理注册服务缓存
                    // 注意这里存储的是serviceKey而不是serviceNodeKey
                    serviceCache.deleteCache(serviceKey);
                    break;
                case PUT:
                default:
                    break;
            }
        }
    });
}
```

4）在消费端获取服务时调用 watch 方法，对获取到的服务节点 key 进行监听。

### ZooKeeper 注册中心

其实和 Etcd 注册中心的实现方式极其相似，步骤如下：

1. 安装 ZooKeeper
2. 引入客户端依赖
3. 实现接口
4. SPI 补充 ZooKeeper 注册中心



## 七、自定义协议

### 需求分析

目前的 RPC 框架，我们使用 Vert.x 的 HttpServer 作为服务提供者的服务器，代码实现比较简单，其底层网络传输使用的是 HTTP 协议。

一般情况下，RPC 框架会比较注重性能，而 HTTP 协议中的头部信息、请求响应格式较 “重”，会影响网络传输性能。

所以，我们需要自己自定义一套 RPC 协议，比如利用 TCP 等传输层协议、自己定义请求响应结构，来实现性能更高、更灵活、更安全的 RPC 框架。

### 系统设计

#### 1、网络传输设计

HTTP 本身是应用层协议，我们现在设计的 RPC 协议也是应用层协议，性能肯定是不如底层（传输层）的 TCP 协议要高的。所以我们想要追求更高的性能，还是选择使用 TCP 协议完成网络传输，有更多的自主设计空间。

#### 2、消息结构设计

消息结构设计的目标是：用 **最少的** 空间传递 **需要的** 信息

1）最少的空间

我们在自定义消息结构时，想要节省空间，就要尽可能使用更轻量的类型，比如 byte 字节类型，只占用 1 个字节、8 个 bit 位。

需要注意的是，Java 中实现 bit 位运算拼接相对比较麻烦，所以权衡开发成本，我们设计消息结构时，尽量给每个数据凑到整个字节。

2）需要的信息

分析 HTTP 请求结构，我们能够得到 RPC 消息所需的信息：

- 魔数：作用是安全校验，防止服务器处理了非框架发来的乱七八糟的消息（类似 HTTPS 的安全证书）
- 版本号：保证请求和响应的一致性（类似 HTTP 协议有 1.0/2.0 等版本）
- 序列化方式：来告诉服务端和客户端如何解析数据（类似 HTTP 的 Content-Type 内容类型）
- 类型：标识是请求还是响应？或者是心跳检测等其他用途。（类似 HTTP 有请求头和响应头）
- 状态：如果是响应，记录响应的结果（类似 HTTP 的 200 状态代码）

此外，还需要有请求 id，唯一标识某个请求，因为 TCP 是双向通信的，需要有个唯一标识来追踪每个请求。

最后，也是最重要的，要发送 body 内容数据。我们暂时称它为 **请求体**，类似于我们之前 HTTP 请求中发送的 RpcRequest。

所以我们需要在消息头中新增一个字段 请求体数据长度，保证能够完整地获取 body 内容信息。

![image-20240408135230394](https://typora-1314662469.cos.ap-shanghai.myqcloud.com/img/202404111630146.png)

实际上，这些数据应该是紧凑的，请求头信息总长 17 个字节。也就是说，上述消息结构，本质上就是拼接在一起的一个字节数组。我们后续实现时，需要有 **消息编码器** 和 **消息解码器**，编码器先 new 一个空的 Buffer 缓冲区，然后按照顺序向缓冲区依次写入这些数据；解码器在读取时也按照顺序依次读取，就能还原出编码前的数据。

### 开发实现

#### 1、消息结构

1）新建协议消息类 ProtocolMessage

将消息头单独封装为一个内部类，消息体可以使用泛型类型，完整代码如下：

```java
/**
 * 协议消息结构
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProtocolMessage<T> {
    /**
     * 消息头
     */
    private Header header;

    /**
     * 消息体（请求或响应对象）
     */
    private T body;

    /**
     * 协议消息头
     */
    @Data
    public static class Header {
        /**
         * 魔数，保证安全性
         */
        private byte magic;

        /**
         * 版本
         */
        private byte version;

        /**
         * 序列化器
         */
        private byte serializer;

        /**
         * 消息类型（请求/响应）
         */
        private byte type;

        /**
         * 状态
         */
        private byte status;

        /**
         * 请求ID
         */
        private long requestId;

        /**
         * 消息体长度
         */
        private int bodyLength;
    }
}
```

2）新建协议常量类 ProtocolConstant，记录了和自定义协议有关的关键信息，比如消息头长度、魔数、版本号。

3）新建消息字段的枚举类，比如：

- 协议状态枚举，暂时只定义成功、请求失败、响应失败三种枚举值。
- 协议消息类型枚举，包括请求、响应、心跳、其他。
- 协议消息的序列化器枚举，跟我们 RPC 框架已支持的序列化器对应。

#### 2、网络传输

我们的 RPC 框架使用了高性能的 Vert.x 作为网络传输服务器，之前用的是 HttpServer。同样，Vert.x 也支持 TCP 服务器，相比于 Netty 或者自己写 Socket 代码，更加简单易用。

首先新建 server.tcp 包，将所有 TCP 服务相关的代码放到该包中。

1）TCP 服务器实现。

```java
public class VertxTcpServer implements HttpServer {

    @Override
    public void doStart(int port) {
        // 创建vertx实例
        Vertx vertx = Vertx.vertx();
        // 创建TCP服务器
        NetServer server = vertx.createNetServer();

        // 处理请求
        server.connectHandler(new TcpServerHandler());

        // 启动TCP服务器并监听指定端口
        server.listen(port, result -> {
            if (result.succeeded()) {
                System.out.println("TCP Server starts successfully on port " + port);
            } else {
                System.err.println("Failed to start TCP server: " + result.cause());
            }
        });
    }
}
```

2）TCP 客户端实现。

```java
public class VertxTcpClient {
    public static RpcResponse doRequest(RpcRequest rpcRequest, ServiceMetaInfo serviceMetaInfo) throws ExecutionException, InterruptedException {
        // 发送TCP请求
        Vertx vertx = Vertx.vertx();
        NetClient netClient = vertx.createNetClient();

        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
        netClient.connect(serviceMetaInfo.getServicePort(), serviceMetaInfo.getServiceHost(),
                result -> {
                    if (!result.succeeded()) {
                        System.err.println("Failed to connect to TCP server");
                        return;
                    }
                    System.out.println("Connected to TCP server");
                    NetSocket socket = result.result();
                    // 发送请求
                    // 1.构造消息
                    ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
                    ProtocolMessage.Header header = new ProtocolMessage.Header();
                    header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
                    header.setVersion(ProtocolConstant.PROTOCOL_VERSION);
                    header.setSerializer((byte) ProtocolMessageSerializerEnum.getEnumByValue(RpcApplication.getRpcConfig().getSerializer()).getKey());
                    header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
//                    header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());
                    // 生成全局请求ID
                    header.setRequestId(IdUtil.getSnowflakeNextId());
                    protocolMessage.setHeader(header);
                    protocolMessage.setBody(rpcRequest);

                    // 2.编码请求
                    try {
                        Buffer buffer = ProtocolMessageEncoder.encode(protocolMessage);
                        socket.write(buffer);
                    } catch (IOException e) {
                        throw new RuntimeException("协议消息编码错误");
                    }
                    // 3.接收响应
                    TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
                        try {
                            ProtocolMessage<RpcResponse> responseProtocolMessage =
                                    (ProtocolMessage<RpcResponse>) ProtocolMessageDecoder.decode(buffer);
                            // 由于 Vert.x 提供的请求处理器是异步、反应式的，我们为了更方便地获取结果，可以使用CompletableFuture转异步为同步
                            responseFuture.complete(responseProtocolMessage.getBody());
                        } catch (IOException e) {
                            throw new RuntimeException("协议消息解码错误");
                        }
                    });
                    socket.handler(bufferHandlerWrapper);

                });

        // 阻塞，直到完成了响应，才会继续向下执行
        RpcResponse rpcResponse = responseFuture.get();
        // 记得关闭连接
        netClient.close();
        return rpcResponse;
    }
}
```

#### 3、编码 / 解码器

在上一步中，我们也注意到了，Vert.x 的 TCP 服务器收发的消息是 Buffer 类型，不能直接写入一个对象。因此，我们需要编码器和解码器，将 Java 的消息对象和 Buffer 进行相互转换。

![image-20240408140234423](https://typora-1314662469.cos.ap-shanghai.myqcloud.com/img/202404111630147.png)

1）首先实现消息编码器。

在 protocol 包下新建 ProtocolMessageEncoder，核心流程是依次向 Buffer 缓冲区写入消息对象里的字段。

```java
/**
 * 消息编码器
 */
public class ProtocolMessageEncoder {

    /**
     * 编码
     *
     * @param protocolMessage
     * @return
     * @throws IOException
     */
    public static Buffer encode(ProtocolMessage<?> protocolMessage) throws IOException {
        if (protocolMessage == null || protocolMessage.getHeader() == null) {
            return Buffer.buffer();
        }
        ProtocolMessage.Header header = protocolMessage.getHeader();
        // 依次向缓冲区写入字节
        Buffer buffer = Buffer.buffer();
        buffer.appendByte(header.getMagic());
        buffer.appendByte(header.getVersion());
        buffer.appendByte(header.getSerializer());
        buffer.appendByte(header.getType());
        buffer.appendByte(header.getStatus());
        buffer.appendLong(header.getRequestId());

        // 将body部分序列化后写入buffer
        ProtocolMessageSerializerEnum serializerEnum = ProtocolMessageSerializerEnum.getEnumByKey(header.getSerializer());
        if (serializerEnum == null) {
            throw new RuntimeException("序列化协议不存在");
        }

        Serializer serializer = SerializerFactory.getInstance(serializerEnum.getValue());
        byte[] bodyBytes = serializer.serialize(protocolMessage.getBody());

        buffer.appendInt(bodyBytes.length);
        buffer.appendBytes(bodyBytes);
        return buffer;
    }
}
```

2）实现消息解码器。

在 protocol 包下新建 ProtocolMessageDecoder，核心流程是依次从 Buffer 缓冲区的指定位置读取字段，构造出完整的消息对象。

```java
/**
 * 消息解码器
 */
public class ProtocolMessageDecoder {

    /**
     * 解码
     * @param buffer
     * @return
     * @throws IOException
     */
    public static ProtocolMessage<?> decode(Buffer buffer) throws IOException {
        ProtocolMessage.Header header = new ProtocolMessage.Header();

        // 1.依次从指定位置读取消息头的数据
        byte magic = buffer.getByte(0);
        if (magic != ProtocolConstant.PROTOCOL_MAGIC) {
            throw new RuntimeException("消息 magic 非法");
        }
        header.setMagic(magic);
        header.setVersion(buffer.getByte(1));
        header.setSerializer(buffer.getByte(2));
        header.setType(buffer.getByte(3));
        header.setStatus(buffer.getByte(4));
        header.setRequestId(buffer.getLong(5));
        header.setBodyLength(buffer.getInt(13));
        // 解决粘包问题，只读指定长度的数据
        byte[] bodyBytes = buffer.getBytes(17, 17 + header.getBodyLength());

        // 2.反序列化消息体
        ProtocolMessageSerializerEnum serializerEnum = ProtocolMessageSerializerEnum.getEnumByKey(header.getSerializer());
        if (serializerEnum == null) {
            throw new RuntimeException("序列化协议不存在");
        }
        Serializer serializer = SerializerFactory.getInstance(serializerEnum.getValue());

        ProtocolMessageTypeEnum typeEnum = ProtocolMessageTypeEnum.getEnumByKey(header.getType());
        if (typeEnum == null) {
            throw new RuntimeException("序列化消息的类型不存在");
        }
        switch (typeEnum) {
            case REQUEST:
                RpcRequest request = serializer.deserialize(bodyBytes, RpcRequest.class);
                return new ProtocolMessage<>(header, request);
            case RESPONSE:
                RpcResponse response = serializer.deserialize(bodyBytes, RpcResponse.class);
                return new ProtocolMessage<>(header, response);
            case HEART_BEAT:
            case OTHERS:
            default:
                throw new RuntimeException("暂不支持该消息类型");
        }
    }
}
```

3）编写单元测试类，先编码再解码，以测试编码器和解码器的正确性。

#### 4、请求处理器（服务提供者）

可以使用 netty 的 pipeline 组合多个 handler（比如 解码 => 请求 / 响应处理 => 编码）

请求处理器的作用是接受请求，然后通过反射调用服务实现类。

类似之前的 HttpServerHandler，我们需要开发一个 TcpServerHandler，用于处理请求。和 HttpServerHandler 的区别只是在获取请求、写入响应的方式上，需要调用上面开发好的编码器和解码器。

通过实现 Vert.x 提供的 `Handler<NetSocket>` 接口，可以定义 TCP 请求处理器。

```java
/**
 * TCP请求处理器（服务提供端）
 */
public class TcpServerHandler implements Handler<NetSocket> {

    @Override
    public void handle(NetSocket socket) {
        // 使用装饰者模式解决半包粘包问题
        TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
            // 解决半包问题
            if (buffer == null || buffer.length() == 0) {
                throw new RuntimeException("消息 buffer 为空");
            }
            if (buffer.getBytes().length < ProtocolConstant.MESSAGE_HEADER_LENGTH) {
                throw new RuntimeException("出现了半包问题");
            }
            // 1.接受请求，解码
            ProtocolMessage<RpcRequest> protocolMessage;
            try {
                protocolMessage = (ProtocolMessage<RpcRequest>) ProtocolMessageDecoder.decode(buffer);
            } catch (IOException e) {
                throw new RuntimeException("协议消息解码错误");
            }
            RpcRequest rpcRequest = protocolMessage.getBody();

            // 2.处理请求
            RpcResponse rpcResponse = new RpcResponse();
            try {
                // 反射调用服务方法
                Class<?> implClass = LocalRegister.get(rpcRequest.getServiceName());
                Method method = implClass.getMethod(rpcRequest.getMethodName(), rpcRequest.getParameterTypes());
                Object result = method.invoke(implClass.newInstance(), rpcRequest.getArgs());
                // 封装返回结果
                rpcResponse.setData(result);
                rpcResponse.setDataType(method.getReturnType());
                rpcResponse.setMessage("ok");
            } catch (Exception e) {
                e.printStackTrace();
                rpcResponse.setMessage(e.getMessage());
                rpcResponse.setException(e);
            }

            // 3.发送响应，编码
            ProtocolMessage.Header header = protocolMessage.getHeader();
            header.setType((byte) ProtocolMessageTypeEnum.RESPONSE.getKey());
            ProtocolMessage<RpcResponse> responseProtocolMessage = new ProtocolMessage<>(header, rpcResponse);

            try {
                Buffer encode = ProtocolMessageEncoder.encode(responseProtocolMessage);
                socket.write(encode);
            } catch (IOException e) {
                throw new RuntimeException("协议消息编码错误");
            }
        });
        socket.handler(bufferHandlerWrapper);
    }
}
```

#### 5、请求发送（服务消费者）

调整服务消费者发送请求的代码，改 HTTP 请求为 TCP 请求。

```java
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
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName(serviceName);
        serviceMetaInfo.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);

        List<ServiceMetaInfo> serviceMetaInfoList = registry.serviceDiscovery(serviceMetaInfo.getServiceKey());
        if (CollUtil.isEmpty(serviceMetaInfoList)) {
            throw new RuntimeException("暂无服务地址");
        }

        // todo 暂时选择第一个服务地址，如果有多个服务集群，可以负载均衡
        ServiceMetaInfo selectedServiceMetaInfo = serviceMetaInfoList.get(0);

        // 4.发送TCP请求
        RpcResponse rpcResponse = VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo);

        return rpcResponse.getData();
    }
}
```

这里的代码看着比较复杂，但只需要关注上述代码中注释了 “发送 TCP 请求” 的部分即可。由于 Vert.x 提供的请求处理器是异步、反应式的，我们为了更方便地获取结果，可以使用 `CompletableFuture` 转异步为同步，参考代码如下：

```java
CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
netClient.connect(xxx, result -> {
                      // 完成了响应
                      responseFuture.complete(responseProtocolMessage.getBody());
});
// 阻塞，直到完成了响应，才会继续向下执行
RpcResponse rpcResponse = responseFuture.get();
```

### 半包粘包

#### 介绍

TCP 粘包、拆包问题：https://zhuanlan.zhihu.com/p/356225028

TCP粘包问题是由于TCP是面向流，没有边界，而操作系统在发送TCP数据时，会通过缓冲区来进行优化，例如缓冲区为1024个字节大小。 如果一次请求发送的数据量比较小，没达到缓冲区大小，TCP则会将多个请求合并为同一个请求进行发送，这就形成了粘包问题。 如果一次请求发送的数据量比较大，超过了缓冲区大小，TCP就会将其拆分为多次发送，这就是拆包。解决TCP粘包和分包问题的方法通常包括：

- 定长消息：固定每条消息的长度，这样可以根据长度字段确定消息边界。
- 封包标记：在每个数据包的头部增加特殊标识符，如消息头包含类型或长度信息，使得接收方能够识别单个消息的开始和结束位置

#### 如何解决半包？

解决半包的核心思路是：在消息头中设置请求体的长度，服务端接收时，判断每次消息的长度是否符合预期，不完整就不读，留到下一次接收到消息时再读取。在 Handler 中示例代码如下：

```java
if (buffer == null || buffer.length() == 0) {
    throw new RuntimeException("消息 buffer 为空");
}
if (buffer.getBytes().length < ProtocolConstant.MESSAGE_HEADER_LENGTH) {
    throw new RuntimeException("出现了半包问题");
}
```

#### 如何解决粘包？

解决粘包的核心思路也是类似的：每次只读取指定长度的数据，超过长度的留着下一次接收到消息时再读取。在解码器 Decoder 中示例代码如下：

```java
// 解决粘包问题，只读指定长度的数据
byte[] bodyBytes = buffer.getBytes(17, 17 + header.getBodyLength());
```

听上去简单，但自己实现起来还是比较麻烦的，要记录每次接收到的消息位置，维护字节数组缓存。有没有更简单的方式呢？

#### Vert.x 解决半包和粘包

在 Vert.x 框架中，可以使用内置的 `RecordParser`  完美解决半包粘包，它的作用是：保证下次读取到 **特定长度** 的字符。

先不要急着直接修改业务代码，而是先学会该类库的使用，跑通测试流程，再引入到自己的业务代码中。

测试代码略...

实际运用中，消息体的长度是不固定的，所以要通过调整 RecordParser 的固定长度（变长）来解决。

那我们的思路可以是，将读取完整的消息拆分为 2 次：

1. 先完整读取请求头信息，由于请求头信息长度是固定的，可以使用 RecordParser 保证每次都完整读取。
2. 再根据请求头长度信息更改 RecordParser 的固定长度，保证完整获取到请求体。

测试代码略...

#### 封装半包粘包处理器

我们会发现，解决半包粘包问题还是有一定的代码量的，而且由于 ServiceProxy（消费者）和请求 Handler（提供者）都需要接受 Buffer，所以都需要半包粘包问题处理。

那我们就应该要想到：需要对代码进行封装复用了。

这里我们可以使用设计模式中的 **装饰者模式**，使用 RecordParser 对原有的 Buffer 处理器的能力进行增强。装饰者模式可以简单理解为给对象穿装备，增强对象的能力。

在 server.tcp 包下新建 TcpBufferHandlerWrapper 类，实现并增强 Handler<Buffer> 接口，代码如下：

```java
/**
 * 装饰者模式（使用 recordParser 对原有 handler处理能力进行增强）
 * 解决半包粘包问题
 */
public class TcpBufferHandlerWrapper implements Handler<Buffer> {
    // RecordParser也是一个Handler，可以保证下次读取到特定长度的字节
    private final RecordParser recordParser;

    public TcpBufferHandlerWrapper(Handler<Buffer> bufferHandler) {
        this.recordParser = initRecordParser(bufferHandler);
    }
    
    @Override
    public void handle(Buffer buffer) {
        recordParser.handle(buffer);
    }

    private RecordParser initRecordParser(Handler<Buffer> bufferHandler) {
        // 构造parser
        RecordParser parser = RecordParser.newFixed(ProtocolConstant.MESSAGE_HEADER_LENGTH);

        parser.setOutput(new Handler<Buffer>() {
            // 初始化
            int size = -1;
            Buffer resultBuffer = Buffer.buffer();

            @Override
            public void handle(Buffer buffer) {
                if (size == -1) {
                    // 读取消息体长度，设置下一次读取的数据长度
                    size = buffer.getInt(13);
                    parser.fixedSizeMode(size);
                    // 写入消息头
                    resultBuffer.appendBuffer(buffer);
                } else {
                    // 写入消息体
                    resultBuffer.appendBuffer(buffer);
                    // 已拼接为完整buffer，使用handler进行处理(未增强的原始处理逻辑)
                    bufferHandler.handle(resultBuffer);
                    // 重置，下一轮读取
                    size = -1;
                    resultBuffer = Buffer.buffer();
                    parser.fixedSizeMode(ProtocolConstant.MESSAGE_HEADER_LENGTH);
                }
            }
        });

        return parser;
    }
}
```

其实就是把 RecordParser 的代码粘了过来，当调用处理器的 handle 方法时，改为调用 recordParser.handle。

#### 优化客户端调用

1）修改 TCP 请求处理器。

使用 TcpBufferHandlerWrapper 来封装之前处理请求的代码，请求逻辑不用变，需要修改的部分代码如下：

```java
/**
 * TCP请求处理器（服务提供端）
 */
public class TcpServerHandler implements Handler<NetSocket> {

    @Override
    public void handle(NetSocket socket) {
        // 使用装饰者模式解决半包粘包问题
        TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
            // 处理请求代码
        });
        socket.handler(bufferHandlerWrapper);
    }
}
```

其实就是使用一个 Wrapper 对象 **包装** 了之前的代码，就解决了半包粘包。是不是很简单？这就是装饰者模式的妙用！

2）修改客户端处理响应的代码。

之前我们是把所有发送请求、处理响应的代码都写到了 ServiceProxy 中，使得这个类的代码 “臃肿不堪”。我们干脆做个优化，把所有的请求响应逻辑提取出来，封装为单独的 VertxTcpClient 类，放在 server.tcp 包下。

```java
public class VertxTcpClient {
    public static RpcResponse doRequest(RpcRequest rpcRequest, ServiceMetaInfo serviceMetaInfo) throws ExecutionException, InterruptedException {
        // 发送TCP请求
        Vertx vertx = Vertx.vertx();
        NetClient netClient = vertx.createNetClient();

        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
        netClient.connect(serviceMetaInfo.getServicePort(), serviceMetaInfo.getServiceHost(),
                result -> {
                    if (!result.succeeded()) {
                        System.err.println("Failed to connect to TCP server");
                        return;
                    }
                    System.out.println("Connected to TCP server");
                    NetSocket socket = result.result();
                    // 发送请求
                    // 1.构造消息
                    ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
                    ProtocolMessage.Header header = new ProtocolMessage.Header();
                    header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
                    header.setVersion(ProtocolConstant.PROTOCOL_VERSION);
                    header.setSerializer((byte) ProtocolMessageSerializerEnum.getEnumByValue(RpcApplication.getRpcConfig().getSerializer()).getKey());
                    header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
                    header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());
                    // 生成全局请求ID
                    header.setRequestId(IdUtil.getSnowflakeNextId());
                    protocolMessage.setHeader(header);
                    protocolMessage.setBody(rpcRequest);

                    // 2.编码请求
                    try {
                        Buffer buffer = ProtocolMessageEncoder.encode(protocolMessage);
                        socket.write(buffer);
                    } catch (IOException e) {
                        throw new RuntimeException("协议消息编码错误");
                    }
                    // 3.接收响应
                    TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
                        try {
                            ProtocolMessage<RpcResponse> responseProtocolMessage =
                                    (ProtocolMessage<RpcResponse>) ProtocolMessageDecoder.decode(buffer);
                            // 由于 Vert.x 提供的请求处理器是异步、反应式的，我们为了更方便地获取结果，可以使用CompletableFuture转异步为同步
                            responseFuture.complete(responseProtocolMessage.getBody());
                        } catch (IOException e) {
                            throw new RuntimeException("协议消息解码错误");
                        }
                    });
                    socket.handler(bufferHandlerWrapper);

                });

        // 阻塞，直到完成了响应，才会继续向下执行
        RpcResponse rpcResponse = responseFuture.get();
        // 记得关闭连接
        netClient.close();
        return rpcResponse;
    }
}
```

注意，上述代码中，也使用了 TcpBufferHandlerWrapper 对处理响应的代码进行了封装。

修改 ServiceProxy 代码，调用 VertxTcpClient，修改后的代码如下：

```java
// 4.发送TCP请求
RpcResponse rpcResponse = VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo);

return rpcResponse.getData();
```

## 八、负载均衡

### 需求分析

现在我们的 RPC 框架已经可以从注册中心获取到服务提供者的注册信息了，同一个服务可能会有多个服务提供者，我们完全可以从服务提供者节点中，选择一个服务提供者发起请求，而不是每次都请求同一个服务提供者，这个操作就叫做 **负载均衡**。

### 负载均衡

#### 1、什么是负载均衡

负载均衡是一种用来分配网络或计算负载到多个资源上的技术。它的目的是确保每个资源都能够有效地处理负载、增加系统的并发量、避免某些资源过载而导致性能下降或服务不可用的情况。

回归到我们的 RPC 框架，负载均衡的作用是从一组可用的服务提供者中选择一个进行调用。

常用的负载均衡实现技术有 Nginx（七层负载均衡）、LVS（四层负载均衡）等。推荐阅读一篇负载均衡入门文章：[什么是负载均衡？](https://www.codefather.cn/%E4%BB%80%E4%B9%88%E6%98%AF%E8%B4%9F%E8%BD%BD%E5%9D%87%E8%A1%A1/)

#### 2、常见负载均衡算法

1）轮询（Round Robin）：按照循环的顺序将请求分配给每个服务器，适用于各服务器性能相近的情况。

2）随机（Random）：随机选择一个服务器来处理请求，适用于服务器性能相近且负载均匀的情况。

3）加权轮询（Weighted Round Robin）：根据服务器的性能或权重分配请求，性能更好的服务器会获得更多的请求，适用于服务器性能不均的情况。

假如有 1 台千兆带宽的服务器节点和 4 台百兆带宽的服务器节点，请求调用顺序可能如下：

```
1,1,1,2  1,1,1,3  1,1,1,4  1,1,1,5
```

4）加权随机（Weighted Random）：根据服务器的权重随机选择一个服务器处理请求，适用于服务器性能不均的情况。

5）最小连接数（Least Connections）：选择当前连接数最少的服务器来处理请求，适用于长连接场景。

6）IP Hash：根据客户端 IP 地址的哈希值选择服务器处理请求，确保同一客户端的请求始终被分配到同一台服务器上，适用于需要保持会话一致性的场景。

当然，也可以根据请求中的其他参数进行 Hash，比如根据请求接口的地址路由到不同的服务器节点。

#### 3、一致性 Hash

一致性哈希（Consistent Hashing）是一种经典的哈希算法，用于将请求分配到多个节点或服务器上，所以非常适用于负载均衡。

它的核心思想是将整个哈希值空间划分成一个环状结构，每个节点或服务器在环上占据一个位置，每个请求根据其哈希值映射到环上的一个点，然后顺时针寻找第一个大于或等于该哈希值的节点，将请求路由到该节点上。

一致性哈希环结构如图：

![img](https://article-images.zsxq.com/FjYbr13I11vFbOE4lxOtlwtBSpqE)

上图中，请求 A 会交给服务器 C 来处理。

好像也没什么特别的啊？还整个环？

其实，一致性哈希还解决了 **节点下线** 和 **倾斜问题**。

1）节点下线：当某个节点下线时，其负载会被平均分摊到其他节点上，而不会影响到整个系统的稳定性，因为只有部分请求会受到影响。

如下图，服务器 C 下线后，请求 A 会交给服务器 A 来处理（顺时针寻找第一个大于或等于该哈希值的节点），而服务器 B 接收到的请求保持不变。

![img](https://article-images.zsxq.com/FjHgjuhaSyX39nID4JBU0yUSwRPX)

如果是轮询取模算法，只要节点数变了，很有可能大多数服务器处理的请求都要发生变化，对系统的影响巨大。

2）倾斜问题：通过虚拟节点的引入，将每个物理节点映射到多个虚拟节点上，使得节点在哈希环上的 **分布更加均匀**，减少了节点间的负载差异。

引入虚拟节点后，环的情况变为：

![img](https://article-images.zsxq.com/FrMrtDGPdklYADbNkpVT6Yc6EGdZ)

这样一来，每个服务器接受到的请求会更容易平均。

### 开发实现

#### 1、负载均衡器实现

大家学习负载均衡的时候，可以参考 Nginx 的负载均衡算法实现，此处我们实现轮询、随机、一致性 Hash 三种负载均衡算法。

在 RPC 项目中新建 loadbalancer 包，将所有负载均衡器相关的代码放到该包下。

1）先编写负载均衡器通用接口。提供一个选择服务方法，接受请求参数和可用服务列表，可以根据这些信息进行选择。

```java
/**
 * 负载均衡器（消费端使用）
 */
public interface LoadBalancer {

    /**
     * 选择服务调用
     * @param requestParams 请求参数
     * @param serviceMetaInfoList 可用服务列表
     * @return
     */
    ServiceMetaInfo select(Map<String, Object> requestParams, List<ServiceMetaInfo> serviceMetaInfoList);
}
```

2）轮询负载均衡器。

使用 JUC 包的 AtomicInteger 实现原子计数器，防止并发冲突问题。

```java
/**
 * 轮询负载均衡器
 */
public class RoundRobinLoadBalancer implements LoadBalancer {
    /**
     * 当前轮询的下标
     */
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    @Override
    public ServiceMetaInfo select(Map<String, Object> requestParams, List<ServiceMetaInfo> serviceMetaInfoList) {
        if (CollUtil.isEmpty(serviceMetaInfoList)) {
            return null;
        }
        int size = serviceMetaInfoList.size();
        // 只有一个服务，无需轮询
        if (size == 1) {
            return serviceMetaInfoList.get(0);
        }
        int index = currentIndex.getAndIncrement() % size;
        return serviceMetaInfoList.get(index);
    }
}
```

3）随机负载均衡器。

使用 Java 自带的 Random 类实现随机选取即可，代码如下：

```java
/**
 * 随机负载均衡器
 */
public class RandomLoadBalancer implements LoadBalancer {
    private final Random random = new Random();

    @Override
    public ServiceMetaInfo select(Map<String, Object> requestParams, List<ServiceMetaInfo> serviceMetaInfoList) {
        if (CollUtil.isEmpty(serviceMetaInfoList)) {
            return null;
        }
        int size = serviceMetaInfoList.size();
        // 只有一个服务
        if (size == 1) {
            return serviceMetaInfoList.get(0);
        }
        return serviceMetaInfoList.get(random.nextInt(size));
    }
}
```

4）实现一致性 Hash 负载均衡器。

可以使用 TreeMap 实现一致性 Hash 环，该数据结构提供了 ceilingEntry 和 firstEntry 两个方法，便于获取符合算法要求的节点。

```java
/**
 * 一致性哈希负载均衡器
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {
    /**
     * 一致性Hash环，存放虚拟节点
     */
    private final TreeMap<Integer, ServiceMetaInfo> virtualNodes = new TreeMap<>();

    /**
     * 虚拟节点数
     */
    private static final int VIRTUAL_NODE_NUM = 100;

    @Override
    public ServiceMetaInfo select(Map<String, Object> requestParams, List<ServiceMetaInfo> serviceMetaInfoList) {
        if (CollUtil.isEmpty(serviceMetaInfoList)) {
            return null;
        }
        // 只有一个服务
        if (serviceMetaInfoList.size() == 1) {
            return serviceMetaInfoList.get(0);
        }
        // 构建虚拟节点环，每次调用select都会重新构造节点环，这是为了即时处理节点的变化
        virtualNodes.clear();
        for (ServiceMetaInfo serviceMetaInfo : serviceMetaInfoList) {
            for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
                int hash = getHash(serviceMetaInfo.getServiceAddress() + "#" + i);
                virtualNodes.put(hash, serviceMetaInfo);
            }
        }
        // 获取调用请求的hash值
        int hash = getHash(requestParams);
        Map.Entry<Integer, ServiceMetaInfo> entry = virtualNodes.ceilingEntry(hash);
        if (entry == null) {
            // 没有大于等于调用请求节点hash值的虚拟节点，取环首部第一个节点
            entry = virtualNodes.firstEntry();
        }
        return entry.getValue();
    }

    /**
     * Hash算法，可自行实现
     * @param obj
     * @return
     */
    private int getHash(Object obj) {
        return obj.hashCode();
    }
}
```

上述代码中，注意两点：

1. 根据 requestParams 对象计算 Hash 值，这里鱼皮只是简单地调用了对象的 hashCode 方法，大家也可以根据需求实现自己的 Hash 算法。
2. 每次调用负载均衡器时，都会重新构造 Hash 环，这是为了能够即时处理节点的变化。

#### 2、支持配置和扩展

一个成熟的 RPC 框架可能会支持多个负载均衡器，像序列化器和注册中心一样，我们的需求是，让开发者能够填写配置来指定使用的负载均衡器，并且支持自定义负载均衡器，让框架更易用、更利于扩展。

要实现这点，开发方式和序列化器、注册中心都是一样的，都可以使用工厂创建对象、使用 SPI 动态加载自定义的注册中心。

流程和之前的注册中心一样：

1. 负载均衡器常量 LoadBalancerKeys。
2. 使用工厂模式 LoadBalancerFactory，支持根据 key 从 SPI 获取负载均衡器对象实例。
3. 在 META-INF 的 rpc/system 目录下编写负载均衡器接口的 SPI 配置文件，文件名称为 com.han.rpc.loadbalancer.LoadBalancer。
4. 为 RpcConfig 全局配置新增负载均衡器的配置

#### 3、应用负载均衡器

现在，我们就能够愉快地使用负载均衡器了。修改 ServiceProxy 的代码，将 “固定调用第一个服务节点” 改为 “调用负载均衡器获取一个服务节点”。

```java
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
```

上述代码中，我们给负载均衡器传入了一个 requestParams HashMap，并且将请求方法名作为参数放到了 Map 中。如果使用的是一致性 Hash 算法，那么会根据 requestParams 计算 Hash 值，调用相同方法的请求 Hash 值肯定相同，所以总会请求到同一个服务器节点上。



## 九、重试机制

### 需求分析

目前，如果使用 RPC 框架的服务消费者调用接口失败，就会直接报错。

调用接口失败可能有很多原因，有时可能是服务提供者返回了错误，但有时可能只是网络不稳定或服务提供者重启等临时性问题。这种情况下，我们可能更希望服务消费者拥有自动重试的能力，提高系统的可用性。

所以我们需要实现服务消费端的重试机制。

### 设计方案

#### 1、重试机制

我们需要掌握的是 “如何设计重试机制”，重试机制的核心是 重试策略，一般来说，包含以下几个考虑点：

**1、重试条件**

这个比较好思考，如果我们希望提高系统的可用性，当由于网络等异常情况发生时，触发重试。

**2、重试时间**

重试时间（也叫重试等待）的策略就比较丰富了，可能会用到一些算法，主流的重试时间算法有：

1）固定重试间隔（Fixed Retry Interval）：在每次重试之间使用固定的时间间隔。

2）指数退避重试（Exponential Backoff Retry）：在每次失败后，重试的时间间隔会以指数级增加，以避免请求过于密集。

比如近 5 次重试的时间如下：

```
1s 
3s（多等 2s） 
7s（多等 4s） 
15s（多等 8s） 
31s（多等 16s）
```

3）随机延迟重试（Random Delay Retry）：在每次重试之间使用随机的时间间隔，以避免请求的同时发生。

4）可变延迟重试（Variable Delay Retry）：这种策略更 “高级” 了，根据先前重试的成功或失败情况，动态调整下一次重试的延迟时间。比如，根据前一次的响应时间调整下一次重试的等待时间。

值得一提的是，以上的策略是可以组合使用的，一定要根据具体情况和需求灵活调整。比如可以先使用指数退避重试策略，如果连续多次重试失败，则切换到固定重试间隔策略。

**3、停止重试**

一般来说，重试次数是有上限的，否则随着报错的增多，系统同时发生的重试也会越来越多，造成雪崩。

主流的停止重试策略有：

1. 最大尝试次数：一般重试当达到最大次数时不再重试。
2. 超时停止：重试达到最大时间的时候，停止重试。

**4、重试工作**

最后一点是重试后要做什么事情？一般来说就是重复执行原本要做的操作，比如发送请求失败了，那就再发一次请求。需要注意的是，当重试次数超过上限时，往往还要进行其他的操作，比如：

1. 通知告警：让开发者人工介入
2. 降级容错：改为调用其他接口、或者执行其他操作

#### 2、重试方案设计

回归到我们的 RPC 框架，消费者发起调用的代码如下：

```java
try {
    // rpc请求
    RpcResponse rpcResponse = VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo, 5000L, TimeUnit.MILLISECONDS));
    return rpcResponse.getData();
} catch (Exception e) {
    throw new RuntimeException("调用失败");
}
```

我们完全可以将 `VertxTcpClient.doRequest` 封装为一个可重试的任务，如果请求失败（重试条件），系统就会自动按照重试策略再次发起请求，不用开发者关心。

对于重试算法，我们就选择主流的重试算法好了，Java 中可以使用 Guava-Retrying 库轻松实现多种不同的重试算法，非常简单，后文直接实战。推荐一篇使用 Guava-Retrying 的教程文章：https://cloud.tencent.com/developer/article/1752086

和序列化器、注册中心、负载均衡器一样，重试策略本身也可以使用 SPI + 工厂的方式，允许开发者动态配置和扩展自己的重试策略。

最后，如果重试超过一定次数，我们就停止重试，并且抛出异常。之后我们会实现重试失败后的另一种选择 —— 容错机制。

### 开发实现

#### 1、多种重试策略实现

下面实现 2 种最基本的重试策略：不重试、固定重试间隔。

在 RPC 项目中新建 `fault.retry` 包，将所有重试相关的代码放到该包下。

1）先编写重试策略通用接口。提供一个重试方法，接受一个具体的任务参数，可以使用 Callable 类代表一个任务。

```java
/**
 * 重试策略
 */
public interface RetryStrategy {

    /**
     * 重试
     *
     * @param callable
     * @return
     * @throws Exception
     */
    RpcResponse doRetry(Callable<RpcResponse> callable) throws Exception;
}
```

2）引入 Guava-Retrying 重试库，代码如下：

```xml
<dependency>
    <groupId>com.github.rholder</groupId>
    <artifactId>guava-retrying</artifactId>
    <version>2.0.0</version>
</dependency>
```

3）不重试策略实现。

就是直接执行一次任务，代码如下：

```java
/**
 * 不重试 - 重试策略
 */
public class NoRetryStrategy implements RetryStrategy {
    @Override
    public RpcResponse doRetry(Callable<RpcResponse> callable) throws Exception {
        return callable.call();
    }
}
```

4）固定重试间隔策略实现。

使用 Guava-Retrying 提供的 `RetryerBuilder` 能够很方便地指定重试条件、重试等待策略、重试停止策略、重试工作等。

```java
/**
 * 固定时间间隔 - 重试策略
 */
@Slf4j
public class FixedIntervalRetryStrategy implements RetryStrategy {
    @Override
    public RpcResponse doRetry(Callable<RpcResponse> callable) throws Exception {
        // 每3秒重试一次，总共执行3次就不再重试
        Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
                .retryIfExceptionOfType(Exception.class)
                .retryIfRuntimeException()
                .withWaitStrategy(WaitStrategies.fixedWait(3L, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        if (attempt.getAttemptNumber() != 1) {
                            log.info("重试次数 {}", attempt.getAttemptNumber() - 1);
                        }
                    }
                })
                .build();
        return retryer.call(callable);
    }
}
```

上述代码中，重试策略如下：

1. 重试条件：使用 retryIfExceptionOfType 方法指定当出现 Exception 异常时重试。
2. 重试等待策略：使用 withWaitStrategy 方法指定策略，选择 fixedWait 固定时间间隔策略。
3. 重试停止策略：使用 withStopStrategy 方法指定策略，选择 stopAfterAttempt 超过最大重试次数停止。
4. 重试工作：使用 withRetryListener 监听重试，每次重试时，除了再次执行任务外，还能够打印当前的重试次数。

5）可以简单编写一个单元测试，来验证不同的重试策略，这是最好的学习方式。

```java
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
```

#### 2、支持配置和扩展

一个成熟的 RPC 框架可能会支持多种不同的重试策略，像序列化器、注册中心、负载均衡器一样，我们的需求是，让开发者能够填写配置来指定使用的重试策略，并且支持自定义重试策略，让框架更易用、更利于扩展。

要实现这点，开发方式和序列化器、注册中心、负载均衡器都是一样的，都可以使用工厂创建对象、使用 SPI 动态加载自定义的注册中心。

1. 重试策略常量。新建 RetryStrategyKeys 类，列举所有支持的重试策略键名。

2. 使用工厂模式，支持根据 key 从 SPI 获取重试策略对象实例。新建 RetryStrategyFactory 类，代码如下：

   ```java
   /**
    * 重试策略工厂（用于获取重试器对象）
    */
   public class RetryStrategyFactory {
   
       static {
           // 不需要一次性加载所有类，每次getInstance时再动态去加载
           // SpiLoader.load(RetryStrategy.class);
       }
   
       /**
        * 默认重试器
        */
       private static final RetryStrategy DEFAULT_RETRY_STRATEGY = new NoRetryStrategy();
   
       /**
        * 获取实例
        * @param key
        * @return
        */
       public static RetryStrategy getInstance(String key) {
           return SpiLoader.getInstance(RetryStrategy.class, key);
       }
   }
   ```

3. 在 META-INF 的 rpc/system 目录下编写重试策略接口的 SPI 配置文件，文件名称为 com.han.rpc.fault.retry.RetryStrategy

4. 为 RpcConfig 全局配置新增重试策略的配置，代码如下：

   ```java
   /**
     * 重试策略
     */
   private String retryStrategy = RetryStrategyKeys.NO;
   ```

#### 3、应用重试功能

现在，我们就能够愉快地使用重试功能了。修改 ServiceProxy 的代码，从工厂中获取重试器，并且将请求代码封装为一个 Callable 接口，作为重试器的参数，调用重试器即可。

修改后的代码如下：

```java
// 使用重试机制
RetryStrategy retryStrategy = RetryStrategyFactory.getInstance(rpcConfig.getRetryStrategy());
rpcResponse = retryStrategy.doRetry(() ->
                        VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo, 5000L, TimeUnit.MILLISECONDS));
```

上述代码中，使用 Lambda 表达式将 `VertxTcpClient.doRequest` 封装为了一个匿名函数，简化了代码。

我们会发现，即使引入了重试机制，整段代码并没有变得更复杂，这就是可扩展性设计的巧妙之处。

### 测试

首先启动服务提供者，然后使用 Debug 模式启动服务消费者，当服务消费者发起调用时，立刻停止服务提供者，就会看到调用失败后重试的情况。

### 扩展

指数退避算法的重试器

```java
/**
 * 指数退避 - 重试策略
 */
@Slf4j
public class ExponentialBackoffRetryStrategy implements RetryStrategy {
    @Override
    public RpcResponse doRetry(Callable<RpcResponse> callable) throws Exception {
        // 初始重试等待2s，之后每次间隔时间乘2，达到12s后间隔时间保持不变
        Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
            .retryIfExceptionOfType(Exception.class)
            .retryIfRuntimeException()
            .withWaitStrategy(WaitStrategies.exponentialWait(2, 12, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .withRetryListener(new RetryListener() {
                @Override
                public <V> void onRetry(Attempt<V> attempt) {
                    if (attempt.getAttemptNumber() != 1) {
                        log.info("重试次数 {}", attempt.getAttemptNumber() - 1);
                    }
                }
            })
            .build();
        return retryer.call(callable);
    }
}
```



## 十、容错机制

### 需求分析

我们已经给 RPC 框架增加了重试机制，提升了服务消费端的可靠性和健壮性。

但如果重试超过了一定次数仍然失败，我们又该怎么处理呢？或者说当调用出现失败时，我们一定要重试么？有没有其他的策略呢？

这次我们来实现另一种提高服务消费端可靠性和健壮性的机制 —— 容错机制。

### 设计方案

#### 1、容错机制

容错是指系统在出现异常情况时，可以通过一定的策略保证系统仍然稳定运行，从而提高系统的可靠性和健壮性。

在分布式系统中，容错机制尤为重要，因为分布式系统中的各个组件都可能存在网络故障、节点故障等各种异常情况。要顾全大局，尽可能消除偶发 / 单点故障对系统带来的整体影响。

**容错策略**

容错策略有很多种，常用的容错策略主要是以下几个：

1. Fail-Over 故障转移：一次调用失败后，切换一个其他节点再次进行调用，也算是一种重试。
2. Fail-Back 失败自动恢复：系统的某个功能出现调用失败或错误时，通过其他的方法，恢复该功能的正常。可以理解为降级，比如重试、调用其他服务等。
3. Fail-Safe 静默处理：系统出现部分非重要功能的异常时，直接忽略掉，不做任何处理，就像错误没有发生过一样。
4. Fail-Fast 快速失败：系统出现调用错误时，立刻报错，交给外层调用方处理。

**容错实现方式**

容错其实是个比较广泛的概念，除了上面几种策略外，很多技术都可以起到容错的作用。

1. 重试：重试本质上也是一种容错的降级策略，系统错误后再试一次。
2. 限流：当系统压力过大、已经出现部分错误时，通过限制执行操作（接受请求）的频率或数量，对系统进行保护。
3. 降级：系统出现错误后，改为执行其他更稳定可用的操作。也可以叫做 “兜底” 或 “有损服务”，这种方式的本质是：即使牺牲一定的服务质量，也要保证系统的部分功能可用，保证基本的功能需求得到满足。
4. 熔断：系统出现故障或异常时，暂时中断对该服务的请求，而是执行其他操作，以避免连锁故障。
5. 超时控制：如果请求或操作长时间没处理完成，就进行中断，防止阻塞和资源占用。

注意，在实际项目中，根据对系统可靠性的需求，我们通常会结合多种策略或方法实现容错机制。

#### 2、容错方案设计

回归到我们的 RPC 框架，之前已经给系统增加重试机制了，算是实现了一部分的容错能力。现在，我们可以正式引入容错机制，通过更多策略来进一步增加系统可靠性。

系统错误时，先通过重试操作解决一些临时性的异常，比如网络波动、服务端临时不可用等；如果重试多次后仍然失败，说明可能存在更严重的问题，这时可以触发其他的容错策略，比如调用降级服务、熔断、限流、快速失败等，来减少异常的影响，保障系统的稳定性和可靠性。

### 开发实现

#### 1、多种容错策略实现

下面实现 2 种最基本的容错策略：Fail-Fast 快速失败、Fail-Safe 静默处理。

在 RPC 项目中新建 fault.tolerant 包，将所有容错相关的代码放到该包下。

1）先编写容错策略通用接口。提供一个容错方法，使用 Map 类型的参数接受上下文信息（可用于灵活地传递容错处理需要用到的数据），并且接受一个具体的异常类参数。

由于容错是应用到发送请求操作的，所以容错方法的返回值是 RpcResponse（响应）。

```java
/**
 * 容错策略
 */
public interface TolerantStrategy {

    /**
     * 容错
     * @param context 上下文，用于传递信息
     * @param e 异常
     * @return
     */
    RpcResponse doTolerant(Map<String, Object> context, Exception e);
}
```

2）快速失败容错策略实现。

很好理解，就是遇到异常后，将异常再次抛出，交给外层处理。

```java
/**
 * 快速失败 - 容错策略（立刻通知外层调用方）
 */
public class FailFastTolerantStrategy implements TolerantStrategy {
    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        throw new RuntimeException("服务报错", e);
    }
}
```

3）静默处理容错策略实现。

也很好理解，就是遇到异常后，记录一条日志，然后正常返回一个响应对象，就好像没有出现过报错。

```java
/**
 * 静默处理 - 容错策略
 */
@Slf4j
public class FailSafeTolerantStrategy implements TolerantStrategy {
    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        log.info("静默处理异常", e);
        return new RpcResponse();
    }
}
```

4）其他容错策略。

还可以自行实现更多的容错策略，比如 FailBackTolerantStrategy 故障恢复策略，获取降级的服务并调用；还有 FailOverTolerantStrategy 故障转移策略，调用其他可用的服务节点

#### 2、支持配置和扩展

一个成熟的 RPC 框架可能会支持多种不同的容错策略，像序列化器、注册中心、负载均衡器一样，我们的需求是，让开发者能够填写配置来指定使用的容错策略，并且支持自定义容错策略，让框架更易用、更利于扩展。

要实现这点，开发方式和序列化器、注册中心、负载均衡器都是一样的，都可以使用工厂创建对象、使用 SPI 动态加载自定义的注册中心。

步骤和前面重试机制一模一样，参考前面的实现

#### 3、应用容错能力

容错功能的应用非常简单，我们只需要修改 ServiceProxy 的部分代码，在重试多次抛出异常时，从工厂中获取容错策略并执行即可。

修改的代码如下：

```java
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
```

我们会发现，即使引入了容错机制，整段代码并没有变得更复杂，这就是可扩展性设计的巧妙之处。

### 测试

首先启动服务提供者，然后使用 Debug 模式启动服务消费者，当服务消费者发起调用时，立刻停止服务提供者，就会看到调用失败后重试的情况。等待多次重试后，就可以看到容错策略的执行。

### 扩展

暂未实现

1）实现 Fail-Back 容错机制。

参考思路：可以参考 Dubbo 的 Mock 能力，让消费端指定调用失败后要执行的本地服务和方法。

2）实现 Fail-Over 容错机制。

参考思路：可以利用容错方法的上下文参数传递所有的服务节点和本次调用的服务节点，选择一个其他节点再次发起调用。



## 十一、启动机制和注解驱动

### 需求分析

通过前面的教程，我们 RPC 框架的功能已经比较完善了，接下来我们就要思考如何优化这个框架。框架是给开发者用的，让我们换位思考：如果你是一名开发者，会选择怎样的一款框架呢？

答案很简单，就是选择符合自身需求的呗！

1. 框架的知名度和用户数：尽量选主流的、用户多的，经过了充分的市场验证。
2. 生态和社区活跃度：尽量选社区活跃的、能和其他技术兼容的。
3. 简单易用易上手：最好能开箱即用，不用花很多时间去上手。这点可能是我们在做个人小型项目时最关注的，可以把精力聚焦到业务开发上。

回归到我们的 RPC 项目，其实框架目前是不够易用的。还记得么？光是我们的示例服务提供者，就要写下面这段又臭又长的代码！

```java
/**
 * 服务提供者示例
 */
public class ProviderExample {
    public static void main(String[] args) {
        // RPC框架初始化
        RpcApplication.init();
        // 注册服务
        LocalRegister.register(UserService.class.getName(), UserServiceImpl.class);

        // 注册服务到注册中心
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName(UserService.class.getName());
        serviceMetaInfo.setServiceVersion("1.0");
        serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
        serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
        try {
            registry.register(serviceMetaInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 启动服务器
        VertxTcpServer tcpServer = new VertxTcpServer();
        tcpServer.doStart(RpcApplication.getRpcConfig().getServerPort());
    }
}
```

所以我们需要来优化框架的易用性，通过建立合适的启动机制和注解驱动机制，帮助开发者最少只用一行代码，就能轻松使用框架！

### 设计方案

让我们先来站在上帝视角，思考一下：怎么能让开发者用更少的代码启动框架？

#### 启动机制设计

其实很简单，把所有启动代码封装成一个 专门的启动类 或方法，然后由服务提供者 / 服务消费者调用即可。

但有一点我们需要注意，服务提供者和服务消费者需要初始化的模块是不同的，比如服务消费者不需要启动 Web 服务器。

所以我们需要针对服务提供者和消费者分别编写一个启动类，如果是二者都需要初始化的模块，可以放到全局应用类 RpcApplication 中，复用代码的同时保证启动类的可维护、可扩展性。

在 Dubbo 中，就有类似的设计，参考文档：https://cn.dubbo.apache.org/zh-cn/overview/mannual/java-sdk/quick-start/api/ 。

#### 注解驱动设计

除了启动类外，其实还有一种更牛的方法，能帮助开发者使用框架。还记得Dubbo 中是如何让开发者快速使用框架的呢？

它的做法是 **注解驱动**，开发者只需要在服务提供者实现类打上一个 DubboService 注解，就能快速注册服务；同样的，只要在服务消费者字段打上一个 DubboReference 注解，就能快速使用服务。

由于现在的 Java 项目基本都使用 Spring Boot 框架，所以 Dubbo 还贴心地推出了 Spring Boot Starter。那我们也可以有样学样，创建一个 Spring Boot Starter 项目，并通过注解驱动框架的初始化，完成服务注册和获取引用。

关于 Spring Boot Starter 的开发，可以参考：https://github.com/Wuhuyoung/api-backend

实现注解驱动并不复杂，有 2 种常用的方式：

1. 主动扫描：让开发者指定要扫描的路径，然后遍历所有的类文件，针对有注解的类文件，执行自定义的操作。
2. 监听 Bean 加载：在 Spring 项目中，可以通过实现 BeanPostProcessor 接口，在 Bean 初始化后执行自定义的操作。

有了思路后，下面我们依次开发实现启动机制和注解驱动。

### 开发实现

#### 1、启动机制

我们在 rpc 项目中新建包名 bootstrap，所有和框架启动初始化相关的代码都放到该包下。

**服务提供者启动类**

在注册服务时，我们需要填入多个字段，比如服务名称、服务实现类，参考代码如下：

```java
// 注册服务
String serviceName = UserService.class.getName();
LocalRegistry.register(serviceName, UserServiceImpl.class);
```

我们可以将这些字段进行封装，在 model 包下新建 ServiceRegisterInfo 类，代码如下：

```java
/**
 * 服务注册信息类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceRegisterInfo<T> {
    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 实现类
     */
    private Class<? extends T> implClass;
}
```

这样一来，服务提供者的初始化方法只需要接受封装的注册信息列表作为参数即可，简化了方法：

```java
/**
 * 服务提供者初始化
 */
public class ProviderBootstrap {
    /**
     * 初始化
     * @param serviceMetaInfoList
     */
    public static void init(List<ServiceRegisterInfo<?>> serviceMetaInfoList) {
        // RPC框架初始化（配置和注册中心）
        RpcApplication.init();

        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());

        // 注册服务
        for (ServiceRegisterInfo<?> serviceRegisterInfo : serviceMetaInfoList) {
            // 本地注册
            LocalRegistry.register(serviceRegisterInfo.getServiceName(), serviceRegisterInfo.getImplClass());

            // 注册服务到注册中心
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceRegisterInfo.getServiceName());
            serviceMetaInfo.setServiceVersion("1.0");
            serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
            serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
            try {
                registry.register(serviceMetaInfo);
            } catch (Exception e) {
                throw new RuntimeException(serviceRegisterInfo.getServiceName() + "注册失败", e);
            }
        }

        // 启动服务器
        VertxTcpServer tcpServer = new VertxTcpServer();
        tcpServer.doStart(RpcApplication.getRpcConfig().getServerPort());
    }
}
```

现在，我们想要在服务提供者项目中使用 RPC 框架，就非常简单了。只需要定义要注册的服务列表，然后一行代码调用 ProviderBootstrap.init 方法即可完成初始化。

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

**服务消费者启动类**

服务消费者启动类的实现就更简单了，因为它不需要注册服务、也不需要启动 Web 服务器，只需要执行 RpcApplication.init 完成框架的通用初始化即可。

```java
/**
 * 服务消费者初始化
 */
public class ConsumerBootstrap {
    /**
     * 初始化
     */
    public static void init() {
        // RPC框架初始化（配置和注册中心）
        RpcApplication.init();
    }
}
```

服务消费者示例项目的代码不会有明显的变化，只不过改为调用启动类了。

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

        User newUser = userService.getUser(user);
        if (newUser != null) {
            System.out.println("newUser:" + newUser.getName());
        } else {
            System.out.println("user 为 null");
        }
    }
}
```

#### 2、Spring Boot Starter 注解驱动

为了不要和已有项目的代码混淆，我们再来创建一个新的项目模块，专门用于实现 Spring Boot Starter 注解驱动的 RPC 框架。Dubbo 是在框架内引入了 spring-context，会让整个框架更内聚，但是不利于理解。

**1、Spring Boot Starter 项目初始化**

创建新模块 my-rpc-spring-boot-starter，添加依赖 Spring Configuration Processor 和我们开发的 RPC 框架 my-rpc-core，并移除无用的插件代码（plugin）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.0</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>

    <groupId>com.han.rpc</groupId>
    <artifactId>my-rpc-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <java.version>11</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>com.han.rpc</groupId>
            <artifactId>my-rpc-core</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.24</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

</project>
```

**2、定义注解**

实现注解驱动的第一步是定义注解，要定义哪些注解呢？

还是那句话，有样学样，可以参考知名框架 Dubbo 的注解。比如：

1. @EnableDubbo：在 Spring Boot 主应用类上使用，用于启用 Dubbo 功能。
2. @DubboComponentScan：在 Spring Boot 主应用类上使用，用于指定 Dubbo 组件扫描的包路径。
3. @DubboReference：在消费者中使用，用于声明 Dubbo 服务引用。
4. @DubboService：在提供者中使用，用于声明 Dubbo 服务。
5. @DubboMethod：在提供者和消费者中使用，用于配置 Dubbo 方法的参数、超时时间等。
6. @DubboTransported：在 Dubbo 提供者和消费者中使用，用于指定传输协议和参数，例如传输协议的类型、端口等。

当然，这些注解我们不需要全部用到，我们只需要定义 3 个注解：

@EnableRpc
@RpcReference
@RpcService

在 my-rpc-spring-boot-starter 项目下新建 annotation 包，将所有注解代码放到该包下。

1）@EnableRpc：用于全局标识项目需要引入 RPC 框架、执行初始化方法。

由于服务消费者和服务提供者初始化的模块不同，我们需要在 EnableRpc 注解中，指定是否需要启动服务器等属性。

```java
/**
 * 启用RPC注解
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableRpc {
    /**
     * 需要启动 server
     * @return
     */
    boolean needServer() default true;
}
```

当然，也可以将 EnableRpc 注解拆分为两个注解（比如 EnableRpcProvider、EnableRpcConsumer），分别用于标识服务提供者和消费者，但可能存在模块重复初始化的可能性。

2）@RpcService：服务提供者注解，在需要注册和提供的服务类上使用。

RpcService 注解中，需要指定服务注册信息属性，比如服务接口实现类、版本号等（也可以包括服务名称）。

```java
/**
 * 服务提供者注解，用于注册服务
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface RpcService {

    /**
     * 服务接口类
     * @return
     */
    Class<?> interfaceClass() default void.class;

    /**
     * 版本
     * @return
     */
    String serviceVersion() default RpcConstant.DEFAULT_SERVICE_VERSION;
}
```

3）@RpcReference：服务消费者注解，在需要注入服务代理对象的属性上使用，类似 Spring 中的 @Resource 注解。

RpcReference 注解中，需要指定调用服务相关的属性，比如服务接口类（可能存在多个接口）、版本号、负载均衡器、重试策略、是否 Mock 模拟调用等。

```java
/**
 * 服务消费者注解，用于注入服务
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcReference {
    /**
     * 服务接口类
     * @return
     */
    Class<?> interfaceClass() default void.class;

    /**
     * 版本
     * @return
     */
    String serviceVersion() default RpcConstant.DEFAULT_SERVICE_VERSION;

    /**
     * 负载均衡器
     * @return
     */
    String loadBalancer() default LoadBalancerKeys.ROUND_ROBIN;

    /**
     * 重试策略
     * @return
     */
    String retryStrategy() default RetryStrategyKeys.NO;

    /**
     * 容错策略
     * @return
     */
    String tolerantStrategy() default TolerantStrategyKeys.FAIL_FAST;

    /**
     * 模拟调用
     * @return
     */
    boolean mock() default false;
}
```

**3、注解驱动**

在 starter 项目中新建 bootstrap 包，并且分别针对上面定义的 3 个注解新建启动类。<img src="https://typora-1314662469.cos.ap-shanghai.myqcloud.com/img/202404151737478.png" alt="image-20240415171616500" style="zoom:80%;" />

1）Rpc 框架全局启动类 RpcInitBootstrap。

我们的需求是，在 Spring 框架初始化时，获取 @EnableRpc 注解的属性，并初始化 RPC 框架。

怎么获取到注解的属性呢？

可以实现 Spring 的 ImportBeanDefinitionRegistrar 接口，并且在 registerBeanDefinitions 方法中，获取到项目的注解和注解属性。参考：[Spring Boot中@Import三种使用方式！](https://zhuanlan.zhihu.com/p/573604546)

```java
/**
 * RPC 框架启动
 * 获取 @EnableRpc 注解的属性，初始化 RPC 框架
 */
@Slf4j
public class RpcInitBootstrap implements ImportBeanDefinitionRegistrar {

    /**
     * Spring初始化时执行
     * @param importingClassMetadata
     * @param registry
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 获取 EnableRpc 注解属性
        boolean needServer = (boolean) importingClassMetadata.getAnnotationAttributes(EnableRpc.class.getName())
                .get("needServer");

        // RPC框架初始化（配置和注册中心）
        RpcApplication.init();
        // 全局配置
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();

        if (needServer) {
            // 启动服务器
            VertxTcpServer tcpServer = new VertxTcpServer();
            tcpServer.doStart(RpcApplication.getRpcConfig().getServerPort());
        } else {
            log.info("不启动server");
        }
    }
}
```

上述代码中，我们从 Spring 元信息中获取到了 EnableRpc 注解的 needServer 属性，并通过它来判断是否要启动服务器。

2）Rpc 服务提供者启动类 RpcProviderBootstrap

服务提供者启动类的作用是，获取到所有包含 @RpcService 注解的类，并且通过注解的属性和反射机制，获取到要注册的服务信息，并且完成服务注册。

怎么获取到所有包含 @RpcService 注解的类呢？

像前面设计方案中提到的，可以主动扫描包，也可以利用 Spring 的特性监听 Bean 的加载。此处我们选择后者，实现更简单，而且能直接获取到服务提供者类的 Bean 对象。

只需要让启动类实现 BeanPostProcessor 接口的 postProcessAfterInitialization 方法，就可以在某个服务提供者 Bean 初始化后，执行注册服务等操作了。参考：[一文读懂 Spring Bean 的生命周期](https://blog.csdn.net/riemann_/article/details/118500805)

```java
/**
 * Rpc服务提供者启动
 */
public class RpcProviderBootstrap implements BeanPostProcessor {

    /**
     * bean初始化后执行，获取到所有包含@RpcService的类，注册服务
     *
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        RpcService rpcService = beanClass.getAnnotation(RpcService.class);

        if (rpcService != null) {
            // 需要注册服务
            // 1.获取服务信息
            Class<?> interfaceClass = rpcService.interfaceClass();
            //   默认值处理
            if (interfaceClass == void.class) {
                interfaceClass = beanClass.getInterfaces()[0];
            }
            String serviceVersion = rpcService.serviceVersion();
            String serviceName = interfaceClass.getName();

            // 2.注册服务
            //   本地注册
            LocalRegistry.register(serviceName, beanClass);

            //   注册到注册中心
            RpcConfig rpcConfig = RpcApplication.getRpcConfig();
            Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceName);
            serviceMetaInfo.setServiceVersion(serviceVersion);
            serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
            serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
            try {
                registry.register(serviceMetaInfo);
            } catch (Exception e) {
                throw new RuntimeException(serviceName + "注册失败", e);
            }
        }

        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
```

3）Rpc 服务消费者启动类 RpcConsumerBootstrap

和服务提供者启动类的实现方式类似，在 Bean 初始化后，通过反射获取到 Bean 的所有属性，如果属性包含 @RpcReference 注解，那么就为该属性动态生成代理对象并赋值。

```java
/**
 * Rpc服务消费者启动
 */
public class RpcConsumerBootstrap implements BeanPostProcessor {
    /**
     * bean初始化后执行，注入服务
     *
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        // 反射获取对象所有属性
        Field[] fields = beanClass.getDeclaredFields();
        for (Field field : fields) {
            RpcReference rpcReference = field.getAnnotation(RpcReference.class);
            if (rpcReference != null) {
                // 为属性生成代理对象
                Class<?> interfaceClass = rpcReference.interfaceClass();
                if (interfaceClass == void.class) {
                    interfaceClass = field.getType();
                }
                Object proxy = ServiceProxyFactory.getProxy(interfaceClass);
                try {
                    field.setAccessible(true);
                    field.set(bean, proxy);
                    field.setAccessible(false);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("为字段注入代理对象失败", e);
                }
            }
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
```

4）注册已编写的启动类。

最后，别忘了在 Spring 中加载我们已经编写好的启动类。

如何加载呢？

我们的需求是，仅在用户使用 @EnableRpc 注解时，才启动 RPC 框架。所以，可以通过给 EnableRpc 增加 @Import 注解，来注册我们自定义的启动类，实现灵活的可选加载。

参考：[Spring Boot中@Import三种使用方式！](https://zhuanlan.zhihu.com/p/573604546)

修改后的 EnableRpc 注解代码如下：

```java
/**
 * 启用RPC注解
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import({RpcInitBootstrap.class, RpcProviderBootstrap.class, RpcConsumerBootstrap.class})
public @interface EnableRpc {
    /**
     * 需要启动 server
     * @return
     */
    boolean needServer() default true;
}
```

至此，一个基于注解驱动的 RPC 框架 Starter 开发完成。

### 测试

1. 新建两个使用 Spring Boot 2 框架的项目，都引入依赖 my-rpc-spring-boot-starter 和 common

2. 示例服务提供者项目的入口类加上 @EnableRpc 注解

3. 服务提供者提供一个简单的服务，代码如下：

   ```java
   @Service
   @RpcService
   public class UserServiceImpl implements UserService {
       @Override
       public User getUser(User user) {
           System.out.println("用户名：" + user.getName());
           return user;
       }
   }
   ```

4. 示例服务消费者的入口类加上 @EnableRpc(needServer = false) 注解，标识启动 RPC 框架，但不启动服务器。

5. 消费者编写一个 Spring 的 Bean，引入 UserService 属性并打上 @RpcReference

   ```java
   @Service
   public class ConsumerExample {
   
       @RpcReference
       private UserService userService;
   
       public void testUserService() {
           User user = new User();
           user.setName("张三");
           User serviceUser = userService.getUser(user);
           if (serviceUser != null) {
               System.out.println("获取到用户:" + serviceUser);
           } else {
               System.out.println("调用服务出错");
           }
       }
   }
   ```

6. 服务消费者编写单元测试，验证能否调用远程服务：

   ```java
   @SpringBootTest
   class SpringbootConsumerApplicationTests {
   
       @Resource
       private ConsumerExample consumerExample;
   
       @Test
       void testUserService() {
           consumerExample.testUserService();
       }
   }
   ```

7. 分别启动提供者、消费者的入口类，然后执行服务消费者的单元测试，可以看到调用成功

