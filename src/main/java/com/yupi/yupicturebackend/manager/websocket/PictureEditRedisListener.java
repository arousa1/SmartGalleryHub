package com.yupi.yupicturebackend.manager.websocket;

import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class PictureEditRedisListener implements MessageListener {

    public static final String PICTURE_EDIT_PEN_CHANNEL = "picture_edit_channel";

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 获取并反序列化消息
            String messageBody = new String(message.getBody());
            PictureEditResponseMessage responseMessage = JSONUtil.toBean(messageBody, PictureEditResponseMessage.class);
            Long pictureId = responseMessage.getPictureId();
            if (pictureId != null) {
                // 调用本地的 WebSocket 处理器进行页面广播
                pictureEditHandler.broadcastToPicture(pictureId, responseMessage);
            }
        } catch (Exception e) {
            log.error("Redis 协同编辑消息处理异常", e);
        }
    }
}
