package com.yupi.yupicturebackend.utils;

import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import org.springframework.util.DigestUtils;

/**
 * 缓存 key 构造工具
 */
public class CacheKeyUtils {

    /**
     * 根据 分页查询请求 构建缓存 key
     * @param pictureQueryRequest
     * @return
     */
    public static String listPictureByPageVoKey(PictureQueryRequest pictureQueryRequest) {
        // 普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 查询缓存，缓存中没有，再查询数据库
        // 构建缓存的 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        return String.format("smartGalleryHub:listPictureVOByPage:%s", hashKey);
    }

    /**
     * 通过 id获取图片查询请求 构建缓存key
     * @param id
     * @return
     */
    public static String getPictureVoByIdKey(Long id) {
        return String.format("smartGalleryHub:getPictureVoById:%s", id);
    }
}
