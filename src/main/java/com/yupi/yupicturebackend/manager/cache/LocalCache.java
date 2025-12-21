package com.yupi.yupicturebackend.manager.cache;

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
public class LocalCache extends EnhancedCacheChainTemplate {

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    @Override
    public String getStringValue(String key) {
        return (String) cache.getIfPresent(key);
    }

    @Override
    public void setStringValue(String key, String value, long expireSeconds) {
        cache.put(key, value);
    }

    @Override
    public Set<String> getKeys() {
        return new HashSet<>(cache.asMap().keySet());
    }

    @Override
    public void deleteValue(String key) {
        cache.invalidate(key);
    }

    @Override
    public void deleteValues(List<String> keys) {
        cache.invalidateAll(keys);
    }

    @Override
    protected String cacheType() {
        return "local";
    }

    @Autowired
    @Qualifier("redisCache")
    @Override
    public void setNext(EnhancedCacheChainTemplate next) {
        super.setNext(next);
    }
}
