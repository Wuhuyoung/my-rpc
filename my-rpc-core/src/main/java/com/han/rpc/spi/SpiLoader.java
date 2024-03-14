package com.han.rpc.spi;

import cn.hutool.core.io.resource.ResourceUtil;
import com.han.rpc.serializer.Serializer;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
