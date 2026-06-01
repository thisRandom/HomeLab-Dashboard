package com.homelab.dashboard_backend.plugin.ikuai;

import com.homelab.dashboard_backend.plugin.CollectorPlugin;
import com.homelab.dashboard_backend.service.PluginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PluginRunner implements CommandLineRunner {

    private final PluginService pluginService;

    public PluginRunner(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    @Override
    public void run(String... args) {
        List<CollectorPlugin> enabledPlugins = pluginService.getEnabledPlugins();
        log.info("Found {} enabled plugins", enabledPlugins.size());

        for (CollectorPlugin plugin : enabledPlugins) {
            if (plugin.isHealthy()) {
                log.info("Plugin [{}] initialized successfully", plugin.getPluginId());
            } else {
                log.warn("Plugin [{}] is not healthy", plugin.getPluginId());
            }
        }
    }
}
