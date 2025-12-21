package com.yupi.yupicturebackend.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 增强版多级缓存快速验证（只使用 public 接口）
 */
@SpringBootTest
class BloomAndDelayTest {

    @Resource
    private CacheManager cacheManager;

    /* ---------- 1. 布隆过滤器拦截未知 key ---------- */
    @Test
    void bloomRejectUnknown() {
        // 从未写入过的 key
        String unknown = "unknown:key:" + System.currentTimeMillis();
        assertNull(cacheManager.getValueForString(unknown));
    }

    /* ---------- 2. 空值缓存（NULL_OBJECT）---------- */
    @Test
    void nullObjectCache() {
        String key = "null:key:" + System.currentTimeMillis();
        // 模拟回源为 null -> 模板类会写入 NULL_OBJECT
        assertNull(cacheManager.getValueForString(key));

        // 再次查询，应直接返回 null 且不再回源（Redis 里能看到占位值）
        assertNull(cacheManager.getValueForString(key));
    }

    /* ---------- 3. 延迟双删 ---------- */
    @Test
    void delayDoubleDelete() {
        String key = "delay:key:" + System.currentTimeMillis();
        cacheManager.setCacheForValue(key, "v");
        assertNotNull(cacheManager.getValueForString(key));

        // 第一次删除
        cacheManager.deleteCacheByKey(key);
        // 本地已失效，Redis 可能还有（延迟 5s 后二次删除）
        await().pollDelay(Duration.ofSeconds(6))
                .untilAsserted(() -> assertNull(cacheManager.getValueForString(key)));
    }

    /* ---------- 4. 普通读写 + 回填 ---------- */
    @Test
    void readWriteAndBackFill() {
        String key = "rw:key:" + System.currentTimeMillis();
        // 写
        cacheManager.setCacheForValue(key, "value");
        // 读
        assertEquals("value", cacheManager.getValueForString(key));
    }

    /* ---------- 5. 获取全链路 key ---------- */
    @Test
    void getKeysChain() {
        String k1 = "chain:k1:" + System.currentTimeMillis();
        String k2 = "chain:k2:" + System.currentTimeMillis();
        cacheManager.setCacheForValue(k1, "v1");
        cacheManager.setCacheForValue(k2, "v2");

        Set<String> keys = cacheManager.getKeys();
        assertTrue(keys.stream().anyMatch(k -> k.equals(k1) || k.equals(k2)));
    }


}