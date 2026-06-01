package com.homelab.dashboard_backend.mapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PluginMapper {

    @Select("SELECT plugin_id, display_name, description, icon, version, enabled, status FROM plugin")
    @Results(id = "pluginResult", value = {
        @Result(property = "pluginId", column = "plugin_id"),
        @Result(property = "displayName", column = "display_name")
    })
    List<PluginRecord> selectAll();

    @Select("SELECT plugin_id, display_name, enabled, status FROM plugin WHERE plugin_id = #{pluginId}")
    @ResultMap("pluginResult")
    PluginRecord selectByPluginId(String pluginId);

    @Insert("INSERT INTO plugin (plugin_id, display_name, description, icon, version, enabled, status) " +
            "VALUES (#{pluginId}, #{displayName}, #{description}, #{icon}, #{version}, #{enabled}, #{status}) " +
            "ON DUPLICATE KEY UPDATE display_name = #{displayName}, description = #{description}")
    int upsert(PluginRecord record);

    @Update("UPDATE plugin SET enabled = #{enabled} WHERE plugin_id = #{pluginId}")
    int setEnabled(@Param("pluginId") String pluginId, @Param("enabled") boolean enabled);

    @Update("UPDATE plugin SET status = #{status}, last_health_check = NOW() WHERE plugin_id = #{pluginId}")
    int setStatus(@Param("pluginId") String pluginId, @Param("status") String status);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PluginRecord {
        private String pluginId;
        private String displayName;
        private String description;
        private String icon;
        private String version;
        private boolean enabled;
        private String status;
    }
}
