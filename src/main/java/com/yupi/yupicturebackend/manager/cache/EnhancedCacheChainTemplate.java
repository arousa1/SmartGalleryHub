package com.yupi.yupicturebackend.manager.cache;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 增强版多级缓存模板：
 * 1. 布隆过滤器防止缓存穿透
 * 2. 空值缓存（NULL_OBJECT）避免回源轰炸
 * 3. 延迟双删 + 删除重试，保证最终一致性
 *
 * @author  arousal
 */
@Slf4j
public abstract class EnhancedCacheChainTemplate {

    /* ========================= 常量 ========================= */

    /** 空值缓存的 JSON 占位字符串，业务不可能出现的值 */
    protected static final String NULL_OBJECT = "\"__NULL__\"";

    /** 延迟双删队列在 Redis 中的 zset key */
    private static final String DELAY_QUEUE_KEY = "cache:delay:delete";

    /** 单线程调度池，用于扫描延迟队列并执行第二次删除 */
    private static final ScheduledExecutorService DELAY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "delay-delete"));

    /* ========================= 链路字段 ========================= */

    /** 下游缓存节点，null 表示已到链尾 */
    @Setter
    protected EnhancedCacheChainTemplate next;

    /** 注入模板，供子类直接操作 Redis */
    @Resource
    protected StringRedisTemplate stringRedisTemplate;

    /* ======================== 抽象方法 ======================== */

    /**
     * 从当前节点获取字符串值
     *
     * @param key 缓存键
     * @return 值，若不存在返回 null
     */
    public abstract String getStringValue(String key);

    /**
     * 向当前节点写入字符串值，并指定过期时间
     *
     * @param key         缓存键
     * @param value       缓存值
     * @param expireSeconds 过期秒数
     */
    public abstract void setStringValue(String key, String value, long expireSeconds);

    /**
     * 获取当前节点持有的所有 key（用于运维巡检）
     *
     * @return key 集合，不会返回 null
     */
    public abstract Set<String> getKeys();

    /**
     * 从当前节点删除单个 key
     *
     * @param key 缓存键
     */
    public abstract void deleteValue(String key);

    /**
     * 从当前节点批量删除 key
     *
     * @param keys 缓存键列表
     */
    public abstract void deleteValues(List<String> keys);

    /**
     * 返回当前节点类型，用于构造布隆过滤器 key
     *
     * @return 如 "local"、"redis"
     */
    protected abstract String cacheType();

    /* ======================== 模板方法 ======================== */

    /**
     * 链式获取字符串值：
     * 1. 布隆过滤器拦截非法 key
     * 2. 当前节点命中（含空值）直接返回
     * 3. 下游节点命中后回填当前节点并返回
     * 4. 全链路未命中则写入空值缓存并返回 null
     *
     * @param key 缓存键
     * @return 值，全链路未命中返回 null
     */
    public final String getStringValueChain(String key) {
        // 布隆过滤器不存在则直接返回 null，防止缓存穿透
        if (!mightContain(key)) {
            return null;
        }

        // 当前节点命中
        String val = getStringValue(key);
        if (val != null) {
            // 如果是空值占位，也返回 null
            if (NULL_OBJECT.equals(val)) {
                return null;
            }
            return val;
        }

        // 下游继续查
        if (next != null) {
            String down = next.getStringValueChain(key);
            if (down != null) {
                // 回填当前节点，过期时间抖动 300~600s
                setStringValue(key, down, randomExpire());
                return down;
            }
        }

        // 全链路未命中 -> 写空值缓存，防止回源轰炸
        setStringValue(key, NULL_OBJECT, 30);
        return null;
    }

    /**
     * 链式写入：
     * 1. 写入布隆过滤器
     * 2. 当前节点写入
     * 3. 递归写入下游
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public final void setStringValueChain(String key, String value) {
        putBloom(key); // 保证布隆过滤器先存在
        setStringValue(key, value, randomExpire());
        if (next != null) {
            next.setStringValueChain(key, value);
        }
    }

    /**
     * 链式删除单个 key：
     * 1. 同步删除（带重试）
     * 2. 下游递归删除
     * 3. 向延迟队列投递“二次删除”任务
     *
     * @param key 缓存键
     */
    public final void deleteValueChain(String key) {
        safeDelete(key); // 第一次删除
        if (next != null) {
            next.deleteValueChain(key);
        }
        // 延迟双删：5 秒后再次删除，防止并发写脏
        long delayMillis = System.currentTimeMillis() + 5_000;
        stringRedisTemplate.opsForZSet().add(DELAY_QUEUE_KEY, key, delayMillis);
    }

    /**
     * 链式批量删除，内部循环调用 {@link #deleteValueChain(String)}
     *
     * @param keys 缓存键列表
     */
    public final void deleteValuesChain(List<String> keys) {
        keys.forEach(this::deleteValueChain);
    }

    /**
     * 链式获取所有 key 的并集（含下游）
     *
     * @return 全链路 key 集合
     */
    public final Set<String> getKeysChain() {
        Set<String> keys = getKeys();
        if (next != null) {
            keys.addAll(next.getKeysChain());
        }
        return keys;
    }

    /**
     * 工具方法：把 JSON 字符串反序列成对象，自动识别空值占位
     *
     * @param key  缓存键
     * @param type 类型引用
     * @param <T>  目标类型
     * @return 反序列化对象，若命中空值占位则返回 null
     */
    public <T> T getObjectValueForString(String key, TypeReference<T> type) {
        String val = getStringValueChain(key);
        if (val == null || NULL_OBJECT.equals(val)) {
            return null;
        }
        return JSONUtil.toBean(val, type, true);
    }

    /* ==================== 布隆过滤器私有方法 ==================== */

    /**
     * 判断 key 是否在布隆过滤器中
     *
     * @param key 缓存键
     * @return true 可能存在，false 一定不存在
     */
    private boolean mightContain(String key) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().getBit(bloomKey(), bloomHash(key)));
    }

    /**
     * 把 key 写入布隆过滤器
     *
     * @param key 缓存键
     */
    private void putBloom(String key) {
        stringRedisTemplate.opsForValue().setBit(bloomKey(), bloomHash(key), true);
    }

    /**
     * 构造布隆过滤器在 Redis 中的 key
     *
     * @return bloom:{cacheType}
     */
    private String bloomKey() {
        return "bloom:" + cacheType();
    }

    /**
     * 简单 hash 函数，保证非负且落在 0~9_999_999 之间
     *
     * @param key 缓存键
     * @return hash 值
     */
    private long bloomHash(String key) {
        return Math.abs(key.hashCode()) % 10_000_000L;
    }

    /* ==================== 删除重试私有方法 ==================== */

    /**
     * 带指数退避的删除重试，最多 3 次
     *
     * @param key 缓存键
     */
    private void safeDelete(String key) {
        int retry = 3;
        while (retry-- > 0) {
            try {
                deleteValue(key);
                return;
            } catch (Exception e) {
                log.warn("delete key {} failed, remain retry {}", key, retry);
                try {
                    Thread.sleep(100L * (1L << (3 - retry)));
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.error("delete key {} finally failed after 3 retries", key);
    }

    /**
     * 在基础时间上增加 0~300 秒的随机抖动，防止大量 key 同时过期
     *
     * @return 实际过期秒数
     */
    private long randomExpire() {
        return (long) 300 + (long) (Math.random() * 300);
    }

    /* ==================== 延迟队列消费者 ==================== */

    /**
     * Spring 依赖注入完成后启动延迟队列消费者
     */
    @PostConstruct
    private void startDelayDeleteTask() {
        DELAY_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                Set<String> keys = stringRedisTemplate.opsForZSet()
                        .rangeByScore(DELAY_QUEUE_KEY, 0, System.currentTimeMillis(), 0, 100);
                if (keys != null && !keys.isEmpty()) {
                    for (String k : keys) {
                        stringRedisTemplate.delete(k);           // 二次删除
                        stringRedisTemplate.opsForZSet().remove(DELAY_QUEUE_KEY, k);
                        log.debug("delay second delete key={}", k);
                    }
                }
            } catch (Exception e) {
                log.error("delay delete task error", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
}