package com.yupi.yupicturebackend.manager.cache;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import lombok.Setter;

import java.util.List;
import java.util.Set;

/**
 * 多级缓存模板类
 * @author  arousal
 */
@Setter
public abstract class CacheChainTemplate {

    protected CacheChainTemplate next;

    /**
     * 获取缓存值
     */
    public abstract String getStringValue(String key);

    /**
     * 设置缓存值
     */
    public abstract void setStringValue(String key, String value);

    /**
     * 获取缓存 key
     */
    public abstract Set<String> getKeys();

    /**
     * 获取所有层次的缓存 key
     */
    public Set<String> getKeysChain() {
        Set<String> keys = getKeys();
        if (next != null) {
            Set<String> keysChain = next.getKeysChain();
            keys.addAll(keysChain);
        }
        return keys;
    }

    /**
     * 删除一个 key
     */
    public abstract void deleteValue(String key);

    /**
     * 链式删除一个 key
     */
    public void deleteValueChain(String key) {
        deleteValue(key);
        if (next != null) {
            next.deleteValueChain(key);
        }
    }

    /**
     * 删除 keys
     */
    public abstract void deleteValues(List<String> keys);

    /**
     * 链式删除 keys
     */
    public void deleteValuesChain(List<String> keys) {
        deleteValues(keys);
        if (next != null) {
            next.deleteValuesChain(keys);
        }
    }

    /**
     * 链式获取缓存值
     */
    public String getStringValueChain(String key) {
        String value = getStringValue(key);
        if (value != null) {
            return value;
        }
        if (next != null) {
            String stringValueChain = next.getStringValueChain(key);
            if (stringValueChain != null) {
                // 这里会将查到的缓存存入当前缓存层
                setStringValue(key, stringValueChain);
                return stringValueChain;
            }
        }
        return null;
    }

    /**
     * 链式设置缓存值
     */
    public void setStringValueChain(String key, String value) {
        setStringValue(key, value);
        if (next != null) {
            next.setStringValueChain(key, value);
        }
    }

    public <T> T getObjectValueForString(String key, TypeReference<T> type) {
        String stringValueChain = getStringValueChain(key);
        if (stringValueChain != null) {
            return JSONUtil.toBean(stringValueChain, type, true);
        }
        return null;
    }
}
