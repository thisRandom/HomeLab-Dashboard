package com.homelab.dashboard_backend.mapper;

import lombok.Data;
import lombok.Builder;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WallpaperMapper {

    @Select("SELECT id, filename, original_name, url, file_size, sort_order, is_active, created_at " +
            "FROM wallpaper ORDER BY sort_order, id")
    @Results(id = "wallpaperResult", value = {
        @Result(property = "originalName", column = "original_name"),
        @Result(property = "fileSize", column = "file_size"),
        @Result(property = "sortOrder", column = "sort_order"),
        @Result(property = "isActive", column = "is_active"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<Wallpaper> selectAll();

    @Select("SELECT id, filename, original_name, url, file_size, sort_order, is_active, created_at " +
            "FROM wallpaper WHERE is_active = 1 LIMIT 1")
    @ResultMap("wallpaperResult")
    Wallpaper selectActive();

    @Insert("INSERT INTO wallpaper (filename, original_name, url, file_size, sort_order, is_active) " +
            "VALUES (#{filename}, #{originalName}, #{url}, #{fileSize}, #{sortOrder}, #{isActive})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Wallpaper wallpaper);

    @Delete("DELETE FROM wallpaper WHERE id = #{id}")
    void deleteById(int id);

    @Update("UPDATE wallpaper SET is_active = 0")
    void clearActive();

    @Update("UPDATE wallpaper SET is_active = 1 WHERE id = #{id}")
    void setActive(int id);

    @Select("SELECT filename FROM wallpaper WHERE id = #{id}")
    String selectFilenameById(int id);

    @Data
    @Builder
    class Wallpaper {
        private int id;
        private String filename;
        private String originalName;
        private String url;
        private long fileSize;
        private int sortOrder;
        private boolean isActive;
        private String createdAt;
    }
}
