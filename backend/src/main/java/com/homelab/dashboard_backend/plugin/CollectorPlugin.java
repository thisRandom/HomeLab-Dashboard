package com.homelab.dashboard_backend.plugin;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface CollectorPlugin {

    String getPluginId();

    String getDisplayName();

    List<ConfigField> getConfigSchema();

    void initialize(Map<String, String> config);

    CollectedMetrics collect();

    default CollectedMetrics collectRealtime() {
        return emptyMetrics();
    }

    default CollectedMetrics collectFrequent() {
        return emptyMetrics();
    }

    default CollectedMetrics collectSlow() {
        return emptyMetrics();
    }

    boolean isHealthy();

    void destroy();

    /**
     * Test connection with the given config without saving.
     * Returns true if connection succeeds, false otherwise.
     */
    default boolean testConnection(Map<String, String> config) {
        return false;
    }

    private CollectedMetrics emptyMetrics() {
        return CollectedMetrics.builder()
                .pluginId(getPluginId())
                .collectedAt(Instant.now())
                .metrics(Collections.emptyMap())
                .build();
    }
}
