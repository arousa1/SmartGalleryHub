package com.yupi.yupicturebackend.manager.cache;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

/**
 * 简化版多级缓存模板（移除延迟双删，依赖 Canal 异步失效）
 * 保留：布隆过滤器 + 空值缓存 + 删除重试
 *
 * @author arousal
 */
@Slf4j
public abstract class EnhancedCacheChainTemplate {

    /* ========================= 常量 ========================= */

    /** 空值缓存的 JSON 占位字符串 */
    protected static final String NULL_OBJECT = "\"__NULL__\"";

    /* ========================= 链路字段 ========================= */

    @Setter
    protected EnhancedCacheChainTemplate next;

    @Resource
    protected StringRedisTemplate stringRedisTemplate;

    /* ======================== 抽象方法 ======================== */

    public abstract String getStringValue(String key);

    public abstract void setStringValue(String key, String value, long expireSeconds);

    public abstract Set<String> getKeys();

    public abstract void deleteValue(String key);

    public abstract void deleteValues(List<String> keys);

    protected abstract String cacheType();

    /* ======================== 模板方法 ======================== */

    /**
     * 链式获取（保持不变）
     */
    public final String getStringValueChain(String key) {
        // 布隆过滤器拦截
        if (!mightContain(key)) {
            return null;
        }

        // L1/L2 命中
        String val = getStringValue(key);
        if (val != null) {
            if (NULL_OBJECT.equals(val)) {
                return null;
            }
            return val;
        }

        // 下游查询
        if (next != null) {
            String down = next.getStringValueChain(key);
            if (down != null) {
                setStringValue(key, down, randomExpire());
                return down;
            }
        }

        // 写空值缓存
        setStringValue(key, NULL_OBJECT, 30);
        return null;
    }

    /**
     * 链式写入（保持不变）
     */
    public final void setStringValueChain(String key, String value) {
        putBloom(key);
        setStringValue(key, value, randomExpire());
        if (next != null) {
            next.setStringValueChain(key, value);
        }
    }

    /**
     * 简化：同步删除 + 下游删除（不再延迟双删，由Canal保证最终一致性）
     */
    public final void deleteValueChain(String key) {
        safeDelete(key);
        if (next != null) {
            next.deleteValueChain(key);
        }
        // 移除：延迟双删逻辑，Canal会处理最终一致性
    }

    public final void deleteValuesChain(List<String> keys) {
        keys.forEach(this::deleteValueChain);
    }

    public final Set<String> getKeysChain() {
        Set<String> keys = getKeys();
        if (next != null) {
            keys.addAll(next.getKeysChain());
        }
        return keys;
    }

    public <T> T getObjectValueForString(String key, TypeReference<T> type) {
        String val = getStringValueChain(key);
        if (val == null || NULL_OBJECT.equals(val)) {
            return null;
        }
        return JSONUtil.toBean(val, type, true);
    }

    /* ==================== 布隆过滤器（保持不变）==================== */

    private boolean mightContain(String key) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().getBit(bloomKey(), bloomHash(key)));
    }

    private void putBloom(String key) {
        stringRedisTemplate.opsForValue().setBit(bloomKey(), bloomHash(key), true);
    }

    private String bloomKey() {
        return "bloom:" + cacheType();
    }

    private long bloomHash(String key) {
        return Math.abs(key.hashCode()) % 10_000_000L;
    }

    /* ==================== 删除重试（保持不变）==================== */

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

    private long randomExpire() {
        return (long) 300 + (long) (Math.random() * 300);
    }
}