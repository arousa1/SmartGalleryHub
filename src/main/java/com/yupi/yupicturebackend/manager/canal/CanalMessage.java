package com.yupi.yupicturebackend.manager.canal;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Canal 消息结构
 */
@Data
public class CanalMessage {
    
    /** 数据库名 */
    private String database;
    
    /** 表名 */
    private String table;
    
    /** 操作类型：INSERT/UPDATE/DELETE */
    private String type;
    
    /** 变更时间戳（秒） */
    private Long ts;
    
    /** 执行的SQL（可能为空） */
    private String sql;
    
    /** 变更后的数据（INSERT/UPDATE） */
    private List<Map<String, Object>> data;
    
    /** 变更前的数据（UPDATE/DELETE） */
    private List<Map<String, Object>> old;
    
    /** 主键字段名列表 */
    private List<String> pkNames;
    
    /** 是否为DDL语句 */
    private Boolean isDdl;
}