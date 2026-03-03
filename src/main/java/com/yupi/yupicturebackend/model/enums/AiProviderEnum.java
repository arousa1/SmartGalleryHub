package com.yupi.yupicturebackend.model.enums;

import lombok.Getter;

/**
 * AI 提供商枚举
 */
@Getter
public enum AiProviderEnum {
    HUNYUAN("HUNYUAN", "腾讯混元"),
    ALIYUN("ALIYUN", "阿里千问");

    private final String value;
    private final String text;

    AiProviderEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }
}