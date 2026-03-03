package com.yupi.yupicturebackend.model.message;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * AI 任务消息（统一格式，支持混元+千问）
 */
@Data
public class AiTaskMessage implements Serializable {

    private Long taskId;
    
    private String taskType;  // TAG/EXPAND/GENERATE
    
    private String aiProvider;  // HUNYUAN/ALIYUN
    
    private Long bizId;
    
    /**
     * 任务参数
     * 混元打标：{imageUrl, pictureId}
     * 千问扩图：{originalUrl, pictureId, xScale, yScale...}
     * 千问生图：{prompt, style, width, height}
     */
    private Map<String, Object> parameters;
    
    private Long submitTime;
    
    private Integer retryCount = 0;
    
    /**
     * 获取路由Key
     */
    public String getRoutingKey() {
        return String.format("ai.task.%s.%s", 
                aiProvider.toLowerCase(), 
                getShortTaskType());
    }
    
    private String getShortTaskType() {
        switch (taskType) {
            case "TAG": return "tag";
            case "EXPAND": return "expand";
            case "GENERATE": return "generate";
            default: return "default";
        }
    }
}