package com.yupi.yupicturebackend.manager.canal;

import lombok.Data;
import java.io.Serializable;

/**
 * 缓存失效广播消息
 */
@Data
public class CacheEvictBroadcast implements Serializable {
    
    /** 需要失效的缓存Key */
    private String key;
    
    /** 发送消息的实例标识 */
    private String sourceInstance;
    
    /** 发送时间戳 */
    private Long timestamp;
}