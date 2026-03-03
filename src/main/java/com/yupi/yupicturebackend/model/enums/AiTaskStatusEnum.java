package com.yupi.yupicturebackend.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 任务状态枚举
 */
@Getter
public enum AiTaskStatusEnum {

    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消");

    private final String value;
    private final String text;

    AiTaskStatusEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据 value 获取枚举
     */
    public static AiTaskStatusEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (AiTaskStatusEnum status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 获取所有未终态的状态（还在处理中）
     */
    public static List<AiTaskStatusEnum> getUnfinishedStatuses() {
        return Arrays.stream(values())
                .filter(s -> s != SUCCESS && s != FAILED && s != CANCELLED)
                .collect(Collectors.toList());
    }

    /**
     * 判断是否已结束
     */
    public static boolean isFinished(String status) {
        return SUCCESS.value.equals(status) 
                || FAILED.value.equals(status) 
                || CANCELLED.value.equals(status);
    }
}