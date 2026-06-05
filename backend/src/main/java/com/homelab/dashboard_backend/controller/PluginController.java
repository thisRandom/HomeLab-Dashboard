package com.homelab.dashboard_backend.controller;

import com.homelab.dashboard_backend.mapper.PluginConfigMapper;
import com.homelab.dashboard_backend.mapper.PluginMapper;
import com.homelab.dashboard_backend.plugin.CollectorPlugin;
import com.homelab.dashboard_backend.plugin.ConfigField;
import com.homelab.dashboard_backend.plugin.pve.PveClient;
import com.homelab.dashboard_backend.service.PluginService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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

    @GetMapping("/{pluginId}/schema")
    public List<Map<String, Object>> getSchema(@PathVariable String pluginId) {
        CollectorPlugin plugin = pluginService.getPlugin(pluginId);
        if (plugin == null) {
            // 尝试创建一个临时实例获取 schema
            plugin = pluginService.createPluginWithoutLoginForSchema(pluginId);
        }
        if (plugin == null) {
            return List.of();
        }
        List<ConfigField> fields = plugin.getConfigSchema();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ConfigField f : fields) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", f.getName());
            map.put("label", f.getLabel());
            map.put("type", f.getType().name().toLowerCase());
            map.put("required", f.isRequired());
            if (f.getDefaultValue() != null) map.put("defaultValue", f.getDefaultValue());
            if (f.getPlaceholder() != null) map.put("placeholder", f.getPlaceholder());
            result.add(map);
        }
        return result;
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

    @PostMapping("/{pluginId}/test-temperature")
    public Map<String, Object> testTemperature(@PathVariable String pluginId, @RequestBody Map<String, String> config) {
        Map<String, Object> result = new HashMap<>();

        if (!"pve".equals(pluginId)) {
            result.put("success", false);
            result.put("message", "仅支持 PVE 插件");
            return result;
        }

        try {
            String sshHost = config.getOrDefault("ssh_host", config.get("host"));
            int sshPort = 22;
            try { sshPort = Integer.parseInt(config.getOrDefault("ssh_port", "22")); } catch (NumberFormatException ignored) {}
            String sshUser = config.getOrDefault("ssh_user", "root");
            String sshPassword = config.get("ssh_password");
            String sshKeyPath = config.get("ssh_key_path");

            PveClient testClient = new PveClient(
                    config.getOrDefault("host", "192.168.10.254"),
                    8006,
                    config.getOrDefault("node", "pve"),
                    "",
                    sshHost, sshPort, sshUser, sshPassword, sshKeyPath
            );

            String tempOutput = testClient.sshExecute("sensors 2>/dev/null | grep -E 'Core 0|temp1' | head -1");
            if (tempOutput != null && !tempOutput.trim().isEmpty()) {
                result.put("success", true);
                result.put("message", "温度读取成功");
                result.put("temperature", tempOutput.trim());
            } else {
                result.put("success", false);
                result.put("message", "SSH 连接成功但无法读取温度，请确认已安装 lm-sensors");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }

        return result;
    }
}
