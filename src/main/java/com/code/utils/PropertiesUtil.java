package com.code.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 配置文件读取工具类（线程安全、每次独立加载、UTF-8 编码、自动关闭流）
 */
public class PropertiesUtil extends Properties {

    private PropertiesUtil() {
    }

    public static PropertiesUtil getMySqlProperties(String path) throws Exception {
        return loadProperties(path + "src/main/resources/templates/config/mysql.properties");
    }

    public static PropertiesUtil getRedisProperties(String path) throws Exception {
        return loadProperties(path + "src/main/resources/templates/config/redis.properties");
    }

    private static PropertiesUtil loadProperties(String filePath) throws Exception {
        PropertiesUtil props = new PropertiesUtil();
        try (BufferedReader reader = getInputStreamReader(filePath)) {
            props.load(reader);
        }
        return props;
    }

    public static BufferedReader getInputStreamReader(String path) throws FileNotFoundException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
    }
}
