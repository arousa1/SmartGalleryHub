package com.yupi.yupicturebackend.manager.cache;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine本地缓存操作类
 */
@Component
public class LocalCache extends CacheChainTemplate {
    /**
     * 手动启动本地缓存
     */
    private final Cache<String, Object> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10_000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    @Override
    @Autowired
    @Qualifier("redisCache")
    public void setNext(CacheChainTemplate next) {
        super.setNext(next);
    }

    @Override
    public String getStringValue(String key) {
        return (String) LOCAL_CACHE.getIfPresent(key);
    }

    @Override
    public void setStringValue(String key, String value) {
        LOCAL_CACHE.put(key, value);
    }

    @Override
    public Set<String> getKeys() {
        Set<String> strings = LOCAL_CACHE.asMap().keySet();
        return CollUtil.isEmpty(strings) ? new HashSet<>() : new HashSet<>(strings);
    }

    @Override
    public void deleteValue(String key) {
        LOCAL_CACHE.invalidate(key);
    }

    @Override
    public void deleteValues(List<String> keys) {
        LOCAL_CACHE.invalidateAll(keys);
    }
}
