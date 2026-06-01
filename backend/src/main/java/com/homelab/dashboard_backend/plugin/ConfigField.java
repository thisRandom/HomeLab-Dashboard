package com.homelab.dashboard_backend.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigField {

    private String name;
    private String label;
    private FieldType type;
    private String defaultValue;
    private boolean required;
    private String placeholder;
}
