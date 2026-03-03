package com.yupi.yupicturebackend.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.mapper.AiTaskMapper;
import com.yupi.yupicturebackend.model.dto.ai.AiTaskSubmitRequest;
import com.yupi.yupicturebackend.model.entity.AiTask;
import com.yupi.yupicturebackend.model.enums.AiTaskStatusEnum;
import com.yupi.yupicturebackend.model.enums.AiTaskTypeEnum;
import com.yupi.yupicturebackend.model.message.AiTaskMessage;
import com.yupi.yupicturebackend.model.vo.ai.AiTaskVO;
import com.yupi.yupicturebackend.service.AiTaskService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
* @author a
* @description 针对表【ai_task(AI任务表（异步处理混元打标、千问扩图等）)】的数据库操作Service实现
* @createDate 2026-03-03 15:35:13
*/
@Service
@Slf4j
public class AiTaskServiceImpl extends ServiceImpl<AiTaskMapper, AiTask>
    implements AiTaskService {
    @Resource
    private AiTaskMapper aiTaskMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String AI_TASK_KEY_PREFIX = "ai:task:";
    private static final long TASK_CACHE_TTL = 30;

    @Transactional
    @Override
    public Long submitTask(AiTaskSubmitRequest request, Long userId) {
        // 1. 校验任务类型
        AiTaskTypeEnum taskType = AiTaskTypeEnum.getByValue(request.getTaskType());
        if (taskType == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的任务类型");
        }

        // 2. 确定 AI 提供商（可自定义，或使用默认）
        String provider = request.getAiProvider();
        if (provider == null) {
            provider = taskType.getDefaultProvider();
        }

        // 3. 创建任务记录
        AiTask task = new AiTask();
        task.setTaskType(taskType.getValue());
        task.setAiProvider(provider);
        task.setBizId(request.getBizId());
        task.setStatus(AiTaskStatusEnum.PENDING.getValue());
        task.setParameters(JSONUtil.toJsonStr(request.getParameters()));
        task.setProgress(0);
        task.setRetryCount(0);
        task.setUserId(userId);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        aiTaskMapper.insert(task);
        Long taskId = task.getId();

        // 4. 构建消息
        AiTaskMessage message = new AiTaskMessage();
        message.setTaskId(taskId);
        message.setTaskType(taskType.getValue());
        message.setAiProvider(provider);
        message.setBizId(request.getBizId());
        message.setParameters(request.getParameters());
        message.setSubmitTime(System.currentTimeMillis());

        // 5. 投递到对应队列
        String routingKey = message.getRoutingKey();
        rabbitTemplate.convertAndSend("ai.task.exchange", routingKey, message, msg -> {
            msg.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            msg.getMessageProperties().setMessageId(String.valueOf(taskId));
            return msg;
        });

        log.info("AI任务已提交，taskId={}, type={}, provider={}, routingKey={}",
                taskId, taskType.getValue(), provider, routingKey);

        // 6. 缓存状态
        cacheTaskStatus(task);

        return taskId;
    }

    @Override
    public AiTaskVO getTaskStatus(Long taskId) {
        String cacheKey = AI_TASK_KEY_PREFIX + taskId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return JSONUtil.toBean(cached, AiTaskVO.class);
        }

        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }

        AiTaskVO vo = convertToVO(task);
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(vo),
                TASK_CACHE_TTL, TimeUnit.MINUTES);
        return vo;
    }

    @Override
    public void updateTaskProgress(Long taskId, Integer progress, String result) {
        AiTask task = new AiTask();
        task.setId(taskId);
        task.setProgress(progress);

        if (progress >= 100) {
            task.setStatus(AiTaskStatusEnum.SUCCESS.getValue());
            task.setResult(result);
            task.setFinishTime(LocalDateTime.now());
        } else {
            task.setStatus(AiTaskStatusEnum.PROCESSING.getValue());
        }

        task.setUpdateTime(LocalDateTime.now());
        aiTaskMapper.updateById(task);
        cacheTaskStatus(task);
    }

    @Override
    public void markTaskFailed(Long taskId, String errorMsg) {
        AiTask task = new AiTask();
        task.setId(taskId);
        task.setStatus(AiTaskStatusEnum.FAILED.getValue());
        task.setErrorMsg(errorMsg);
        task.setFinishTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        aiTaskMapper.updateById(task);
        cacheTaskStatus(task);

        log.error("AI任务失败，taskId={}, error={}", taskId, errorMsg);
    }

    private void cacheTaskStatus(AiTask task) {
        String cacheKey = AI_TASK_KEY_PREFIX + task.getId();
        AiTaskVO vo = convertToVO(task);
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(vo),
                TASK_CACHE_TTL, TimeUnit.MINUTES);
    }

    private AiTaskVO convertToVO(AiTask task) {
        AiTaskVO vo = new AiTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }
}




