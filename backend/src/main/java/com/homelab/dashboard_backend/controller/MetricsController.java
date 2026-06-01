package com.homelab.dashboard_backend.controller;

import com.homelab.dashboard_backend.plugin.CollectorPlugin;
import com.homelab.dashboard_backend.plugin.CollectedMetrics;
import com.homelab.dashboard_backend.service.PluginService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/metrics")
public class MetricsController {

    private final PluginService pluginService;

    public MetricsController(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    @GetMapping("/realtime")
    public Map<String, CollectedMetrics> realtime() {
        return collectAll(CollectorPlugin::collectRealtime);
    }

    @GetMapping("/frequent")
    public Map<String, CollectedMetrics> frequent() {
        return collectAll(CollectorPlugin::collectFrequent);
    }

    @GetMapping("/slow")
    public Map<String, CollectedMetrics> slow() {
        return collectAll(CollectorPlugin::collectSlow);
    }

    @PostMapping("/{pluginId}/reload")
    public String reload(@PathVariable String pluginId) {
        pluginService.reloadPlugin(pluginId);
        return "ok";
    }

    private Map<String, CollectedMetrics> collectAll(CollectorMethod method) {
        List<CollectorPlugin> enabledPlugins = pluginService.getEnabledPlugins();
        Map<String, CollectedMetrics> result = new LinkedHashMap<>();
        for (CollectorPlugin plugin : enabledPlugins) {
            try {
                result.put(plugin.getPluginId(), method.collect(plugin));
            } catch (Exception e) {
                result.put(plugin.getPluginId(), CollectedMetrics.builder()
                        .pluginId(plugin.getPluginId())
                        .metrics(Map.of())
                        .build());
            }
        }
        return result;
    }

    @FunctionalInterface
    interface CollectorMethod {
        CollectedMetrics collect(CollectorPlugin plugin);
    }
}
