package com.yupi.yupicturebackend.model.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 任务提交响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskSubmitVO implements Serializable {

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 提示信息
     */
    private String message;

    public AiTaskSubmitVO(Long taskId) {
        this.taskId = taskId;
        this.message = "任务已提交";
    }
}