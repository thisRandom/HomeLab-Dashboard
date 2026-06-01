package com.homelab.dashboard_backend.mapper;

import lombok.Data;
import lombok.Builder;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SystemConfigMapper {

    @Select("SELECT id, config_key, config_value, value_type, updated_at FROM system_config")
    List<SystemConfig> selectAll();

    @Select("SELECT config_value FROM system_config WHERE config_key = #{key}")
    String selectValueByKey(String key);

    @Insert("INSERT INTO system_config (config_key, config_value, value_type) " +
            "VALUES (#{configKey}, #{configValue}, #{valueType}) " +
            "ON DUPLICATE KEY UPDATE config_value = #{configValue}, value_type = #{valueType}")
    void upsert(SystemConfig config);

    @Delete("DELETE FROM system_config WHERE config_key = #{key}")
    void deleteByKey(String key);

    @Data
    @Builder
    class SystemConfig {
        private int id;
        private String configKey;
        private String configValue;
        private String valueType;
        private String updatedAt;
    }
}
