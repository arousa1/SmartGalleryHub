package com.yupi.yupicturebackend.manager.canal;

import com.rabbitmq.client.Channel;
import com.yupi.yupicturebackend.manager.cache.EnhancedCacheChainTemplate;
import com.yupi.yupicturebackend.manager.cache.LocalCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

/**
 * Canal 消息消费者：实现缓存异步精准失效
 */
@Slf4j
@Component
public class CanalMessageConsumer {

    @Resource
    private LocalCache localCache;  // L1 入口
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private CacheKeyGenerator cacheKeyGenerator;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 监听 Canal 消息队列
     */
    @RabbitListener(queues = "cache.evict.queue", concurrency = "3-10")
    public void onCanalMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        try {
            String body = new String(message.getBody());
            CanalMessage canalMessage = parseMessage(body);
            
            if (canalMessage == null || canalMessage.getIsDdl()) {
                // DDL 语句或解析失败，直接确认
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.debug("收到Canal消息: {}.{} 操作: {}", 
                    canalMessage.getDatabase(), 
                    canalMessage.getTable(), 
                    canalMessage.getType());

            // 处理数据变更
            processDataChange(canalMessage);
            
            // 确认消息
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("处理Canal消息失败: {}", new String(message.getBody()), e);
            
            // 重试3次后进入死信队列
            Integer retryCount = (Integer) message.getMessageProperties()
                    .getHeaders().getOrDefault("x-retry-count", 0);
            
            if (retryCount >= 3) {
                channel.basicReject(deliveryTag, false);  // 进入死信队列
            } else {
                // 重新投递，增加重试计数
                message.getMessageProperties().setHeader("x-retry-count", retryCount + 1);
                rabbitTemplate.send("cache.evict.queue", message);
                channel.basicAck(deliveryTag, false);
            }
        }
    }

    /**
     * 处理数据变更，生成并失效缓存Key
     */
    private void processDataChange(CanalMessage message) {
        List<Map<String, Object>> dataList = message.getData();
        List<Map<String, Object>> oldList = message.getOld();
        
        // DELETE 操作只有 old 数据
        if ("DELETE".equals(message.getType())) {
            dataList = oldList;
        }
        
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        Set<String> allKeys = new HashSet<>();
        
        for (int i = 0; i < dataList.size(); i++) {
            Map<String, Object> newData = dataList.get(i);
            Map<String, Object> oldData = ("UPDATE".equals(message.getType()) && oldList != null) 
                    ? oldList.get(i) : null;
            
            // 生成需要失效的缓存Key
            Set<String> keys = cacheKeyGenerator.generateKeys(
                    message.getDatabase(),
                    message.getTable(),
                    message.getType(),
                    newData,
                    oldData
            );
            
            allKeys.addAll(keys);
        }

        if (!allKeys.isEmpty()) {
            // 执行缓存失效
            evictCaches(allKeys);
            log.info("Canal触发缓存失效，表: {}.{}, 操作: {}, keys数量: {}", 
                    message.getDatabase(), message.getTable(), message.getType(), allKeys.size());
        }
    }

    /**
     * 失效多级缓存
     */
    private void evictCaches(Set<String> keys) {
        for (String key : keys) {
            try {
                if (key.endsWith("*")) {
                    // 模糊匹配删除（Redis）
                    evictPattern(key);
                } else {
                    // 精确删除
                    evictExact(key);
                }
            } catch (Exception e) {
                log.error("缓存失效失败: {}", key, e);
            }
        }
    }

    /**
     * 精确删除：L1 + L2
     */
    private void evictExact(String key) {
        // 1. 删除本地 L1
        localCache.deleteValue(key);
        
        // 2. 删除 Redis L2
        stringRedisTemplate.delete(key);
        
        // 3. 广播给其他实例删除 L1
        broadcastEvict(key);
    }

    /**
     * 模糊删除（主要针对Redis中的列表缓存）
     */
    private void evictPattern(String pattern) {
        // 1. 从 Redis 找出匹配的 keys
        Set<String> redisKeys = stringRedisTemplate.keys(pattern);
        
        if (redisKeys != null && !redisKeys.isEmpty()) {
            // 2. 删除 Redis
            stringRedisTemplate.delete(redisKeys);
            
            // 3. 删除本地 L1（遍历删除）
            for (String key : redisKeys) {
                localCache.deleteValue(key);
                broadcastEvict(key);
            }
        }
        
        // 4. 对于本地 Caffeine，只能遍历所有key匹配（性能考虑，实际可能跳过）
        // 或者依赖 Caffeine 的短 TTL 自然过期
    }

    /**
     * 广播缓存失效事件（通知其他实例清除 L1）
     */
    private void broadcastEvict(String key) {
        CacheEvictBroadcast broadcast = new CacheEvictBroadcast();
        broadcast.setKey(key);
        broadcast.setSourceInstance(getInstanceId());
        broadcast.setTimestamp(System.currentTimeMillis());
        
        rabbitTemplate.convertAndSend("cache.broadcast.exchange", "", broadcast);
    }

    /**
     * 接收其他实例的缓存失效广播
     */
    @RabbitListener(queues = "#{cacheBroadcastQueue.name}")
    public void onBroadcastEvict(CacheEvictBroadcast broadcast) {
        // 忽略自己发出的消息
        if (getInstanceId().equals(broadcast.getSourceInstance())) {
            return;
        }
        
        // 只清除本地 L1，不操作 Redis（避免重复删除）
        localCache.deleteValue(broadcast.getKey());
        
        log.debug("收到广播失效通知，清除本地缓存: {}", broadcast.getKey());
    }

    /**
     * 获取当前实例标识
     */
    private String getInstanceId() {
        // 实际应配置为 IP:PORT 或 Pod Name
        return System.getenv("HOSTNAME") + ":" + System.getProperty("server.port", "8080");
    }

    /**
     * 解析 Canal 消息
     */
    private CanalMessage parseMessage(String json) {
        try {
            return com.alibaba.fastjson.JSON.parseObject(json, CanalMessage.class);
        } catch (Exception e) {
            log.error("解析Canal消息失败: {}", json, e);
            return null;
        }
    }
}