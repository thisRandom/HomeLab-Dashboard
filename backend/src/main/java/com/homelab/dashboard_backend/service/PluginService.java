package com.homelab.dashboard_backend.service;

import com.homelab.dashboard_backend.mapper.PluginConfigMapper;
import com.homelab.dashboard_backend.mapper.PluginMapper;
import com.homelab.dashboard_backend.plugin.CollectorPlugin;
import com.homelab.dashboard_backend.plugin.ikuai.IkuaiPlugin;
import com.homelab.dashboard_backend.plugin.pve.PvePlugin;
import com.homelab.dashboard_backend.plugin.storeos.StoreosPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PluginService {

    private final PluginMapper pluginMapper;
    private final PluginConfigMapper pluginConfigMapper;
    private final Map<String, CollectorPlugin> plugins = new ConcurrentHashMap<>();

    public PluginService(PluginMapper pluginMapper, PluginConfigMapper pluginConfigMapper) {
        this.pluginMapper = pluginMapper;
        this.pluginConfigMapper = pluginConfigMapper;
    }

    public CollectorPlugin getPlugin(String pluginId) {
        return plugins.get(pluginId);
    }

    public List<CollectorPlugin> getEnabledPlugins() {
        List<CollectorPlugin> result = new ArrayList<>();

        for (PluginMapper.PluginRecord record : pluginMapper.selectAll()) {
            if (!record.isEnabled()) {
                destroyPlugin(record.getPluginId());
                continue;
            }

            CollectorPlugin plugin = plugins.get(record.getPluginId());
            if (plugin == null || !plugin.isHealthy()) {
                plugin = createPlugin(record.getPluginId());
                if (plugin != null) {
                    plugins.put(record.getPluginId(), plugin);
                }
            }

            if (plugin != null && plugin.isHealthy()) {
                result.add(plugin);
            }
        }

        return result;
    }

    private void destroyPlugin(String pluginId) {
        CollectorPlugin old = plugins.remove(pluginId);
        if (old != null) {
            old.destroy();
        }
    }

    private CollectorPlugin createPlugin(String pluginId) {
        switch (pluginId) {
            case "ikuai": return createIkuai();
            case "pve": return createPve();
            case "storeos": return createStoreos();
            default:
                log.warn("Unknown plugin: {}", pluginId);
                return null;
        }
    }

    private IkuaiPlugin createIkuai() {
        List<Map<String, String>> rows = pluginConfigMapper.selectByPluginId("ikuai");
        Map<String, String> config = new HashMap<>();
        for (Map<String, String> row : rows) {
            config.put(row.get("config_key"), row.get("config_value"));
        }
        if (config.isEmpty()) {
            log.error("No config found for ikuai plugin in database, please run sql/ikuai.sql first");
            return null;
        }

        IkuaiPlugin plugin = new IkuaiPlugin();
        plugin.initialize(config);

        pluginMapper.setStatus("ikuai", plugin.isHealthy() ? "online" : "error");
        return plugin;
    }

    private PvePlugin createPve() {
        List<Map<String, String>> rows = pluginConfigMapper.selectByPluginId("pve");
        Map<String, String> config = new HashMap<>();
        for (Map<String, String> row : rows) {
            config.put(row.get("config_key"), row.get("config_value"));
        }
        if (config.isEmpty()) {
            log.error("No config found for pve plugin in database, please run sql/pve.sql first");
            return null;
        }

        PvePlugin plugin = new PvePlugin();
        plugin.initialize(config);

        pluginMapper.setStatus("pve", plugin.isHealthy() ? "online" : "error");
        return plugin;
    }

    private StoreosPlugin createStoreos() {
        List<Map<String, String>> rows = pluginConfigMapper.selectByPluginId("storeos");
        Map<String, String> config = new HashMap<>();
        for (Map<String, String> row : rows) {
            config.put(row.get("config_key"), row.get("config_value"));
        }
        if (config.isEmpty()) {
            log.error("No config found for storeos plugin in database, please run sql/storeos.sql first");
            return null;
        }

        StoreosPlugin plugin = new StoreosPlugin();
        plugin.initialize(config);

        pluginMapper.setStatus("storeos", plugin.isHealthy() ? "online" : "error");
        return plugin;
    }

    public void reloadPlugin(String pluginId) {
        log.info("Reloading plugin config: {}", pluginId);

        CollectorPlugin old = plugins.get(pluginId);
        CollectorPlugin newPlugin = createPlugin(pluginId);

        if (newPlugin != null && newPlugin.isHealthy()) {
            plugins.put(pluginId, newPlugin);
            if (old != null) {
                old.destroy();
            }
            log.info("Plugin [{}] reloaded successfully", pluginId);
        } else {
            log.warn("Plugin [{}] reload failed, keeping old instance", pluginId);
        }
    }

    /**
     * Test connection with given config, save to DB if successful.
     * Only calls login once via testConnection.
     */
    public boolean testConnectionAndSave(String pluginId, Map<String, String> config) {
        CollectorPlugin plugin = createPluginWithoutLogin(pluginId);
        if (plugin == null) {
            log.warn("Cannot test connection for unknown plugin: {}", pluginId);
            return false;
        }

        boolean ok = plugin.testConnection(config);
        if (!ok) {
            log.warn("Connection test failed for plugin [{}]", pluginId);
            return false;
        }

        // Connection succeeded — save config to DB
        for (Map.Entry<String, String> entry : config.entrySet()) {
            pluginConfigMapper.upsert(pluginId, entry.getKey(), entry.getValue());
        }
        log.info("Connection test passed for plugin [{}], config saved", pluginId);

        // Reload plugin with the new config (this will login again, but config is valid now)
        reloadPlugin(pluginId);
        return true;
    }

    /**
     * Enable plugin — only succeeds if config is valid and connection works.
     */
    public boolean enablePlugin(String pluginId) {
        List<Map<String, String>> rows = pluginConfigMapper.selectByPluginId(pluginId);
        if (rows.isEmpty()) {
            log.warn("Cannot enable plugin [{}] — no config found", pluginId);
            return false;
        }

        Map<String, String> config = new HashMap<>();
        for (Map<String, String> row : rows) {
            config.put(row.get("config_key"), row.get("config_value"));
        }

        CollectorPlugin testPlugin = createPluginWithoutLogin(pluginId);
        if (testPlugin == null || !testPlugin.testConnection(config)) {
            log.warn("Cannot enable plugin [{}] — connection test failed", pluginId);
            pluginMapper.setEnabled(pluginId, false);
            return false;
        }

        pluginMapper.setEnabled(pluginId, true);
        reloadPlugin(pluginId);
        return true;
    }

    /**
     * Test connection, save config, and enable plugin — all in one step.
     * Used by the frontend enable dialog.
     */
    public boolean testSaveEnable(String pluginId, Map<String, String> config) {
        // Step 1: test connection
        CollectorPlugin testPlugin = createPluginWithoutLogin(pluginId);
        if (testPlugin == null) {
            log.warn("Cannot test connection for unknown plugin: {}", pluginId);
            return false;
        }

        boolean ok = testPlugin.testConnection(config);
        if (!ok) {
            log.warn("Connection test failed for plugin [{}]", pluginId);
            return false;
        }

        // Step 2: save config to DB
        for (Map.Entry<String, String> entry : config.entrySet()) {
            pluginConfigMapper.upsert(pluginId, entry.getKey(), entry.getValue());
        }
        log.info("Config saved for plugin [{}]", pluginId);

        // Step 3: enable plugin
        pluginMapper.setEnabled(pluginId, true);
        reloadPlugin(pluginId);
        log.info("Plugin [{}] enabled after successful connection test", pluginId);
        return true;
    }

    /**
     * Create plugin instance without triggering login.
     * Used for testConnection to avoid double-login.
     */
    private CollectorPlugin createPluginWithoutLogin(String pluginId) {
        switch (pluginId) {
            case "ikuai": return new IkuaiPlugin();
            case "pve": return new PvePlugin();
            case "storeos": return new StoreosPlugin();
            default:
                log.warn("Unknown plugin: {}", pluginId);
                return null;
        }
    }

    /**
     * 创建插件实例（不登录），仅用于获取 configSchema
     */
    public CollectorPlugin createPluginWithoutLoginForSchema(String pluginId) {
        return createPluginWithoutLogin(pluginId);
    }
}
