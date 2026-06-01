package com.homelab.dashboard_backend.controller;

import com.homelab.dashboard_backend.mapper.SystemConfigMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/config")
public class SystemConfigController {

    private final SystemConfigMapper configMapper;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private Path uploadPath;

    public SystemConfigController(SystemConfigMapper configMapper) {
        this.configMapper = configMapper;
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
    public List<SystemConfigMapper.SystemConfig> getAll() {
        return configMapper.selectAll();
    }

    @GetMapping("/{key}")
    public Map<String, Object> getByKey(@PathVariable String key) {
        String value = configMapper.selectValueByKey(key);
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("value", value);
        return result;
    }

    @PutMapping
    public String save(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        String valueType = body.getOrDefault("valueType", "string");

        if (key == null || value == null) {
            return "error";
        }

        configMapper.upsert(SystemConfigMapper.SystemConfig.builder()
                .configKey(key)
                .configValue(value)
                .valueType(valueType)
                .build());
        return "ok";
    }

    @PutMapping("/batch")
    public String saveBatch(@RequestBody List<Map<String, String>> configs) {
        for (Map<String, String> item : configs) {
            String key = item.get("key");
            String value = item.get("value");
            String valueType = item.getOrDefault("valueType", "string");
            if (key != null && value != null) {
                configMapper.upsert(SystemConfigMapper.SystemConfig.builder()
                        .configKey(key)
                        .configValue(value)
                        .valueType(valueType)
                        .build());
            }
        }
        return "ok";
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) {
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
            result.put("success", true);
            result.put("url", url);
            result.put("filename", filename);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/image/{filename}")
    public org.springframework.http.ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        try {
            Path filePath = uploadPath.resolve(filename);
            byte[] imageBytes = Files.readAllBytes(filePath);
            String contentType = "image/jpeg";
            if (filename.endsWith(".png")) {
                contentType = "image/png";
            } else if (filename.endsWith(".webp")) {
                contentType = "image/webp";
            }
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Cache-Control", "max-age=31536000")
                    .body(imageBytes);
        } catch (IOException e) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }
}
