package com.yupi.yupicturebackend.manager;

import cn.hutool.core.lang.TypeReference;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.manager.cache.CacheChainTemplate;
import com.yupi.yupicturebackend.manager.cache.EnhancedCacheChainTemplate;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.utils.CacheKeyUtils;
import lombok.Data;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

/**
 * 多级缓存管理类
 */
@Component
@Data
public class CacheManager {

    private static final TypeReference<? extends PictureVO> TYPE_PICTURE_VO = new TypeReference<PictureVO>() {};

    private static final TypeReference<? extends Page<PictureVO>> TYPE_PICTURE_VO_PAGE = new TypeReference<Page<PictureVO>>() {};

    @Resource(name = "localCache")
    private EnhancedCacheChainTemplate cacheChainTemplate;

    public PictureVO getPictureVoById(Long id) {
        return cacheChainTemplate.getObjectValueForString(
                CacheKeyUtils.getPictureVoByIdKey(id), TYPE_PICTURE_VO);
    }

    public Page<PictureVO> listPictureVoByPage(PictureQueryRequest pictureQueryRequest) {
        return cacheChainTemplate.getObjectValueForString(
                CacheKeyUtils.listPictureByPageVoKey(pictureQueryRequest),
                TYPE_PICTURE_VO_PAGE);
    }

    public void setCacheForValue(String key, String value) {
        cacheChainTemplate.setStringValueChain(key, value);
    }

    public Set<String> getKeys() {
        return cacheChainTemplate.getKeysChain();
    }

    public String getValueForString(String key) {
        return cacheChainTemplate.getStringValueChain(key);
    }

    public void deleteCacheByKey(String key) {
        cacheChainTemplate.deleteValueChain(key);
    }

    public void deleteCacheByKeys(List<String> keys) {
        cacheChainTemplate.deleteValuesChain(keys);
    }
}
