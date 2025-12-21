package com.yupi.yupicturebackend.manager.cache;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis分布式缓存操作类
 */
@Component("redisCache")
public class RedisCache extends EnhancedCacheChainTemplate {

    @Override
    public String getStringValue(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public void setStringValue(String key, String value, long expireSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Set<String> getKeys() {
        return stringRedisTemplate.keys("*").stream()
                .filter(k -> !k.startsWith("spring:session"))
                .collect(Collectors.toSet());
    }

    @Override
    public void deleteValue(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public void deleteValues(List<String> keys) {
        stringRedisTemplate.delete(keys);
    }

    @Override
    protected String cacheType() {
        return "redis";
    }

    /* 结束链 */
    @Override
    public void setNext(EnhancedCacheChainTemplate next) {
        super.setNext(null);
    }
}
