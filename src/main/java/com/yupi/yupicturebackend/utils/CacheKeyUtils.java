package com.yupi.yupicturebackend.utils;

import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import org.springframework.util.DigestUtils;

/**
 * 缓存 Key 构造工具（适配 Canal 多级缓存架构）
 *
 * 【设计原则】
 * 1. Key 格式：业务标识:库名:表名:维度:标识
 * 2. 与 Canal 端的 CacheKeyGenerator 策略严格保持一致
 * 3. 支持精准失效（单条）和模糊失效（列表）
 *
 * @author arousal
 */
public class CacheKeyUtils {

    /* ========================= 常量定义 ========================= */

    /** 业务标识（统一前缀） */
    private static final String BIZ_PREFIX = "smartGalleryHub";

    /** 分隔符 */
    private static final String SEPARATOR = ":";

    /* ========================= 单条数据缓存 ========================= */

    /**
     * 单条图片详情缓存 Key（主键维度）
     *
     * 格式：smartGalleryHub:yu_picture:picture:id:{id}
     * 对应 Canal 策略：primaryKey("id", "smartGalleryHub:yu_picture:picture:id:%s")
     *
     * @param id 图片ID
     * @return 缓存Key
     */
    public static String getPictureVoByIdKey(Long id) {
        return buildKey("yu_picture", "picture", "id", String.valueOf(id));
    }

    /**
     * 单条用户详情缓存 Key（主键维度）
     *
     * 格式：smartGalleryHub:yu_picture:user:id:{id}
     *
     * @param id 用户ID
     * @return 缓存Key
     */
    public static String getUserByIdKey(Long id) {
        return buildKey("yu_picture", "user", "id", String.valueOf(id));
    }

    /**
     * 单条空间详情缓存 Key（主键维度）
     *
     * 格式：smartGalleryHub:yu_picture:space:id:{id}
     *
     * @param id 空间ID
     * @return 缓存Key
     */
    public static String getSpaceByIdKey(Long id) {
        return buildKey("yu_picture", "space", "id", String.valueOf(id));
    }

    /**
     * 单条空间用户关系缓存 Key（联合主键维度）
     *
     * 格式：smartGalleryHub:yu_picture:space_user:id:{id}
     *
     * @param id 关系ID
     * @return 缓存Key
     */
    public static String getSpaceUserByIdKey(Long id) {
        return buildKey("yu_picture", "space_user", "id", String.valueOf(id));
    }

    /* ========================= 唯一索引维度缓存 ========================= */

    /**
     * 用户账号维度缓存 Key
     *
     * 格式：smartGalleryHub:yu_picture:user:account:{account}
     * 对应 Canal 策略：uniqueIndex("userAccount", "smartGalleryHub:yu_picture:user:account:%s")
     *
     * @param userAccount 用户账号
     * @return 缓存Key
     */
    public static String getUserByAccountKey(String userAccount) {
        return buildKey("yu_picture", "user", "account", userAccount);
    }

    /**
     * 空间用户关系 - 按空间和用户查询
     *
     * 格式：smartGalleryHub:yu_picture:space_user:space:{spaceId}:user:{userId}
     * 对应 Canal 策略：compositeKey(["spaceId", "userId"], "smartGalleryHub:yu_picture:space_user:space:%s:user:%s")
     *
     * @param spaceId 空间ID
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String getSpaceUserByCompositeKey(Long spaceId, Long userId) {
        return String.format("%s%syu_picture%sspace_user%sspace:%s%suser:%s",
                BIZ_PREFIX, SEPARATOR, SEPARATOR, SEPARATOR, spaceId, SEPARATOR, userId);
    }

    /* ========================= 外键关联缓存（列表维度） ========================= */

    /**
     * 用户的所有图片列表缓存 Key 前缀（用于模糊清除）
     *
     * 格式：smartGalleryHub:yu_picture:picture:user:{userId}:*
     * 对应 Canal 策略：foreignKey("userId", "smartGalleryHub:yu_picture:picture:user:%s:*")
     *
     * @param userId 用户ID
     * @return 缓存Key模式（带*通配符）
     */
    public static String getUserPicturesPattern(Long userId) {
        return buildPattern("yu_picture", "picture", "user", String.valueOf(userId));
    }

    /**
     * 空间的图片列表缓存 Key 前缀（用于模糊清除）
     *
     * 格式：smartGalleryHub:yu_picture:picture:space:{spaceId}:*
     * 对应 Canal 策略：foreignKey("spaceId", "smartGalleryHub:yu_picture:picture:space:%s:*")
     *
     * @param spaceId 空间ID
     * @return 缓存Key模式（带*通配符）
     */
    public static String getSpacePicturesPattern(Long spaceId) {
        return buildPattern("yu_picture", "picture", "space", String.valueOf(spaceId));
    }

    /**
     * 用户的空间列表缓存 Key 前缀
     *
     * 格式：smartGalleryHub:yu_picture:space:user:{userId}:*
     *
     * @param userId 用户ID
     * @return 缓存Key模式
     */
    public static String getUserSpacesPattern(Long userId) {
        return buildPattern("yu_picture", "space", "user", String.valueOf(userId));
    }

    /* ========================= 分页列表缓存（已废弃/慎用） ========================= */

    /**
     * 【已废弃】分页图片列表缓存 Key
     *
     * 废弃原因：
     * 1. 分页参数组合多，缓存命中率低
     * 2. Canal 难以精准失效（参数变化导致 Key 不可预测）
     * 3. 建议：列表查询走数据库 + ES，只缓存单条热点数据
     *
     * 如必须使用，建议：
     * 1. 缩短 TTL 至 10-30 秒
     * 2. 简化查询条件（只缓存最热的几种组合）
     * 3. 使用 Canal 的 pattern 清除：smartGalleryHub:listPictureVOByPage:*
     *
     * @param pictureQueryRequest 查询请求
     * @return 缓存Key
     */
    @Deprecated
    public static String listPictureByPageVoKey(PictureQueryRequest pictureQueryRequest) {
        // 强制设置审核状态，确保一致性
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 构建查询条件签名
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());

        // 旧格式保留兼容（建议逐步迁移到新格式）
        return String.format("smartGalleryHub:listPictureVOByPage:%s", hashKey);
    }

    /**
     * 新的分页列表 Key 格式（如必须使用）
     * 格式：smartGalleryHub:yu_picture:picture:list:{hash}
     * Canal 清除模式：smartGalleryHub:yu_picture:picture:list:*
     *
     * @param pictureQueryRequest 查询请求
     * @return 缓存Key
     */
    public static String listPictureByPageVoKeyNew(PictureQueryRequest pictureQueryRequest) {
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 只保留核心参数构建 Key，提高命中率
        String coreParams = String.format("spaceId=%s&current=%s&pageSize=%s",
                pictureQueryRequest.getSpaceId(),
                pictureQueryRequest.getCurrent(),
                pictureQueryRequest.getPageSize());

        String hashKey = DigestUtils.md5DigestAsHex(coreParams.getBytes());
        return buildKey("yu_picture", "picture", "list", hashKey);
    }

    /* ========================= 通用列表模式（Canal 清除用） ========================= */

    /**
     * 所有图片列表缓存的模糊匹配模式
     *
     * 用于 Canal 清除所有列表缓存
     * 格式：smartGalleryHub:yu_picture:picture:list:*
     *
     * @return 模糊匹配模式
     */
    public static String getAllPictureListPattern() {
        return buildPattern("yu_picture", "picture", "list", "*");
    }

    /**
     * 所有空间列表缓存的模糊匹配模式
     *
     * @return 模糊匹配模式
     */
    public static String getAllSpaceListPattern() {
        return buildPattern("yu_picture", "space", "list", "*");
    }

    /* ========================= 私有工具方法 ========================= */

    /**
     * 构建标准缓存 Key
     * 格式：biz:database:table:dimension:value
     */
    private static String buildKey(String database, String table, String dimension, String value) {
        return String.format("%s%s%s%s%s%s%s%s%s",
                BIZ_PREFIX, SEPARATOR,
                database, SEPARATOR,
                table, SEPARATOR,
                dimension, SEPARATOR,
                value);
    }

    /**
     * 构建模糊匹配模式（用于批量清除）
     * 格式：biz:database:table:dimension:value:*
     */
    private static String buildPattern(String database, String table, String dimension, String value) {
        return String.format("%s%s%s%s%s%s%s%s%s*",
                BIZ_PREFIX, SEPARATOR,
                database, SEPARATOR,
                table, SEPARATOR,
                dimension, SEPARATOR,
                value);
    }

    /* ========================= 兼容旧格式（迁移期使用） ========================= */

    /**
     * 获取旧格式的 Key 模式（用于 Canal 兼容清除）
     * 返回：smartGalleryHub:listPictureVOByPage:*
     */
    @Deprecated
    public static String getLegacyListPattern() {
        return "smartGalleryHub:listPictureVOByPage:*";
    }
}