package com.yupi.yupicturebackend.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQCanalConfig {

    /**
     * Canal 消息交换机
     */
    @Bean
    public TopicExchange canalExchange() {
        return new TopicExchange("canal.cache.exchange", true, false);
    }

    /**
     * 缓存失效队列
     */
    @Bean
    public Queue cacheEvictQueue() {
        return QueueBuilder.durable("cache.evict.queue")
                .withArgument("x-dead-letter-exchange", "cache.dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "cache.dlx.routing.key")
                .build();
    }

    /**
     * 绑定：按表名路由
     * routingKey 格式: canal.cache.exchange.yupi_picture.user
     */
    @Bean
    public Binding cacheEvictBinding() {
        return BindingBuilder.bind(cacheEvictQueue())
                .to(canalExchange())
                .with("canal.cache.exchange.yupi_picture.*");
    }

    /**
     * 死信交换机（处理失败消息）
     */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange("cache.dlx.exchange", true, false);
    }

    /**
     * 死信队列（人工介入）
     */
    @Bean
    public Queue dlxQueue() {
        return new Queue("cache.dlx.queue", true);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with("cache.dlx.routing.key");
    }

    /**
     * 跨实例缓存同步广播交换机（Fanout）
     */
    @Bean
    public FanoutExchange cacheBroadcastExchange() {
        return new FanoutExchange("cache.broadcast.exchange", true, false);
    }

    /**
     * 每个实例独有的队列（用于接收其他实例的失效通知）
     */
    @Bean
    public Queue cacheBroadcastQueue() {
        // 使用随机名称，确保每个实例都有独立队列
        return QueueBuilder.nonDurable()
                .autoDelete()
                .build();
    }

    @Bean
    public Binding cacheBroadcastBinding() {
        return BindingBuilder.bind(cacheBroadcastQueue())
                .to(cacheBroadcastExchange());
    }
}