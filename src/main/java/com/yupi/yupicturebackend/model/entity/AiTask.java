package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 任务表（支持混元+千问双模型）
 */
@Data
@TableName("ai_task")
public class AiTask implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 任务类型：TAG-混元自动打标, EXPAND-千问扩图, GENERATE-千问生图
     */
    private String taskType;

    /**
     * AI 模型来源：HUNYUAN-混元, ALIYUN-阿里千问
     */
    private String aiProvider;

    /**
     * 关联业务ID（如图片ID）
     */
    private Long bizId;

    /**
     * 任务状态：PENDING-待处理, PROCESSING-处理中, SUCCESS-成功, FAILED-失败
     */
    private String status;

    /**
     * 任务参数（JSON格式）
     */
    private String parameters;

    /**
     * 执行结果（JSON格式）
     */
    private String result;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 进度百分比 0-100
     */
    private Integer progress;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建用户ID
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;

    @TableLogic
    private Integer isDelete;
}