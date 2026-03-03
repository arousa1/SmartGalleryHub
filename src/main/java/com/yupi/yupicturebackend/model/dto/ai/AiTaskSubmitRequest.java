package com.yupi.yupicturebackend.model.dto.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * AI 任务提交请求
 */
@Data
public class AiTaskSubmitRequest implements Serializable {

    /**
     * 任务类型：TAG-自动打标, EXPAND-AI扩图, GENERATE-文生图
     */
    private String taskType;

    /**
     * AI 提供商（可选，默认根据任务类型自动选择）
     * HUNYUAN-混元, ALIYUN-阿里千问
     */
    private String aiProvider;

    /**
     * 关联业务ID（如图片ID）
     */
    private Long bizId;

    /**
     * 任务参数（具体参数根据任务类型不同）
     * 
     * 混元打标：{imageUrl: "图片URL", pictureId: 123}
     * 千问扩图：{originalUrl: "原图URL", pictureId: 123, xScale: 1.5, yScale: 1.0}
     * 千问生图：{prompt: "提示词", style: "写实", width: 1024, height: 1024}
     */
    private Map<String, Object> parameters;
}