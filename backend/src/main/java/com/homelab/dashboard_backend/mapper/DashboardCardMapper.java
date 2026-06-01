package com.homelab.dashboard_backend.mapper;

import lombok.Data;
import lombok.Builder;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DashboardCardMapper {

    @Select("SELECT id, active_template, card_type, title, position_x, position_y, enabled, card_config, sort_order " +
            "FROM dashboard_card WHERE active_template = #{template} ORDER BY sort_order")
    List<DashboardCard> selectByTemplate(String template);

    @Insert("INSERT INTO dashboard_card (active_template, card_type, title, position_x, position_y, enabled, sort_order) " +
            "VALUES (#{activeTemplate}, #{cardType}, #{title}, #{positionX}, #{positionY}, #{enabled}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DashboardCard card);

    @Update("UPDATE dashboard_card SET position_x = #{positionX}, position_y = #{positionY}, sort_order = #{sortOrder} " +
            "WHERE id = #{id}")
    void updatePosition(DashboardCard card);

    @Delete("DELETE FROM dashboard_card WHERE id = #{id}")
    void deleteById(int id);

    @Delete("DELETE FROM dashboard_card WHERE active_template = #{template}")
    void deleteByTemplate(String template);

    @Data
    @Builder
    class DashboardCard {
        private int id;
        private String activeTemplate;
        private String cardType;
        private String title;
        private int positionX;
        private int positionY;
        private boolean enabled;
        private String cardConfig;
        private int sortOrder;
    }
}
