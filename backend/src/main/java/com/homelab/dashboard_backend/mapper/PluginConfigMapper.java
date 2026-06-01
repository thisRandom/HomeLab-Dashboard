package com.homelab.dashboard_backend.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface PluginConfigMapper {

    @Select("SELECT config_key, config_value FROM plugin_config WHERE plugin_id = #{pluginId}")
    List<Map<String, String>> selectByPluginId(String pluginId);

    @Insert("INSERT INTO plugin_config (plugin_id, config_key, config_value) " +
            "VALUES (#{pluginId}, #{configKey}, #{configValue}) " +
            "ON DUPLICATE KEY UPDATE config_value = #{configValue}")
    int upsert(@Param("pluginId") String pluginId, @Param("configKey") String configKey, @Param("configValue") String configValue);

    @Delete("DELETE FROM plugin_config WHERE plugin_id = #{pluginId}")
    int deleteByPluginId(String pluginId);
}
