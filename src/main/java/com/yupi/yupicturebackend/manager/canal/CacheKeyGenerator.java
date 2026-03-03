package com.yupi.yupicturebackend.manager.canal;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 根据库表和行数据生成需要失效的缓存Key
 */
@Component
public class CacheKeyGenerator {

    /**
     * 表级别的缓存策略配置
     */
    private static final Map<String, TableCacheStrategy> STRATEGY_MAP = new HashMap<>();

    private static final String BIZ_PREFIX = "smartGalleryHub";

    public CacheKeyGenerator() {
        // ==================== picture 表 ====================
        STRATEGY_MAP.put("yu_picture.picture", new TableCacheStrategy()
                // 主键：smartGalleryHub:yu_picture:picture:id:{id}
                .addPrimaryKey("id", BIZ_PREFIX + ":yu_picture:picture:id:%s")
                // 用户维度列表：smartGalleryHub:yu_picture:picture:user:{userId}:*
                .addForeignKey("userId", BIZ_PREFIX + ":yu_picture:picture:user:%s:*")
                // 空间维度列表：smartGalleryHub:yu_picture:picture:space:{spaceId}:*
                .addForeignKey("spaceId", BIZ_PREFIX + ":yu_picture:picture:space:%s:*")
                // 列表缓存模式：smartGalleryHub:yu_picture:picture:list:*
                .addPattern(BIZ_PREFIX + ":yu_picture:picture:list:*")
                // 兼容旧格式：smartGalleryHub:listPictureVOByPage:*
                .addPattern(BIZ_PREFIX + ":listPictureVOByPage:*")
        );

        // ==================== user 表 ====================
        STRATEGY_MAP.put("yu_picture.user", new TableCacheStrategy()
                // 主键：smartGalleryHub:yu_picture:user:id:{id}
                .addPrimaryKey("id", BIZ_PREFIX + ":yu_picture:user:id:%s")
                // 账号维度：smartGalleryHub:yu_picture:user:account:{account}
                .addUniqueIndex("userAccount", BIZ_PREFIX + ":yu_picture:user:account:%s")
                // 用户列表模式：smartGalleryHub:yu_picture:user:list:*
                .addPattern(BIZ_PREFIX + ":yu_picture:user:list:*")
        );

        // ==================== space 表 ====================
        STRATEGY_MAP.put("yu_picture.space", new TableCacheStrategy()
                // 主键：smartGalleryHub:yu_picture:space:id:{id}
                .addPrimaryKey("id", BIZ_PREFIX + ":yu_picture:space:id:%s")
                // 用户维度列表：smartGalleryHub:yu_picture:space:user:{userId}:*
                .addForeignKey("userId", BIZ_PREFIX + ":yu_picture:space:user:%s:*")
                .addPattern(BIZ_PREFIX + ":yu_picture:space:list:*")
        );

        // ==================== space_user 表 ====================
        STRATEGY_MAP.put("yu_picture.space_user", new TableCacheStrategy()
                // 主键：smartGalleryHub:yu_picture:space_user:id:{id}
                .addPrimaryKey("id", BIZ_PREFIX + ":yu_picture:space_user:id:%s")
                // 联合维度：smartGalleryHub:yu_picture:space_user:space:{spaceId}:user:{userId}
                .addCompositeKey(Arrays.asList("spaceId", "userId"),
                        BIZ_PREFIX + ":yu_picture:space_user:space:%s:user:%s")
                .addPattern(BIZ_PREFIX + ":yu_picture:space_user:list:*")
        );
    }

    /**
     * 生成需要失效的缓存Key集合
     */
    public Set<String> generateKeys(String database, String table, 
                                    String operation,
                                    Map<String, Object> newData, 
                                    Map<String, Object> oldData) {
        
        String fullTableName = database + "." + table;
        TableCacheStrategy strategy = STRATEGY_MAP.get(fullTableName);
        
        if (strategy == null) {
            // 未配置策略，只清除主键缓存
            return generateDefaultKeys(database, table, newData, oldData);
        }

        Set<String> keys = new HashSet<>();
        
        // 根据操作类型选择数据源
        Map<String, Object> dataToUse = "DELETE".equals(operation) ? oldData : newData;
        
        // 1. 主键缓存
        for (IndexConfig pk : strategy.getPrimaryKeys()) {
            String key = generateKey(pk.getPattern(), dataToUse, pk.getFields());
            if (key != null) keys.add(key);
        }
        
        // 2. 唯一索引缓存
        for (IndexConfig uk : strategy.getUniqueKeys()) {
            String key = generateKey(uk.getPattern(), dataToUse, uk.getFields());
            if (key != null) keys.add(key);
        }
        
        // 3. 外键关联缓存（如用户的所有图片列表）
        for (IndexConfig fk : strategy.getForeignKeys()) {
            String key = generateKey(fk.getPattern(), dataToUse, fk.getFields());
            if (key != null) keys.add(key);
        }
        
        // 4. UPDATE操作：如果关联字段变更，需要清除旧关联缓存
        if ("UPDATE".equals(operation) && oldData != null) {
            for (IndexConfig fk : strategy.getForeignKeys()) {
                String oldKey = generateKey(fk.getPattern(), oldData, fk.getFields());
                String newKey = generateKey(fk.getPattern(), dataToUse, fk.getFields());
                if (oldKey != null && !oldKey.equals(newKey)) {
                    keys.add(oldKey);  // 清除旧关联
                }
            }
        }
        
        // 5. 列表模式缓存（模糊清除）
        keys.addAll(strategy.getPatterns());
        
        return keys;
    }

    private String generateKey(String pattern, Map<String, Object> data, List<String> fields) {
        try {
            Object[] values = fields.stream()
                    .map(data::get)
                    .filter(Objects::nonNull)
                    .toArray();
            
            if (values.length != fields.size()) {
                return null;  // 字段缺失
            }
            
            return String.format(pattern, values);
        } catch (Exception e) {
            return null;
        }
    }

    private Set<String> generateDefaultKeys(String database, String table, 
                                           Map<String, Object> newData, 
                                           Map<String, Object> oldData) {
        Set<String> keys = new HashSet<>();
        
        // 尝试使用 id 作为主键
        Object id = newData != null ? newData.get("id") : 
                   (oldData != null ? oldData.get("id") : null);
        
        if (id != null) {
            keys.add(String.format("%s:%s:id:%s", database, table, id));
        }
        
        // 清除列表缓存
        keys.add(String.format("%s:%s:list:*", database, table));
        
        return keys;
    }

    /**
     * 表级缓存策略
     */
    @Data
    private static class TableCacheStrategy {
        private List<IndexConfig> primaryKeys = new ArrayList<>();
        private List<IndexConfig> uniqueKeys = new ArrayList<>();
        private List<IndexConfig> foreignKeys = new ArrayList<>();
        private Set<String> patterns = new HashSet<>();

        public TableCacheStrategy addPrimaryKey(String field, String pattern) {
            primaryKeys.add(new IndexConfig(Collections.singletonList(field), pattern));
            return this;
        }

        public TableCacheStrategy addUniqueIndex(String field, String pattern) {
            uniqueKeys.add(new IndexConfig(Collections.singletonList(field), pattern));
            return this;
        }

        public TableCacheStrategy addCompositeKey(List<String> fields, String pattern) {
            uniqueKeys.add(new IndexConfig(fields, pattern));
            return this;
        }

        public TableCacheStrategy addForeignKey(String field, String pattern) {
            foreignKeys.add(new IndexConfig(Collections.singletonList(field), pattern));
            return this;
        }

        public TableCacheStrategy addPattern(String pattern) {
            patterns.add(pattern);
            return this;
        }
    }

    @Data
    @AllArgsConstructor
    private static class IndexConfig {
        private List<String> fields;
        private String pattern;
    }
}