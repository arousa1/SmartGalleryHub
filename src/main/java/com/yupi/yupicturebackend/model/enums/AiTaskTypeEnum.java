package com.yupi.yupicturebackend.model.enums;

import lombok.Getter;

/**
 * AI 任务类型枚举
 */
@Getter
public enum AiTaskTypeEnum {
    AUTO_TAG("TAG", "自动打标", "HUNYUAN"),
    IMAGE_EXPAND("EXPAND", "AI扩图", "ALIYUN"),
    TEXT_TO_IMAGE("GENERATE", "文生图", "ALIYUN");

    private final String value;
    private final String text;
    private final String defaultProvider;

    AiTaskTypeEnum(String value, String text, String defaultProvider) {
        this.value = value;
        this.text = text;
        this.defaultProvider = defaultProvider;
    }

    public static AiTaskTypeEnum getByValue(String value) {
        for (AiTaskTypeEnum type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}