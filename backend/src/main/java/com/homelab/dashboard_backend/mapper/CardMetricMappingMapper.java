package com.homelab.dashboard_backend.mapper;

import lombok.Data;
import lombok.Builder;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CardMetricMappingMapper {

    /**
     * 查询指定卡片类型需要的所有指标
     */
    @Select("SELECT card_type, metric_key, poll_freq, plugin_id " +
            "FROM card_metric_mapping WHERE card_type = #{cardType}")
    List<CardMetricMapping> selectByCardType(String cardType);

    /**
     * 批量查询多种卡片类型需要的所有指标（去重）
     */
    @Select("<script>" +
            "SELECT DISTINCT card_type, metric_key, poll_freq, plugin_id " +
            "FROM card_metric_mapping WHERE card_type IN " +
            "<foreach collection='cardTypes' item='ct' open='(' separator=',' close=')'>" +
            "#{ct}" +
            "</foreach>" +
            "</script>")
    List<CardMetricMapping> selectByCardTypes(@Param("cardTypes") List<String> cardTypes);

    /**
     * 查询指定插件的所有指标映射
     */
    @Select("SELECT card_type, metric_key, poll_freq, plugin_id " +
            "FROM card_metric_mapping WHERE plugin_id = #{pluginId}")
    List<CardMetricMapping> selectByPluginId(String pluginId);

    @Data
    @Builder
    class CardMetricMapping {
        private String cardType;
        private String metricKey;
        private String pollFreq;
        private String pluginId;
    }
}
