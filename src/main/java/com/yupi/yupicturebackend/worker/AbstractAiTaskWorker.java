package com.yupi.yupicturebackend.worker;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.yupi.yupicturebackend.model.enums.AiTaskStatusEnum;
import com.yupi.yupicturebackend.model.message.AiTaskMessage;
import com.yupi.yupicturebackend.service.AiTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;

/**
 * AI 任务 Worker 抽象基类（支持混元+千问双模型）
 */
@Slf4j
public abstract class AbstractAiTaskWorker {

    @Resource
    protected AiTaskService aiTaskService;

    @Resource
    protected RabbitTemplate rabbitTemplate;

    /**
     * 最大重试次数
     */
    protected static final int MAX_RETRY = 3;

    /**
     * 消费入口（子类通过 @RabbitListener 指定队列）
     */
    protected void consume(Message message, Channel channel, String queueName) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        AiTaskMessage taskMessage = null;
        
        try {
            // 1. 解析消息
            String body = new String(message.getBody());
            taskMessage = JSONUtil.toBean(body, AiTaskMessage.class);
            
            Long taskId = taskMessage.getTaskId();
            log.info("[{}] 开始处理任务，taskId={}, type={}, provider={}", 
                    queueName, taskId, taskMessage.getTaskType(), taskMessage.getAiProvider());

            // 2. 幂等检查：任务是否已处理
            if (AiTaskStatusEnum.isFinished(aiTaskService.getTaskStatus(taskId).getStatus())) {
                log.warn("任务已完成，跳过重复处理，taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 标记处理中（10%）
            updateProgress(taskId, 10);

            // 4. 执行具体任务（子类实现）
            executeTask(taskMessage);

            // 5. 标记成功（100%）
            // 子类负责调用 updateProgress(taskId, 100, result)
            
            // 6. 确认消息
            channel.basicAck(deliveryTag, false);
            
            log.info("[{}] 任务处理成功，taskId={}", queueName, taskId);

        } catch (Exception e) {
            log.error("[{}] 任务处理失败", queueName, e);
            handleFailure(message, channel, deliveryTag, taskMessage, e);
        }
    }

    /**
     * 具体任务执行逻辑（子类实现）
     */
    protected abstract void executeTask(AiTaskMessage message) throws Exception;

    /**
     * 更新进度（供子类调用）
     */
    protected void updateProgress(Long taskId, int progress) {
        updateProgress(taskId, progress, null);
    }

    /**
     * 更新进度并设置结果
     */
    protected void updateProgress(Long taskId, int progress, String result) {
        aiTaskService.updateTaskProgress(taskId, progress, result);
    }

    /**
     * 保存任务结果（Map格式，自动转JSON）
     */
    protected void saveResult(Long taskId, int progress, Map<String, Object> resultMap) {
        String resultJson = resultMap != null ? JSONUtil.toJsonStr(resultMap) : null;
        updateProgress(taskId, progress, resultJson);
    }

    /**
     * 处理失败（重试或进入死信）
     */
    private void handleFailure(Message message, Channel channel, long deliveryTag,
                               AiTaskMessage taskMessage, Exception e) throws IOException {
        if (taskMessage == null) {
            // 消息解析失败，直接丢弃
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        int retryCount = taskMessage.getRetryCount() + 1;
        taskMessage.setRetryCount(retryCount);

        if (retryCount <= MAX_RETRY) {
            // 重新投递，延迟5秒*重试次数（指数退避）
            long delayMillis = 5000L * retryCount;
            
            log.warn("任务失败，准备第{}次重试，taskId={}, delay={}ms", 
                    retryCount, taskMessage.getTaskId(), delayMillis);
            
            // 拒绝消息，不重新入队
            channel.basicNack(deliveryTag, false, false);
            
            // 重新发送（带延迟）
            // 实际项目中可使用延迟队列插件，这里简化处理
            // 重新投递到队列尾部
            rabbitTemplate.convertAndSend(
                    "ai.task.exchange",
                    taskMessage.getRoutingKey(),
                    JSONUtil.toJsonStr(taskMessage),
                    msg -> {
                        // 设置过期时间实现延迟（简易方案）
                        msg.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                        return msg;
                    }
            );
        } else {
            // 超过重试次数，进入死信
            channel.basicNack(deliveryTag, false, false);
            aiTaskService.markTaskFailed(taskMessage.getTaskId(), 
                    "重试" + MAX_RETRY + "次后仍失败: " + e.getMessage());
        }
    }
}