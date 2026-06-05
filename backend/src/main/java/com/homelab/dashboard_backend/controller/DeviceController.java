package com.homelab.dashboard_backend.controller;

import com.homelab.dashboard_backend.plugin.ikuai.IkuaiPlugin;
import com.homelab.dashboard_backend.service.PluginService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final PluginService pluginService;

    public DeviceController(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    @GetMapping
    public List<Map<String, Object>> listDevices() {
        IkuaiPlugin plugin = getIkuaiPlugin();
        if (plugin == null) return List.of();
        return plugin.fetchDeviceDetails();
    }

    private IkuaiPlugin getIkuaiPlugin() {
        var plugin = pluginService.getPlugin("ikuai");
        if (plugin instanceof IkuaiPlugin ikuai) {
            return ikuai;
        }
        return null;
    }
}
