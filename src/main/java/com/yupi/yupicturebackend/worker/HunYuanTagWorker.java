package com.yupi.yupicturebackend.worker;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.yupi.yupicturebackend.manager.HunYuanManager;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.message.AiTaskMessage;
import com.yupi.yupicturebackend.model.vo.AIPictureInfoVO;
import com.yupi.yupicturebackend.service.AiTaskService;
import com.yupi.yupicturebackend.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 混元自动打标 Worker
 * 消费 ai.hunyuan.tag.queue
 */
@Slf4j
@Component
public class HunYuanTagWorker extends AbstractAiTaskWorker {

    @Resource
    private HunYuanManager hunYuanManager;

    @Resource
    private PictureService pictureService;

    @Resource
    private AiTaskService aiTaskService;

    /**
     * 混元打标队列：并发 5-10（混元响应快）
     */
    @RabbitListener(queues = "ai.hunyuan.tag.queue", concurrency = "5-10")
    public void onMessage(Message message, Channel channel) throws Exception {
        consume(message, channel, "ai.hunyuan.tag.queue");
    }

    @Override
    protected void executeTask(AiTaskMessage message) throws Exception {
        Long taskId = message.getTaskId();
        Map<String, Object> params = message.getParameters();

        Long pictureId = Long.valueOf(params.get("pictureId").toString());
        String imageUrl = (String) params.get("imageUrl");

        // 1. 开始处理
        updateProgress(taskId, 10);

        // 2. 调用混元分析图片
        // 支持 URL 或本地文件，这里用 URL 方式
        AIPictureInfoVO result = hunYuanManager.analyzeImage(imageUrl);

        updateProgress(taskId, 80);

        // 3. 更新图片表（触发Canal缓存失效）
        Picture picture = new Picture();
        picture.setId(pictureId);
        picture.setName(result.getIntroduction());  // 简介作为名称
        picture.setTags(result.getTags());
        picture.setCategory(result.getCategory());

        pictureService.updateById(picture);

        // 4. 保存任务结果（Java 8 兼容写法）
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("introduction", result.getIntroduction());
        resultMap.put("tags", result.getTags());
        resultMap.put("category", result.getCategory());

        updateProgress(taskId, 100, JSONUtil.toJsonStr(resultMap));

        log.info("混元打标完成，taskId={}, pictureId={}, tags={}",
                taskId, pictureId, result.getTags());
    }
}