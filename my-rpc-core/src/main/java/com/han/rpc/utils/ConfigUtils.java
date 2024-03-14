package com.han.rpc.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.Setting;
import cn.hutool.setting.dialect.Props;
import cn.hutool.setting.yaml.YamlUtil;
import com.han.rpc.config.RpcConfig;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

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
}
