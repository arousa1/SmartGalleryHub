package com.yupi.yupicturebackend.manager;

import cn.hutool.core.lang.TypeReference;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.manager.cache.EnhancedCacheChainTemplate;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.utils.CacheKeyUtils;
import lombok.Data;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;

/**
 * 多级缓存管理类 - 查询专用（写入/删除由Canal异步处理）
 */
@Component
@Data
public class CacheManager {

    private static final TypeReference<PictureVO> TYPE_PICTURE_VO = new TypeReference<PictureVO>() {};

    private static final TypeReference<Page<PictureVO>> TYPE_PICTURE_VO_PAGE = new TypeReference<Page<PictureVO>>() {};

    @Resource(name = "localCache")
    private EnhancedCacheChainTemplate cacheChainTemplate;

    /**
     * 查询单条图片缓存（Canal会自动失效）
     */
    public PictureVO getPictureVoById(Long id) {
        return cacheChainTemplate.getObjectValueForString(
                CacheKeyUtils.getPictureVoByIdKey(id), TYPE_PICTURE_VO);
    }

    /**
     * 查询分页图片缓存（Canal会自动失效）
     */
    public Page<PictureVO> listPictureVoByPage(PictureQueryRequest pictureQueryRequest) {
        return cacheChainTemplate.getObjectValueForString(
                CacheKeyUtils.listPictureByPageVoKey(pictureQueryRequest),
                TYPE_PICTURE_VO_PAGE);
    }

    /**
     * 写入缓存（仅用于查询后回填，不主动更新）
     */
    public void setCacheForValue(String key, String value) {
        cacheChainTemplate.setStringValueChain(key, value);
    }

    /**
     * 获取缓存值（原始字符串）
     */
    public String getValueForString(String key) {
        return cacheChainTemplate.getStringValueChain(key);
    }

    /**
     * 获取所有缓存Key（运维巡检用）
     */
    public Set<String> getKeys() {
        return cacheChainTemplate.getKeysChain();
    }

    // ============================================
    // 删除方法已移除！由Canal监听Binlog自动处理
    // ============================================
}