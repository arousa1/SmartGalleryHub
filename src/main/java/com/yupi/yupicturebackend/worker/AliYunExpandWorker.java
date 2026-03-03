package com.yupi.yupicturebackend.worker;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.yupi.yupicturebackend.api.aliyunai.AliYunAiApi;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yupi.yupicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yupi.yupicturebackend.model.message.AiTaskMessage;
import com.yupi.yupicturebackend.service.AiTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 千问 AI 扩图 Worker
 * 消费 ai.aliyun.expand.queue
 */
@Slf4j
@Component
public class AliYunExpandWorker extends AbstractAiTaskWorker {

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private AiTaskService aiTaskService;

    /**
     * 千问扩图队列：并发 2-5（扩图耗时较长）
     */
    @RabbitListener(queues = "ai.aliyun.expand.queue", concurrency = "2-5")
    public void onMessage(Message message, Channel channel) throws Exception {
        consume(message, channel, "ai.aliyun.expand.queue");
    }

    @Override
    protected void executeTask(AiTaskMessage message) throws Exception {
        Long taskId = message.getTaskId();
        Map<String, Object> params = message.getParameters();

        Long pictureId = Long.valueOf(params.get("pictureId").toString());
        String originalUrl = (String) params.get("originalUrl");
        Float xScale = params.containsKey("xScale") ?
                Float.valueOf(params.get("xScale").toString()) : 1.0f;
        Float yScale = params.containsKey("yScale") ?
                Float.valueOf(params.get("yScale").toString()) : 1.0f;

        // 1. 提交扩图任务
        updateProgress(taskId, 10);

        CreateOutPaintingTaskRequest request = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(originalUrl);
        request.setInput(input);

        CreateOutPaintingTaskRequest.Parameters parameters = new CreateOutPaintingTaskRequest.Parameters();
        parameters.setXScale(xScale);
        parameters.setYScale(yScale);
        parameters.setAddWatermark(false);
        request.setParameters(parameters);

        CreateOutPaintingTaskResponse response = aliYunAiApi.createOutPaintingTask(request);
        String aliTaskId = response.getOutput().getTaskId();

        updateProgress(taskId, 30);

        // 2. 轮询查询结果
        String finalImageUrl = pollTaskResult(taskId, aliTaskId);

        // 3. 保存结果（触发Canal缓存失效）
        saveExpandResult(pictureId, finalImageUrl);

        // ========== Java 8 兼容写法 ==========
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("expandedUrl", finalImageUrl);
        updateProgress(taskId, 100, JSONUtil.toJsonStr(resultMap));
        // =====================================

        log.info("千问扩图完成，taskId={}, pictureId={}", taskId, pictureId);
    }

    /**
     * 轮询查询千问任务结果
     */
    private String pollTaskResult(Long taskId, String aliTaskId) throws Exception {
        int maxPoll = 60;  // 最多轮询60次
        int pollCount = 0;

        while (pollCount < maxPoll) {
            Thread.sleep(5000);  // 5秒轮询

            GetOutPaintingTaskResponse result = aliYunAiApi.getOutPaintingTask(aliTaskId);
            String status = result.getOutput().getTaskStatus();

            switch (status) {
                case "PENDING":
                    updateProgress(taskId, 35);
                    break;
                case "RUNNING":
                    updateProgress(taskId, 60);
                    break;
                case "SUCCEEDED":
                    return result.getOutput().getOutputImageUrl();
                case "FAILED":
                    throw new RuntimeException("扩图失败: " + result.getOutput().getMessage());
                default:
                    log.warn("未知状态: {}", status);
            }

            pollCount++;
        }

        throw new RuntimeException("扩图任务超时");
    }

    private void saveExpandResult(Long pictureId, String expandedUrl) {
        // 保存扩图结果到业务表，Canal会自动失效缓存
        // 实际调用 PictureService 更新
    }
}