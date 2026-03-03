package com.yupi.yupicturebackend.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ AI 任务队列配置（混元+千问双模型）
 */
@Configuration
public class RabbitMQAiTaskConfig {

    /* ========================= 交换机 ========================= */

    /**
     * AI 任务主题交换机
     */
    @Bean
    public TopicExchange aiTaskExchange() {
        return new TopicExchange("ai.task.exchange", true, false);
    }

    /**
     * AI 任务死信交换机
     */
    @Bean
    public DirectExchange aiTaskDlxExchange() {
        return new DirectExchange("ai.task.dlx.exchange", true, false);
    }

    /* ========================= 混元队列（自动打标） ========================= */

    /**
     * 混元打标任务队列
     * 混元 API 通常响应快（1-3秒），并发可较高
     */
    @Bean
    public Queue hunYuanTagTaskQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-max-length", 100000);
        args.put("x-message-ttl", 5 * 60 * 1000);  // 5分钟过期
        args.put("x-dead-letter-exchange", "ai.task.dlx.exchange");
        args.put("x-dead-letter-routing-key", "ai.task.dlx.routing.key");
        
        return QueueBuilder.durable("ai.hunyuan.tag.queue").withArguments(args).build();
    }

    @Bean
    public Binding hunYuanTagTaskBinding() {
        return BindingBuilder.bind(hunYuanTagTaskQueue())
                .to(aiTaskExchange())
                .with("ai.task.hunyuan.tag");
    }

    /* ========================= 千问队列（扩图+生图） ========================= */

    /**
     * 千问扩图任务队列
     * 扩图耗时较长（10-60秒），并发不宜过高
     */
    @Bean
    public Queue aliYunExpandTaskQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-max-length", 50000);
        args.put("x-message-ttl", 10 * 60 * 1000);  // 10分钟过期
        args.put("x-dead-letter-exchange", "ai.task.dlx.exchange");
        args.put("x-dead-letter-routing-key", "ai.task.dlx.routing.key");
        
        return QueueBuilder.durable("ai.aliyun.expand.queue").withArguments(args).build();
    }

    @Bean
    public Binding aliYunExpandTaskBinding() {
        return BindingBuilder.bind(aliYunExpandTaskQueue())
                .to(aiTaskExchange())
                .with("ai.task.aliyun.expand");
    }

    /**
     * 千问生图任务队列
     * 生图最耗时（30-120秒），并发最低
     */
    @Bean
    public Queue aliYunGenerateTaskQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-max-length", 20000);
        args.put("x-message-ttl", 15 * 60 * 1000);  // 15分钟过期
        args.put("x-dead-letter-exchange", "ai.task.dlx.exchange");
        args.put("x-dead-letter-routing-key", "ai.task.dlx.routing.key");
        
        return QueueBuilder.durable("ai.aliyun.generate.queue").withArguments(args).build();
    }

    @Bean
    public Binding aliYunGenerateTaskBinding() {
        return BindingBuilder.bind(aliYunGenerateTaskQueue())
                .to(aiTaskExchange())
                .with("ai.task.aliyun.generate");
    }

    /* ========================= 死信队列 ========================= */

    @Bean
    public Queue aiTaskDlxQueue() {
        return QueueBuilder.durable("ai.task.dlx.queue").build();
    }

    @Bean
    public Binding aiTaskDlxBinding() {
        return BindingBuilder.bind(aiTaskDlxQueue())
                .to(aiTaskDlxExchange())
                .with("ai.task.dlx.routing.key");
    }
}