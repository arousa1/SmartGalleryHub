package com.yupi.yupicturebackend.manager.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisWebSocketConfig {

    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                                   MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 订阅频道
        container.addMessageListener(listenerAdapter, new ChannelTopic(PictureEditRedisListener.PICTURE_EDIT_PEN_CHANNEL));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(PictureEditRedisListener pictureEditRedisListener) {
        return new MessageListenerAdapter(pictureEditRedisListener, "onMessage");
    }
}
