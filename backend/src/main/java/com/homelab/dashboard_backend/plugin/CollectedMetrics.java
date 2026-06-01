package com.homelab.dashboard_backend.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectedMetrics {

    private String pluginId;
    private Instant collectedAt;
    private Map<String, MetricValue> metrics;
}
