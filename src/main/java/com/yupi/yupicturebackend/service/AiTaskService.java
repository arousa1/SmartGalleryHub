package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.ai.AiTaskSubmitRequest;
import com.yupi.yupicturebackend.model.entity.AiTask;
import com.yupi.yupicturebackend.model.vo.ai.AiTaskVO;

import org.springframework.transaction.annotation.Transactional;

/**
* @author a
* @description 针对表【ai_task(AI任务表（异步处理混元打标、千问扩图等）)】的数据库操作Service
* @createDate 2026-03-03 15:35:13
*/
public interface AiTaskService extends IService<AiTask> {

    @Transactional
    Long submitTask(AiTaskSubmitRequest request, Long userId);

    AiTaskVO getTaskStatus(Long taskId);

    void updateTaskProgress(Long taskId, Integer progress, String result);

    void markTaskFailed(Long taskId, String errorMsg);
}
