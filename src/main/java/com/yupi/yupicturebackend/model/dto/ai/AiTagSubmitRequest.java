package com.yupi.yupicturebackend.model.dto.ai;

import lombok.Data;


import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 混元自动打标提交请求
 */
@Data
public class AiTagSubmitRequest implements Serializable {

    /**
     * 图片ID
     */
    @NotNull(message = "图片ID不能为空")
    private Long pictureId;

    /**
     * 图片URL（必须是公网可访问地址，或COS地址）
     */
    @NotBlank(message = "图片URL不能为空")
    private String imageUrl;
}