package com.homelab.dashboard_backend.plugin.pve;

import com.homelab.dashboard_backend.plugin.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class PvePlugin implements CollectorPlugin {

    private static final String PLUGIN_ID = "pve";
    private static final String DISPLAY_NAME = "Proxmox VE";

    private PveClient client;
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
                        .name("host")
                        .label("PVE 地址")
                        .type(FieldType.TEXT)
                        .defaultValue("192.168.10.254")
                        .required(true)
                        .placeholder("192.168.10.254")
                        .build(),
                ConfigField.builder()
                        .name("port")
                        .label("API 端口")
                        .type(FieldType.NUMBER)
                        .defaultValue("8006")
                        .required(true)
                        .build(),
                ConfigField.builder()
                        .name("node")
                        .label("节点名称")
                        .type(FieldType.TEXT)
                        .defaultValue("pve")
                        .required(true)
                        .placeholder("pve")
                        .build(),
                ConfigField.builder()
                        .name("username")
                        .label("用户名")
                        .type(FieldType.TEXT)
                        .defaultValue("root")
                        .required(true)
                        .placeholder("root")
                        .build(),
                ConfigField.builder()
                        .name("realm")
                        .label("认证域")
                        .type(FieldType.TEXT)
                        .defaultValue("pam")
                        .required(true)
                        .placeholder("pam")
                        .build(),
                ConfigField.builder()
                        .name("token_id")
                        .label("Token ID")
                        .type(FieldType.TEXT)
                        .required(true)
                        .placeholder("homelab-dashboard")
                        .build(),
                ConfigField.builder()
                        .name("token_secret")
                        .label("Token Secret")
                        .type(FieldType.PASSWORD)
                        .required(true)
                        .build(),
                ConfigField.builder()
                        .name("ssh_host")
                        .label("SSH 地址（读取温度用，可选）")
                        .type(FieldType.TEXT)
                        .placeholder("与 PVE 地址相同即可")
                        .build(),
                ConfigField.builder()
                        .name("ssh_port")
                        .label("SSH 端口")
                        .type(FieldType.NUMBER)
                        .defaultValue("22")
                        .build(),
                ConfigField.builder()
                        .name("ssh_user")
                        .label("SSH 用户名")
                        .type(FieldType.TEXT)
                        .defaultValue("root")
                        .placeholder("root")
                        .build(),
                ConfigField.builder()
                        .name("ssh_password")
                        .label("SSH 密码（与密钥二选一）")
                        .type(FieldType.PASSWORD)
                        .build(),
                ConfigField.builder()
                        .name("ssh_key_path")
                        .label("SSH 私钥路径（与密码二选一）")
                        .type(FieldType.TEXT)
                        .placeholder("如 C:/Users/xxx/.ssh/id_rsa")
                        .build()
        );
    }

    @Override
    public void initialize(Map<String, String> config) {
        this.config = config;
        String host = config.get("host");
        int port = parsePort(config.get("port"), 8006);
        String node = config.getOrDefault("node", "pve");
        String username = config.get("username");
        String realm = config.getOrDefault("realm", "pam");
        String tokenId = config.get("token_id");
        String tokenSecret = config.get("token_secret");

        // SSH config
        String sshHost = config.get("ssh_host");
        if (sshHost == null || sshHost.isEmpty()) sshHost = host; // 默认和 PVE 地址相同
        int sshPort = parsePort(config.get("ssh_port"), 22);
        String sshUser = config.getOrDefault("ssh_user", "root");
        String sshPassword = config.get("ssh_password");
        String sshKeyPath = config.get("ssh_key_path");

        if (host == null || username == null || tokenId == null || tokenSecret == null) {
            log.error("Missing required PVE config: host={}, username={}, tokenId={}", host, username, tokenId);
            return;
        }

        // Build PVE API token: username@realm!tokenid=secret
        String apiToken = username + "@" + realm + "!" + tokenId + "=" + tokenSecret;

        this.client = new PveClient(host, port, node, apiToken,
                sshHost, sshPort, sshUser, sshPassword, sshKeyPath);
        healthy = client.testConnection();
        if (!healthy) {
            log.error("Failed to initialize PVE plugin - connection test failed");
        } else {
            log.info("PVE plugin initialized: {}:{} node={}", host, port, node);
            // 建立 SSH 长连接
            client.connectSsh();
            // 测试 SSH 温度读取
            String tempTest = client.sshExecute("sensors 2>/dev/null | head -1 || echo 'no sensors'");
            log.info("PVE SSH temperature test: {}", tempTest);
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

    @Override
    public CollectedMetrics collectRealtime() {
        Map<String, MetricValue> metrics = new ConcurrentHashMap<>();
        collectRealtime(metrics);
        return buildMetrics(metrics);
    }

    @Override
    public CollectedMetrics collectFrequent() {
        Map<String, MetricValue> metrics = new ConcurrentHashMap<>();
        collectFrequent(metrics);
        return buildMetrics(metrics);
    }

    @Override
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

    // ===== Realtime: 节点 CPU + 内存使用率 =====

    private void collectRealtime(Map<String, MetricValue> metrics) {
        PveClient.NodeStatus status = client.getNodeStatus();
        if (status == null) return;

        // CPU 使用率 (PVE 返回 0~1 的小数)
        double cpuPercent = Math.round(status.cpu * 1000.0) / 10.0;
        metrics.put("pve.cpu.usage", MetricValue.builder()
                .key("pve.cpu.usage")
                .value(cpuPercent)
                .unit("%")
                .displayName("PVE CPU 使用率")
                .type(MetricType.GAUGE)
                .build());

        // 内存 (转为 GB，保留一位小数)
        if (status.memory != null && status.memory.total > 0) {
            double usedGb = Math.round(status.memory.used * 10.0 / (1024L * 1024 * 1024)) / 10.0;
            double totalGb = Math.round(status.memory.total * 10.0 / (1024L * 1024 * 1024)) / 10.0;
            double memPercent = Math.round(status.memory.used * 10000.0 / status.memory.total) / 100.0;

            metrics.put("pve.memory.used", MetricValue.builder()
                    .key("pve.memory.used")
                    .value(usedGb)
                    .unit("GB")
                    .displayName("PVE 已用内存")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("pve.memory.total", MetricValue.builder()
                    .key("pve.memory.total")
                    .value(totalGb)
                    .unit("GB")
                    .displayName("PVE 总内存")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("pve.memory.usage", MetricValue.builder()
                    .key("pve.memory.usage")
                    .value(memPercent)
                    .unit("%")
                    .displayName("PVE 内存使用率")
                    .type(MetricType.GAUGE)
                    .build());
        }

        // 负载均值
        if (status.loadavg != null && !status.loadavg.isEmpty()) {
            metrics.put("pve.loadavg.1", MetricValue.builder()
                    .key("pve.loadavg.1")
                    .value(status.loadavg.get(0))
                    .unit("")
                    .displayName("PVE 1分钟负载")
                    .type(MetricType.GAUGE)
                    .build());
            if (status.loadavg.size() > 1) {
                metrics.put("pve.loadavg.5", MetricValue.builder()
                        .key("pve.loadavg.5")
                        .value(status.loadavg.get(1))
                        .unit("")
                        .displayName("PVE 5分钟负载")
                        .type(MetricType.GAUGE)
                        .build());
            }
            if (status.loadavg.size() > 2) {
                metrics.put("pve.loadavg.15", MetricValue.builder()
                        .key("pve.loadavg.15")
                        .value(status.loadavg.get(2))
                        .unit("")
                        .displayName("PVE 15分钟负载")
                        .type(MetricType.GAUGE)
                        .build());
            }
        }

        // CPU 温度（通过 SSH + sensors 命令读取）
        String tempOutput = client.sshExecute("sensors 2>/dev/null | grep -E 'Core 0|temp1' | head -1");
        if (tempOutput != null && !tempOutput.trim().isEmpty()) {
            // 解析 sensors 输出，如 "Core 0:        +45.0°C" 或 "temp1:        +42.0°C"
            double temp = parseSensorTemp(tempOutput);
            if (temp > 0) {
                metrics.put("pve.cpu.temp", MetricValue.builder()
                        .key("pve.cpu.temp")
                        .value(temp)
                        .unit("°C")
                        .displayName("CPU 温度")
                        .type(MetricType.GAUGE)
                        .build());
            }
        }
    }

    /**
     * 解析 sensors 命令输出中的温度值
     * 支持格式: "Core 0: +45.0°C" 或 "temp1: +42.0°C"
     */
    private double parseSensorTemp(String line) {
        try {
            // 匹配 "+数字.数字°C" 或 "-数字.数字°C"
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([+-]\\d+\\.\\d+)°?C?").matcher(line);
            if (m.find()) {
                return Math.round(Double.parseDouble(m.group(1)) * 10.0) / 10.0;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // ===== Frequent: VM/CT 列表 + 状态 =====

    private void collectFrequent(Map<String, MetricValue> metrics) {
        List<PveClient.ClusterResource> resources = client.getClusterResources();

        int vmCount = 0;
        int ctCount = 0;
        int runningCount = 0;

        StringBuilder vmList = new StringBuilder();
        StringBuilder vmJson = new StringBuilder("[");

        for (int i = 0; i < resources.size(); i++) {
            PveClient.ClusterResource r = resources.get(i);
            boolean isQemu = "qemu".equals(r.type);
            boolean isLxc = "lxc".equals(r.type);

            if (isQemu) vmCount++;
            if (isLxc) ctCount++;
            if ("running".equals(r.status)) runningCount++;

            // 人类可读列表
            if (i > 0) vmList.append("\n");
            String typeLabel = isQemu ? "VM" : "CT";
            String statusIcon = "running".equals(r.status) ? "🟢" : "⚫";
            vmList.append(String.format("%s %s [%d] %s - %s",
                    statusIcon, typeLabel, r.vmid,
                    r.name != null ? r.name : "",
                    r.status));

            // JSON 详情
            if (i > 0) vmJson.append(",");
            vmJson.append("{");
            vmJson.append("\"vmid\":").append(r.vmid).append(",");
            vmJson.append("\"name\":\"").append(escapeJson(r.name != null ? r.name : "")).append("\",");
            vmJson.append("\"type\":\"").append(r.type != null ? r.type : "").append("\",");
            vmJson.append("\"status\":\"").append(r.status != null ? r.status : "").append("\",");
            vmJson.append("\"node\":\"").append(escapeJson(r.node != null ? r.node : "")).append("\",");
            vmJson.append("\"cpu\":").append(r.cpu).append(",");
            vmJson.append("\"mem\":").append(r.mem).append(",");
            vmJson.append("\"maxmem\":").append(r.maxmem).append(",");
            vmJson.append("\"disk\":").append(r.disk).append(",");
            vmJson.append("\"maxdisk\":").append(r.maxdisk).append(",");
            vmJson.append("\"netin\":").append(r.netin).append(",");
            vmJson.append("\"netout\":").append(r.netout);
            vmJson.append("}");
        }
        vmJson.append("]");

        metrics.put("pve.vm.count", MetricValue.builder()
                .key("pve.vm.count")
                .value(vmCount)
                .unit("")
                .displayName("PVE 虚拟机数量")
                .type(MetricType.GAUGE)
                .build());
        metrics.put("pve.ct.count", MetricValue.builder()
                .key("pve.ct.count")
                .value(ctCount)
                .unit("")
                .displayName("PVE 容器数量")
                .type(MetricType.GAUGE)
                .build());
        metrics.put("pve.vm.running_count", MetricValue.builder()
                .key("pve.vm.running_count")
                .value(runningCount)
                .unit("")
                .displayName("PVE 运行中数量")
                .type(MetricType.GAUGE)
                .build());
        metrics.put("pve.vm.list", MetricValue.builder()
                .key("pve.vm.list")
                .value(vmList.toString())
                .unit("")
                .displayName("PVE 虚拟机列表")
                .type(MetricType.INFO)
                .build());
        metrics.put("pve.vm.details", MetricValue.builder()
                .key("pve.vm.details")
                .value(vmJson.toString())
                .unit("")
                .displayName("PVE 虚拟机详情")
                .type(MetricType.INFO)
                .build());
    }

    // ===== Slow: 节点详细信息 + 磁盘 =====

    private void collectSlow(Map<String, MetricValue> metrics) {
        PveClient.NodeStatus status = client.getNodeStatus();
        if (status == null) return;

        // 运行时间
        metrics.put("pve.system.uptime", MetricValue.builder()
                .key("pve.system.uptime")
                .value(status.uptime)
                .unit("seconds")
                .displayName("PVE 运行时间")
                .type(MetricType.GAUGE)
                .build());

        // CPU 信息
        if (status.cpuinfo != null) {
            metrics.put("pve.cpu.cores", MetricValue.builder()
                    .key("pve.cpu.cores")
                    .value(status.cpuinfo.cores)
                    .unit("")
                    .displayName("PVE CPU 核心数")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("pve.cpu.model", MetricValue.builder()
                    .key("pve.cpu.model")
                    .value(status.cpuinfo.model != null ? status.cpuinfo.model : "")
                    .unit("")
                    .displayName("PVE CPU 型号")
                    .type(MetricType.INFO)
                    .build());
        }

        // 磁盘 (rootfs)
        if (status.rootfs != null && status.rootfs.total > 0) {
            long usedGb = status.rootfs.used / (1024L * 1024 * 1024);
            long totalGb = status.rootfs.total / (1024L * 1024 * 1024);
            double diskPercent = Math.round(status.rootfs.used * 10000.0 / status.rootfs.total) / 100.0;

            metrics.put("pve.disk.used", MetricValue.builder()
                    .key("pve.disk.used")
                    .value(usedGb)
                    .unit("GB")
                    .displayName("PVE 已用磁盘")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("pve.disk.total", MetricValue.builder()
                    .key("pve.disk.total")
                    .value(totalGb)
                    .unit("GB")
                    .displayName("PVE 总磁盘")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("pve.disk.usage", MetricValue.builder()
                    .key("pve.disk.usage")
                    .value(diskPercent)
                    .unit("%")
                    .displayName("PVE 磁盘使用率")
                    .type(MetricType.GAUGE)
                    .build());
        }

        // 节点名称
        metrics.put("pve.system.node", MetricValue.builder()
                .key("pve.system.node")
                .value(status.node != null ? status.node : config.getOrDefault("node", "pve"))
                .unit("")
                .displayName("PVE 节点名称")
                .type(MetricType.INFO)
                .build());

        // 节点状态
        metrics.put("pve.system.status", MetricValue.builder()
                .key("pve.system.status")
                .value(status.status != null ? status.status : "unknown")
                .unit("")
                .displayName("PVE 节点状态")
                .type(MetricType.INFO)
                .build());
    }

    @Override
    public boolean testConnection(Map<String, String> config) {
        String host = config.get("host");
        int port = parsePort(config.get("port"), 8006);
        String node = config.getOrDefault("node", "pve");
        String username = config.get("username");
        String realm = config.getOrDefault("realm", "pam");
        String tokenId = config.get("token_id");
        String tokenSecret = config.get("token_secret");

        if (host == null || username == null || tokenId == null || tokenSecret == null) {
            return false;
        }

        String sshHost = config.get("ssh_host");
        if (sshHost == null || sshHost.isEmpty()) sshHost = host;
        int sshPort = parsePort(config.get("ssh_port"), 22);
        String sshUser = config.getOrDefault("ssh_user", "root");
        String sshPassword = config.get("ssh_password");
        String sshKeyPath = config.get("ssh_key_path");

        String apiToken = username + "@" + realm + "!" + tokenId + "=" + tokenSecret;
        PveClient testClient = new PveClient(host, port, node, apiToken,
                sshHost, sshPort, sshUser, sshPassword, sshKeyPath);
        return testClient.testConnection();
    }

    @Override
    public boolean isHealthy() {
        return healthy && client != null;
    }

    @Override
    public void destroy() {
        healthy = false;
        if (client != null) {
            client.disconnectSsh();
        }
        client = null;
        log.info("PVE plugin destroyed");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int parsePort(String portStr, int defaultPort) {
        if (portStr == null || portStr.isEmpty()) return defaultPort;
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }
}
