package com.homelab.dashboard_backend.plugin.ikuai;

import com.homelab.dashboard_backend.plugin.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class IkuaiPlugin implements CollectorPlugin {

    private static final String PLUGIN_ID = "ikuai";
    private static final String DISPLAY_NAME = "iKuai Router";

    private IkuaiClient client;
    private Map<String, String> config;
    private volatile boolean healthy = false;

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                ConfigField.builder()
                        .name("address")
                        .label("管理地址")
                        .type(FieldType.TEXT)
                        .defaultValue("http://192.168.1.1")
                        .required(true)
                        .placeholder("http://192.168.1.1")
                        .build(),
                ConfigField.builder()
                        .name("username")
                        .label("用户名")
                        .type(FieldType.TEXT)
                        .defaultValue("admin")
                        .required(true)
                        .build(),
                ConfigField.builder()
                        .name("password")
                        .label("密码")
                        .type(FieldType.PASSWORD)
                        .required(true)
                        .build()
        );
    }

    @Override
    public void initialize(Map<String, String> config) {
        this.config = config;
        String address = config.get("address");
        String username = config.get("username");
        String password = config.get("password");

        if (address == null || username == null || password == null) {
            log.error("Missing required config: address={}, username={}, password=***", address, username);
            return;
        }

        if (!address.startsWith("http")) {
            address = "http://" + address;
        }

        this.client = new IkuaiClient(address, username, password);
        healthy = client.login();
        if (!healthy) {
            log.error("Failed to initialize iKuai plugin - login failed");
        }
    }

    @Override
    public CollectedMetrics collect() {
        Map<String, MetricValue> metrics = new ConcurrentHashMap<>();
        collectRealtime(metrics);
        collectFrequent(metrics);
        collectSlow(metrics);
        return buildMetrics(metrics);
    }

    public CollectedMetrics collectRealtime() {
        Map<String, MetricValue> metrics = new ConcurrentHashMap<>();
        collectRealtime(metrics);
        return buildMetrics(metrics);
    }

    public CollectedMetrics collectFrequent() {
        Map<String, MetricValue> metrics = new ConcurrentHashMap<>();
        collectFrequent(metrics);
        return buildMetrics(metrics);
    }

    public CollectedMetrics collectSlow() {
        Map<String, MetricValue> metrics = new ConcurrentHashMap<>();
        collectSlow(metrics);
        return buildMetrics(metrics);
    }

    private CollectedMetrics buildMetrics(Map<String, MetricValue> metrics) {
        return CollectedMetrics.builder()
                .pluginId(PLUGIN_ID)
                .collectedAt(Instant.now())
                .metrics(metrics)
                .build();
    }

    private void collectRealtime(Map<String, MetricValue> metrics) {
        IkuaiClient.HomepageData data = client.fetchHomepage();
        if (data == null || data.sysstat == null) return;

        IkuaiClient.HomepageData.Sysstat sys = data.sysstat;

        // Upload/download rate
        if (sys.stream != null) {
            metrics.put("stream.upload", MetricValue.builder()
                    .key("stream.upload")
                    .value(sys.stream.upload)
                    .unit("bps")
                    .displayName("上行速率")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("stream.download", MetricValue.builder()
                    .key("stream.download")
                    .value(sys.stream.download)
                    .unit("bps")
                    .displayName("下行速率")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("stream.connect_num", MetricValue.builder()
                    .key("stream.connect_num")
                    .value(sys.stream.connectNum)
                    .unit("")
                    .displayName("连接数")
                    .type(MetricType.GAUGE)
                    .build());
        }

        // CPU usage (average across cores)
        if (sys.cpu != null && !sys.cpu.isEmpty()) {
            double avgCpu = sys.cpu.stream()
                    .mapToDouble(s -> {
                        try { return Double.parseDouble(s.replace("%", "")); }
                        catch (Exception e) { return 0; }
                    })
                    .average().orElse(0);
            metrics.put("cpu.usage", MetricValue.builder()
                    .key("cpu.usage")
                    .value(Math.round(avgCpu * 100.0) / 100.0)
                    .unit("%")
                    .displayName("CPU 使用率")
                    .type(MetricType.GAUGE)
                    .build());
        }

        // CPU temperature (max across cores)
        if (sys.cpuTemp != null && !sys.cpuTemp.isEmpty()) {
            int maxTemp = sys.cpuTemp.stream().mapToInt(Integer::intValue).max().orElse(0);
            metrics.put("cpu.temp", MetricValue.builder()
                    .key("cpu.temp")
                    .value(maxTemp)
                    .unit("°C")
                    .displayName("CPU 温度")
                    .type(MetricType.GAUGE)
                    .build());
        }

        // WAN interface status + speed (realtime)
        IkuaiClient.IfaceData ifaceData = client.fetchIfaceData();
        if (ifaceData != null) {
            for (IkuaiClient.IfaceCheck check : ifaceData.ifaceCheck) {
                String ifaceName = check.iface != null ? check.iface : "wan" + check.id;
                metrics.put("wan." + ifaceName + ".status", MetricValue.builder()
                        .key("wan." + ifaceName + ".status")
                        .value(check.internet != null ? check.internet : "unknown")
                        .unit("")
                        .displayName(ifaceName + " 状态")
                        .type(MetricType.INFO)
                        .build());
            }

            for (IkuaiClient.IfaceStream stream : ifaceData.ifaceStream) {
                String ifaceName = stream.iface != null ? stream.iface : "wan";
                metrics.put("wan." + ifaceName + ".upload", MetricValue.builder()
                        .key("wan." + ifaceName + ".upload")
                        .value(stream.upload)
                        .unit("bps")
                        .displayName(ifaceName + " 上行")
                        .type(MetricType.GAUGE)
                        .build());
                metrics.put("wan." + ifaceName + ".download", MetricValue.builder()
                        .key("wan." + ifaceName + ".download")
                        .value(stream.download)
                        .unit("bps")
                        .displayName(ifaceName + " 下行")
                        .type(MetricType.GAUGE)
                        .build());
            }
        }
    }

    private void collectFrequent(Map<String, MetricValue> metrics) {
        // Online devices
        List<IkuaiClient.LanDevice> devices = client.fetchLanDevices();
        metrics.put("lan.device_count", MetricValue.builder()
                .key("lan.device_count")
                .value(devices.size())
                .unit("")
                .displayName("在线设备数")
                .type(MetricType.GAUGE)
                .build());

        // Device list as INFO
        StringBuilder deviceList = new StringBuilder();
        for (int i = 0; i < devices.size(); i++) {
            IkuaiClient.LanDevice d = devices.get(i);
            String name = (d.hostname != null && !d.hostname.isEmpty()) ? d.hostname
                    : (d.comment != null && !d.comment.isEmpty()) ? d.comment
                    : "Unknown";
            if (i > 0) deviceList.append("\n");
            deviceList.append(String.format("%s (%s) [%s]",
                    name,
                    d.ipAddr != null ? d.ipAddr : d.mac,
                    d.clientType != null ? d.clientType : ""));
        }
        metrics.put("lan.devices", MetricValue.builder()
                .key("lan.devices")
                .value(deviceList.toString())
                .unit("")
                .displayName("在线设备列表")
                .type(MetricType.INFO)
                .build());
    }

    private void collectSlow(Map<String, MetricValue> metrics) {
        IkuaiClient.HomepageData data = client.fetchHomepage();
        if (data == null || data.sysstat == null) return;

        IkuaiClient.HomepageData.Sysstat sys = data.sysstat;

        // Memory — iKuai returns values in KB
        if (sys.memory != null) {
            long totalKb = sys.memory.total;
            long usedKb = totalKb - sys.memory.available;
            double usedPercent = totalKb > 0
                    ? Math.round(usedKb * 10000.0 / totalKb) / 100.0
                    : 0;

            metrics.put("memory.total", MetricValue.builder()
                    .key("memory.total")
                    .value(totalKb / 1024)
                    .unit("MB")
                    .displayName("总内存")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("memory.used", MetricValue.builder()
                    .key("memory.used")
                    .value(usedKb / 1024)
                    .unit("MB")
                    .displayName("已用内存")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("memory.usage", MetricValue.builder()
                    .key("memory.usage")
                    .value(usedPercent)
                    .unit("%")
                    .displayName("内存使用率")
                    .type(MetricType.GAUGE)
                    .build());
        }

        // Cumulative traffic
        if (sys.stream != null) {
            metrics.put("stream.total_up", MetricValue.builder()
                    .key("stream.total_up")
                    .value(sys.stream.totalUp)
                    .unit("bytes")
                    .displayName("累计上行流量")
                    .type(MetricType.COUNTER)
                    .build());
            metrics.put("stream.total_down", MetricValue.builder()
                    .key("stream.total_down")
                    .value(sys.stream.totalDown)
                    .unit("bytes")
                    .displayName("累计下行流量")
                    .type(MetricType.COUNTER)
                    .build());
        }

        // Uptime
        metrics.put("system.uptime", MetricValue.builder()
                .key("system.uptime")
                .value(sys.uptime)
                .unit("seconds")
                .displayName("运行时间")
                .type(MetricType.GAUGE)
                .build());

        // Version info
        if (sys.verInfo != null) {
            metrics.put("system.version", MetricValue.builder()
                    .key("system.version")
                    .value(sys.verInfo.verstring != null ? sys.verInfo.verstring : "")
                    .unit("")
                    .displayName("固件版本")
                    .type(MetricType.INFO)
                    .build());
            metrics.put("system.model", MetricValue.builder()
                    .key("system.model")
                    .value(sys.verInfo.modelname != null ? sys.verInfo.modelname : "")
                    .unit("")
                    .displayName("设备型号")
                    .type(MetricType.INFO)
                    .build());
        }

        // Hostname
        metrics.put("system.hostname", MetricValue.builder()
                .key("system.hostname")
                .value(sys.hostname != null ? sys.hostname : "")
                .unit("")
                .displayName("主机名")
                .type(MetricType.INFO)
                .build());

        // Online user summary
        if (sys.onlineUser != null) {
            metrics.put("online_user.count", MetricValue.builder()
                    .key("online_user.count")
                    .value(sys.onlineUser.count)
                    .unit("")
                    .displayName("在线用户数")
                    .type(MetricType.GAUGE)
                    .build());
        }
    }

    @Override
    public boolean testConnection(Map<String, String> config) {
        String address = config.get("address");
        String username = config.get("username");
        String password = config.get("password");

        if (address == null || username == null || password == null) {
            return false;
        }

        if (!address.startsWith("http")) {
            address = "http://" + address;
        }

        IkuaiClient testClient = new IkuaiClient(address, username, password);
        return testClient.login();
    }

    @Override
    public boolean isHealthy() {
        return healthy && client != null && client.isSessionValid();
    }

    @Override
    public void destroy() {
        healthy = false;
        client = null;
        log.info("iKuai plugin destroyed");
    }
}
