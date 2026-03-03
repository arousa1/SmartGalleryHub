package com.yupi.yupicturebackend.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 任务视图对象（前端展示用）
 */
@Data
public class AiTaskVO implements Serializable {

    /**
     * 任务ID
     */
    private Long id;

    /**
     * 任务类型：TAG-自动打标, EXPAND-AI扩图, GENERATE-文生图
     */
    private String taskType;

    /**
     * AI 提供商：HUNYUAN-混元, ALIYUN-阿里千问
     */
    private String aiProvider;

    /**
     * 关联业务ID
     */
    private Long bizId;

    /**
     * 任务状态：PENDING-待处理, PROCESSING-处理中, SUCCESS-成功, FAILED-失败, CANCELLED-已取消
     */
    private String status;

    /**
     * 进度百分比 0-100
     */
    private Integer progress;

    /**
     * 执行结果（JSON字符串，前端按需解析）
     */
    private String result;

    /**
     * 错误信息
     */
    private String errorMsg;

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

    // 脱敏：不包含 parameters 字段
}