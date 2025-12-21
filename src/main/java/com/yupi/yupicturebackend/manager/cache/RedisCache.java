package com.yupi.yupicturebackend.manager.cache;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis分布式缓存操作类
 */
@Component
public class RedisCache extends CacheChainTemplate {

    private static final long BASE_EXPIRE = 300L;

    private static final TimeUnit SECONDS = TimeUnit.SECONDS;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void setNext(CacheChainTemplate next) {
        super.setNext(null);
    }

    @Override
    public void setStringValue(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value, getExpire(), SECONDS);
    }

    @Override
    public Set<String> getKeys() {
        Set<String> keys = stringRedisTemplate.keys("*");
        if (CollUtil.isEmpty(keys)) {
            return new HashSet<>();
        }
        return keys.stream().filter(key -> !key.startsWith("spring:session"))
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
    public String getStringValue(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    private long getExpire() {
        return BASE_EXPIRE + RandomUtil.randomLong(0, 300);
    }
}
