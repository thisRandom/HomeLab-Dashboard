package com.homelab.dashboard_backend.controller;

import com.homelab.dashboard_backend.mapper.WallpaperMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/wallpapers")
public class WallpaperController {

    private final WallpaperMapper wallpaperMapper;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private Path uploadPath;

    public WallpaperController(WallpaperMapper wallpaperMapper) {
        this.wallpaperMapper = wallpaperMapper;
    }

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir, "backgrounds").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @GetMapping
    public List<WallpaperMapper.Wallpaper> getAll() {
        return wallpaperMapper.selectAll();
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件为空");
                return result;
            }

            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String filename = UUID.randomUUID() + ext;
            Path filePath = uploadPath.resolve(filename);
            file.transferTo(filePath.toFile());

            String url = "/api/config/image/" + filename;

            // Check if this is the first wallpaper
            List<WallpaperMapper.Wallpaper> existing = wallpaperMapper.selectAll();
            int isActive = existing.isEmpty() ? 1 : 0;

            wallpaperMapper.insert(WallpaperMapper.Wallpaper.builder()
                    .filename(filename)
                    .originalName(originalFilename)
                    .url(url)
                    .fileSize(file.getSize())
                    .sortOrder(existing.size())
                    .isActive(isActive == 1)
                    .build());

            // If first wallpaper, also save to system_config
            if (isActive == 1) {
                // Will be saved by the frontend when selecting
            }

            result.put("success", true);
            result.put("url", url);
            result.put("filename", filename);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable int id) {
        Map<String, Object> result = new HashMap<>();
        try {
            String filename = wallpaperMapper.selectFilenameById(id);
            if (filename != null) {
                // Delete file from disk
                Path filePath = uploadPath.resolve(filename);
                Files.deleteIfExists(filePath);
            }
            wallpaperMapper.deleteById(id);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    @PutMapping("/{id}/active")
    public Map<String, Object> setActive(@PathVariable int id) {
        Map<String, Object> result = new HashMap<>();
        try {
            wallpaperMapper.clearActive();
            wallpaperMapper.setActive(id);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "设置失败: " + e.getMessage());
        }
        return result;
    }
}
