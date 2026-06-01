package com.homelab.dashboard_backend.controller;

import com.homelab.dashboard_backend.mapper.CardMetricMappingMapper;
import com.homelab.dashboard_backend.mapper.DashboardCardMapper;
import com.homelab.dashboard_backend.plugin.CollectorPlugin;
import com.homelab.dashboard_backend.plugin.CollectedMetrics;
import com.homelab.dashboard_backend.plugin.MetricValue;
import com.homelab.dashboard_backend.service.DashboardEditingService;
import com.homelab.dashboard_backend.service.PluginService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/metrics")
public class MetricsController {

    private final PluginService pluginService;
    private final DashboardCardMapper cardMapper;
    private final CardMetricMappingMapper mappingMapper;
    private final DashboardEditingService editingService;

    public MetricsController(PluginService pluginService,
                             DashboardCardMapper cardMapper,
                             CardMetricMappingMapper mappingMapper,
                             DashboardEditingService editingService) {
        this.pluginService = pluginService;
        this.cardMapper = cardMapper;
        this.mappingMapper = mappingMapper;
        this.editingService = editingService;
    }

    @GetMapping("/realtime")
    public Map<String, CollectedMetrics> realtime(
            @RequestParam(defaultValue = "network-overview") String template) {
        return collectFiltered(CollectorPlugin::collectRealtime, template);
    }

    @GetMapping("/frequent")
    public Map<String, CollectedMetrics> frequent(
            @RequestParam(defaultValue = "network-overview") String template) {
        return collectFiltered(CollectorPlugin::collectFrequent, template);
    }

    @GetMapping("/slow")
    public Map<String, CollectedMetrics> slow(
            @RequestParam(defaultValue = "network-overview") String template) {
        return collectFiltered(CollectorPlugin::collectSlow, template);
    }

    /**
     * 按需采集：根据当前布局的卡片类型，只返回这些卡片需要的指标。
     * 编辑模式下返回全量指标，便于预览面板展示所有卡片。
     */
    private Map<String, CollectedMetrics> collectFiltered(CollectorMethod method, String template) {
        List<CollectorPlugin> enabledPlugins = pluginService.getEnabledPlugins();
        Map<String, CollectedMetrics> result = new LinkedHashMap<>();

        // 编辑模式：返回全量
        boolean fullMode = editingService.isEditing(template);

        // 非编辑模式：收集当前布局需要的指标 key
        Set<String> requiredKeys = fullMode ? null : collectRequiredKeys(template);

        for (CollectorPlugin plugin : enabledPlugins) {
            try {
                CollectedMetrics metrics = method.collect(plugin);
                if (!fullMode && requiredKeys != null) {
                    metrics = filterMetrics(metrics, requiredKeys);
                }
                result.put(plugin.getPluginId(), metrics);
            } catch (Exception e) {
                result.put(plugin.getPluginId(), CollectedMetrics.builder()
                        .pluginId(plugin.getPluginId())
                        .metrics(Map.of())
                        .build());
            }
        }
        return result;
    }

    /**
     * 查询当前布局用到的卡片类型，再查这些卡片需要的指标 key
     */
    private Set<String> collectRequiredKeys(String template) {
        List<DashboardCardMapper.DashboardCard> cards = cardMapper.selectByTemplate(template);
        if (cards.isEmpty()) return Collections.emptySet();

        List<String> cardTypes = cards.stream()
                .map(DashboardCardMapper.DashboardCard::getCardType)
                .distinct()
                .toList();

        List<CardMetricMappingMapper.CardMetricMapping> mappings =
                mappingMapper.selectByCardTypes(cardTypes);

        return mappings.stream()
                .map(CardMetricMappingMapper.CardMetricMapping::getMetricKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 过滤指标：只保留 requiredKeys 中匹配的 key。
     * 支持 * 通配符匹配（如 wan.*.upload 匹配 wan.wan1.upload）。
     */
    private CollectedMetrics filterMetrics(CollectedMetrics metrics, Set<String> requiredKeys) {
        if (requiredKeys.isEmpty()) {
            return CollectedMetrics.builder()
                    .pluginId(metrics.getPluginId())
                    .collectedAt(metrics.getCollectedAt())
                    .metrics(Map.of())
                    .build();
        }

        Map<String, MetricValue> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, MetricValue> entry : metrics.getMetrics().entrySet()) {
            if (matchesRequired(entry.getKey(), requiredKeys)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        return CollectedMetrics.builder()
                .pluginId(metrics.getPluginId())
                .collectedAt(metrics.getCollectedAt())
                .metrics(filtered)
                .build();
    }

    /**
     * 判断指标 key 是否匹配 requiredKeys 中的任一 pattern。
     * 支持 * 通配：wan.*.upload 匹配 wan.wan1.upload、wan.lan1.upload 等。
     */
    private boolean matchesRequired(String metricKey, Set<String> requiredKeys) {
        for (String pattern : requiredKeys) {
            if (pattern.equals(metricKey)) return true;
            if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.").replace("*", "[^.]+");
                if (Pattern.matches(regex, metricKey)) return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface CollectorMethod {
        CollectedMetrics collect(CollectorPlugin plugin);
    }
}
