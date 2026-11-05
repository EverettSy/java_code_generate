package com.code.utils;

import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.Jedis;

import java.util.*;

public class RedisUtils {

    /**
     * 默认连接与读取超时时间（毫秒），防止 Redis 不可达时长期阻塞任务
     */
    private static final int DEFAULT_TIMEOUT_MS = 2000;

    /**
     * Jedis实例获取返回码
     *
     * @author syrobin
     */
    public static class JedisStatus {
        /**
         * Jedis实例获取失败
         */
        public static final long FAIL_LONG = -5L;
        /**
         * Jedis实例获取失败
         */
        public static final int FAIL_INT = -5;
        /**
         * Jedis实例获取失败
         */
        public static final String FAIL_STRING = "-5";
    }

    /**
     * 同步获取Jedis实例（带超时与空密码兼容，异常时返回 null 降级）
     *
     * @return Jedis
     */
    public static Jedis getJedis(String path) {
        try {
            PropertiesUtil propertiesUtil = PropertiesUtil.getRedisProperties(path);
            String host = propertiesUtil.getProperty("host");
            String portStr = propertiesUtil.getProperty("port");
            if (StringUtils.isAnyBlank(host, portStr)) {
                return null;
            }
            int port = Integer.parseInt(portStr.trim());
            Jedis jedis = new Jedis(host.trim(), port, DEFAULT_TIMEOUT_MS);
            String password = propertiesUtil.getProperty("password");
            if (StringUtils.isNotBlank(password)) {
                jedis.auth(password.trim());
            }
            return jedis;
        } catch (Exception e) {
            System.err.println("获取 Jedis 实例失败（已降级为本地内存模式）：" + e.getMessage());
            return null;
        }
    }

    /**
     * 释放jedis资源
     *
     * @param jedis
     */
    public static void returnResource(final Jedis jedis) {
        if (jedis != null) {
            try {
                jedis.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static String set(Jedis jedis, String key, String value) {
        if (jedis == null) {
            return JedisStatus.FAIL_STRING;
        }

        String result = null;
        try {
            result = jedis.set(key, value);
        } catch (Exception e) {
            System.err.println("设置值失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static String set(Jedis jedis, String key, String value, int expire) {
        if (jedis == null) {
            return JedisStatus.FAIL_STRING;
        }

        String result = null;
        try {
            result = jedis.set(key, value);
            jedis.expire(key, expire);
        } catch (Exception e) {
            System.err.println("设置值失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static String get(Jedis jedis, String key) {
        if (jedis == null) {
            return JedisStatus.FAIL_STRING;
        }

        String result = null;
        try {
            result = jedis.get(key);
        } catch (Exception e) {
            System.err.println("获取值失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static long expire(Jedis jedis, String key, int seconds) {
        if (jedis == null) {
            return JedisStatus.FAIL_LONG;
        }

        long result = JedisStatus.FAIL_LONG;
        try {
            Long res = jedis.expire(key, seconds);
            if (res != null) {
                result = res;
            }
        } catch (Exception e) {
            System.err.println(String.format("设置key=%s的过期时间失败：%s", key, e.getMessage()));
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static boolean exists(Jedis jedis, String key) {
        if (jedis == null) {
            return false;
        }

        boolean result = false;
        try {
            Boolean res = jedis.exists(key);
            result = Boolean.TRUE.equals(res);
        } catch (Exception e) {
            System.err.println(String.format("判断key=%s是否存在失败：%s", key, e.getMessage()));
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static long del(Jedis jedis, String... keys) {
        if (jedis == null) {
            return JedisStatus.FAIL_LONG;
        }

        long result = JedisStatus.FAIL_LONG;
        try {
            result = jedis.del(keys);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static long setnx(Jedis jedis, String key, String value) {
        long result = JedisStatus.FAIL_LONG;

        if (jedis == null) {
            return result;
        }

        try {
            result = jedis.setnx(key, value);
        } catch (Exception e) {
            System.err.println("设置值失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static long setnx(Jedis jedis, String key, String value, int expire) {
        long result = JedisStatus.FAIL_LONG;

        if (jedis == null) {
            return result;
        }

        try {
            result = jedis.setnx(key, value);
            jedis.expire(key, expire);
        } catch (Exception e) {
            System.err.println("设置值失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static long lpush(Jedis jedis, String key, String... values) {
        long result = JedisStatus.FAIL_LONG;

        if (jedis == null) {
            return result;
        }

        try {
            result = jedis.lpush(key, values);
        } catch (Exception e) {
            System.err.println("在列表key的头部插入元素失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static long rpush(Jedis jedis, String key, String... values) {
        long result = JedisStatus.FAIL_LONG;

        if (jedis == null) {
            return result;
        }

        try {
            result = jedis.rpush(key, values);
        } catch (Exception e) {
            System.err.println("在列表key的尾部插入元素失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static List<String> lrange(Jedis jedis, String key, long start, long end) {
        if (jedis == null) {
            return null;
        }

        List<String> result = null;
        try {
            result = jedis.lrange(key, start, end);
        } catch (Exception e) {
            System.err.println("查询列表元素失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static long llen(Jedis jedis, String key) {
        if (jedis == null) {
            return JedisStatus.FAIL_LONG;
        }

        long result = 0;
        try {
            result = jedis.llen(key);
        } catch (Exception e) {
            System.err.println("获取列表长度失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static long lrem(Jedis jedis, String key, long count, String value) {
        if (jedis == null) {
            return JedisStatus.FAIL_LONG;
        }

        long result = 0;
        try {
            result = jedis.lrem(key, count, value);
        } catch (Exception e) {
            System.err.println("移除列表元素失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static String ltrim(Jedis jedis, String key, long start, long end) {
        if (jedis == null) {
            return JedisStatus.FAIL_STRING;
        }

        String result = "";
        try {
            result = jedis.ltrim(key, start, end);
        } catch (Exception e) {
            System.err.println("修剪列表失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static long hset(Jedis jedis, String key, String field, String value) {
        if (jedis == null) {
            return JedisStatus.FAIL_LONG;
        }

        long result = 0L;
        try {
            result = jedis.hset(key, field, value);
        } catch (Exception e) {
            System.err.println("缓存Map赋值失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static String hget(Jedis jedis, String key, String field) {
        if (jedis == null) {
            return null;
        }

        String result = null;
        try {
            result = jedis.hget(key, field);
        } catch (Exception e) {
            System.err.println("获取缓存的Map值失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return result;
    }

    public static Map<String, String> hgetAll(Jedis jedis, String key) {
        Map<String, String> map = new HashMap<>();

        if (jedis == null) {
            return map;
        }

        try {
            map = jedis.hgetAll(key);
        } catch (Exception e) {
            System.err.println("获取map所有的字段和值失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return map;
    }

    public static Boolean hexists(Jedis jedis, String key, String field) {
        if (jedis == null) {
            return null;
        }

        try {
            return jedis.hexists(key, field);
        } catch (Exception e) {
            System.err.println("查看哈希表field字段是否存在失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return null;
    }

    public static Set<String> hkeys(Jedis jedis, String key) {
        Set<String> set = new HashSet<>();
        if (jedis == null) {
            return set;
        }

        try {
            return jedis.hkeys(key);
        } catch (Exception e) {
            System.err.println("获取所有哈希表中的字段失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return null;
    }

    public static List<String> hvals(Jedis jedis, String key) {
        List<String> list = new ArrayList<>();
        if (jedis == null) {
            return list;
        }

        try {
            return jedis.hvals(key);
        } catch (Exception e) {
            System.err.println("获取所有哈希表中的值失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return null;
    }

    public static long hdel(Jedis jedis, String key, String... fields) {
        if (jedis == null) {
            return JedisStatus.FAIL_LONG;
        }

        try {
            return jedis.hdel(key, fields);
        } catch (Exception e) {
            System.err.println("map删除指定的field失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return 0;
    }

    public static Set<String> keys(Jedis jedis, String pattern) {
        Set<String> keyList = new HashSet<>();
        if (jedis == null) {
            return keyList;
        }

        try {
            keyList = jedis.keys(pattern);
        } catch (Exception e) {
            System.err.println("操作keys失败：" + e.getMessage());
        } finally {
            returnResource(jedis);
        }

        return keyList;
    }
}
