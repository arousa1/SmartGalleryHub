package com.yupi.yupicturebackend.model.dto.ai;

import lombok.Data;


import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 千问 AI 扩图提交请求
 */
@Data
public class AiExpandSubmitRequest implements Serializable {

    /**
     * 原图片ID
     */
    @NotNull(message = "图片ID不能为空")
    private Long pictureId;

    /**
     * 原图URL
     */
    @NotBlank(message = "原图URL不能为空")
    private String originalUrl;

    /**
     * 水平扩展比例（1.0-3.0，默认1.0）
     */
    private Float xScale = 1.0f;

    /**
     * 垂直扩展比例（1.0-3.0，默认1.0）
     */
    private Float yScale = 1.0f;

    /**
     * 左边扩展像素（可选）
     */
    private Integer leftOffset = 0;

    /**
     * 右边扩展像素（可选）
     */
    private Integer rightOffset = 0;

    /**
     * 上边扩展像素（可选）
     */
    private Integer topOffset = 0;

    /**
     * 下边扩展像素（可选）
     */
    private Integer bottomOffset = 0;

    /**
     * 是否最佳质量（耗时更长，默认false）
     */
    private Boolean bestQuality = false;
}