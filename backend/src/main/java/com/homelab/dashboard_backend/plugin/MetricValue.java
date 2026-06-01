package com.homelab.dashboard_backend.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricValue {

    private String key;
    private Object value;
    private String unit;
    private String displayName;
    private MetricType type;
}
