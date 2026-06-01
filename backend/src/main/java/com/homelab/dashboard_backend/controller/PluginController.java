package com.homelab.dashboard_backend.controller;

import com.homelab.dashboard_backend.mapper.PluginConfigMapper;
import com.homelab.dashboard_backend.mapper.PluginMapper;
import com.homelab.dashboard_backend.service.PluginService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/plugins")
public class PluginController {

    private final PluginMapper pluginMapper;
    private final PluginConfigMapper pluginConfigMapper;
    private final PluginService pluginService;

    public PluginController(PluginMapper pluginMapper, PluginConfigMapper pluginConfigMapper, PluginService pluginService) {
        this.pluginMapper = pluginMapper;
        this.pluginConfigMapper = pluginConfigMapper;
        this.pluginService = pluginService;
    }

    @GetMapping
    public List<PluginMapper.PluginRecord> list() {
        return pluginMapper.selectAll();
    }

    @GetMapping("/{pluginId}/config")
    public Map<String, String> getConfig(@PathVariable String pluginId) {
        List<Map<String, String>> rows = pluginConfigMapper.selectByPluginId(pluginId);
        Map<String, String> config = new HashMap<>();
        for (Map<String, String> row : rows) {
            config.put(row.get("config_key"), row.get("config_value"));
        }
        return config;
    }

    @PutMapping("/{pluginId}/config")
    public Map<String, Object> saveConfig(@PathVariable String pluginId, @RequestBody Map<String, String> config) {
        boolean ok = pluginService.testConnectionAndSave(pluginId, config);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "连接校验成功，配置已保存" : "连接校验失败，请检查地址和账号密码");
        return result;
    }

    @PostMapping("/{pluginId}/enable")
    public Map<String, Object> toggleEnable(@PathVariable String pluginId, @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", false);
        Map<String, Object> result = new HashMap<>();

        if (enabled) {
            // Enabling — use testSaveEnable with existing config from DB
            boolean ok = pluginService.enablePlugin(pluginId);
            result.put("success", ok);
            result.put("message", ok ? "插件已启用" : "启用失败：配置无效或连接校验不通过");
        } else {
            // Disabling — just update DB
            pluginMapper.setEnabled(pluginId, false);
            pluginService.reloadPlugin(pluginId);
            result.put("success", true);
            result.put("message", "插件已禁用");
        }
        return result;
    }

    @PostMapping("/{pluginId}/test-save-enable")
    public Map<String, Object> testSaveEnable(@PathVariable String pluginId, @RequestBody Map<String, String> config) {
        boolean ok = pluginService.testSaveEnable(pluginId, config);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "连接成功，插件已启用" : "连接失败，请检查地址和账号密码");
        return result;
    }
}
