package com.homelab.dashboard_backend.plugin.storeos;

import com.homelab.dashboard_backend.plugin.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class StoreosPlugin implements CollectorPlugin {

    private static final String PLUGIN_ID = "storeos";
    private static final String DISPLAY_NAME = "OpenWrt";

    private StoreosClient client;
    private Map<String, String> config;
    private volatile boolean healthy = false;

    // 用于计算网络速率的上次采样值
    private long lastRxBytes = -1;
    private long lastTxBytes = -1;
    private Instant lastTrafficTime = null;

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
                        .defaultValue("192.168.10.10")
                        .required(true)
                        .placeholder("192.168.10.10")
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
            log.error("Missing required OpenWrt config: address={}, username={}", address, username);
            return;
        }

        // 去除协议前缀（如果有）
        if (address.startsWith("http://")) address = address.substring(7);
        if (address.startsWith("https://")) address = address.substring(8);
        // 去除末尾斜杠
        while (address.endsWith("/")) address = address.substring(0, address.length() - 1);

        this.client = new StoreosClient(address, username, password);
        healthy = client.login();
        if (!healthy) {
            log.error("Failed to initialize OpenWrt plugin - login failed");
        } else {
            log.info("OpenWrt plugin initialized: {}", address);
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

    // ===== Realtime: 网络速率 + 连接数 =====

    private void collectRealtime(Map<String, MetricValue> metrics) {
        // 获取网络设备统计
        Map<String, StoreosClient.NetworkDevice> devices = client.fetchNetworkDevices();
        if (devices != null) {
            // 找到 br-lan（主桥接接口）的流量数据
            StoreosClient.NetworkDevice lan = devices.get("br-lan");
            if (lan != null && lan.stats != null) {
                long currentRx = lan.stats.rx_bytes;
                long currentTx = lan.stats.tx_bytes;
                Instant now = Instant.now();

                if (lastTrafficTime != null && lastRxBytes >= 0) {
                    double elapsed = (now.toEpochMilli() - lastTrafficTime.toEpochMilli()) / 1000.0;
                    if (elapsed > 0) {
                        // 计算速率（bytes/s → bps）
                        double rxRate = (currentRx - lastRxBytes) * 8.0 / elapsed;
                        double txRate = (currentTx - lastTxBytes) * 8.0 / elapsed;

                        metrics.put("storeos.net.rx_rate", MetricValue.builder()
                                .key("storeos.net.rx_rate")
                                .value(Math.round(rxRate))
                                .unit("bps")
                                .displayName("网络接收速率")
                                .type(MetricType.GAUGE)
                                .build());
                        metrics.put("storeos.net.tx_rate", MetricValue.builder()
                                .key("storeos.net.tx_rate")
                                .value(Math.round(txRate))
                                .unit("bps")
                                .displayName("网络发送速率")
                                .type(MetricType.GAUGE)
                                .build());
                    }
                }

                lastRxBytes = currentRx;
                lastTxBytes = currentTx;
                lastTrafficTime = now;
            }
        }

        // 连接数
        String conntrackCount = client.fetchConntrackCount();
        if (conntrackCount != null) {
            try {
                metrics.put("storeos.conn.count", MetricValue.builder()
                        .key("storeos.conn.count")
                        .value(Integer.parseInt(conntrackCount))
                        .unit("")
                        .displayName("连接数")
                        .type(MetricType.GAUGE)
                        .build());
            } catch (NumberFormatException ignored) {}
        }
    }

    // ===== Frequent: 内存、负载、DHCP、进程 =====

    private void collectFrequent(Map<String, MetricValue> metrics) {
        StoreosClient.SystemInfo info = client.fetchSystemInfo();
        if (info != null && info.memory != null) {
            // 内存（ubus 返回的是 bytes）
            long totalBytes = info.memory.total;
            long availableBytes = info.memory.available;
            long usedBytes = totalBytes - availableBytes;
            double usedPercent = totalBytes > 0
                    ? Math.round(usedBytes * 10000.0 / totalBytes) / 100.0
                    : 0;

            metrics.put("storeos.memory.used", MetricValue.builder()
                    .key("storeos.memory.used")
                    .value(usedBytes / (1024 * 1024))
                    .unit("MB")
                    .displayName("已用内存")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("storeos.memory.total", MetricValue.builder()
                    .key("storeos.memory.total")
                    .value(totalBytes / (1024 * 1024))
                    .unit("MB")
                    .displayName("总内存")
                    .type(MetricType.GAUGE)
                    .build());
            metrics.put("storeos.memory.usage", MetricValue.builder()
                    .key("storeos.memory.usage")
                    .value(usedPercent)
                    .unit("%")
                    .displayName("内存使用率")
                    .type(MetricType.GAUGE)
                    .build());

            // 负载均值（ubus 返回 ×10000 的整数，如 16608 → 1.6608）
            if (info.load != null && info.load.size() >= 3) {
                metrics.put("storeos.load.1", MetricValue.builder()
                        .key("storeos.load.1")
                        .value(info.load.get(0) / 10000.0)
                        .unit("")
                        .displayName("1分钟负载")
                        .type(MetricType.GAUGE)
                        .build());
                metrics.put("storeos.load.5", MetricValue.builder()
                        .key("storeos.load.5")
                        .value(info.load.get(1) / 10000.0)
                        .unit("")
                        .displayName("5分钟负载")
                        .type(MetricType.GAUGE)
                        .build());
                metrics.put("storeos.load.15", MetricValue.builder()
                        .key("storeos.load.15")
                        .value(info.load.get(2) / 10000.0)
                        .unit("")
                        .displayName("15分钟负载")
                        .type(MetricType.GAUGE)
                        .build());
            }
        }

        // DHCP 客户端数 + 详情
        StoreosClient.DhcpLeases leases = client.fetchDHCPLeases();
        if (leases != null && leases.dhcp_leases != null) {
            metrics.put("storeos.dhcp.count", MetricValue.builder()
                    .key("storeos.dhcp.count")
                    .value(leases.dhcp_leases.size())
                    .unit("")
                    .displayName("DHCP 客户端数")
                    .type(MetricType.GAUGE)
                    .build());

            // DHCP 客户端详情 JSON
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < leases.dhcp_leases.size(); i++) {
                StoreosClient.DhcpLease lease = leases.dhcp_leases.get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"hostname\":\"").append(escapeJson(lease.hostname != null ? lease.hostname : "")).append("\",");
                json.append("\"ip\":\"").append(escapeJson(lease.ipaddr != null ? lease.ipaddr : "")).append("\",");
                json.append("\"mac\":\"").append(escapeJson(lease.macaddr != null ? lease.macaddr : "")).append("\"");
                json.append("}");
            }
            json.append("]");
            metrics.put("storeos.dhcp.clients", MetricValue.builder()
                    .key("storeos.dhcp.clients")
                    .value(json.toString())
                    .unit("")
                    .displayName("DHCP 客户端列表")
                    .type(MetricType.INFO)
                    .build());
        }

        // 在线设备（合并 DHCP + ARP，去重；ARP 在某些版本不可用，自动跳过）
        Set<String> seenIps = new LinkedHashSet<>();
        StringBuilder devicesJson = new StringBuilder("[");
        int deviceIdx = 0;

        // 先加 DHCP 客户端
        if (leases != null && leases.dhcp_leases != null) {
            for (StoreosClient.DhcpLease lease : leases.dhcp_leases) {
                String ip = lease.ipaddr != null ? lease.ipaddr : "";
                if (ip.isEmpty() || seenIps.contains(ip)) continue;
                seenIps.add(ip);
                if (deviceIdx > 0) devicesJson.append(",");
                devicesJson.append("{");
                devicesJson.append("\"hostname\":\"").append(escapeJson(lease.hostname != null ? lease.hostname : "")).append("\",");
                devicesJson.append("\"ip\":\"").append(escapeJson(ip)).append("\",");
                devicesJson.append("\"mac\":\"").append(escapeJson(lease.macaddr != null ? lease.macaddr : "")).append("\",");
                devicesJson.append("\"source\":\"dhcp\"");
                devicesJson.append("}");
                deviceIdx++;
            }
        }

        // 再补 ARP 中不在 DHCP 里的设备（静态 IP 设备；luci.getHostHints 不可用时自动为空）
        List<StoreosClient.ArpEntry> arpEntries = client.fetchArpTable();
        for (StoreosClient.ArpEntry arp : arpEntries) {
            String ip = arp.ip != null ? arp.ip : "";
            if (ip.isEmpty() || seenIps.contains(ip)) continue;
            seenIps.add(ip);
            if (deviceIdx > 0) devicesJson.append(",");
            devicesJson.append("{");
            devicesJson.append("\"hostname\":\"").append(escapeJson(arp.hostname != null ? arp.hostname : "")).append("\",");
            devicesJson.append("\"ip\":\"").append(escapeJson(ip)).append("\",");
            devicesJson.append("\"mac\":\"").append(escapeJson(arp.mac != null ? arp.mac : "")).append("\",");
            devicesJson.append("\"source\":\"arp\"");
            devicesJson.append("}");
            deviceIdx++;
        }

        devicesJson.append("]");

        metrics.put("storeos.device.count", MetricValue.builder()
                .key("storeos.device.count")
                .value(deviceIdx)
                .unit("")
                .displayName("在线设备数")
                .type(MetricType.GAUGE)
                .build());
        metrics.put("storeos.device.details", MetricValue.builder()
                .key("storeos.device.details")
                .value(devicesJson.toString())
                .unit("")
                .displayName("在线设备详情")
                .type(MetricType.INFO)
                .build());

        // 连接追踪统计（旁路由场景下很重要）
        String ctCountStr = client.fetchConntrackCount();
        String ctMaxStr = client.fetchConntrackMax();
        int ctCount = 0;
        int ctMax = 0;
        try { ctCount = Integer.parseInt(ctCountStr); } catch (Exception ignored) {}
        try { ctMax = Integer.parseInt(ctMaxStr); } catch (Exception ignored) {}

        double ctUsage = ctMax > 0 ? Math.round(ctCount * 10000.0 / ctMax) / 100.0 : 0;
        metrics.put("storeos.conn.usage", MetricValue.builder()
                .key("storeos.conn.usage")
                .value(ctUsage)
                .unit("%")
                .displayName("连接追踪使用率")
                .type(MetricType.GAUGE)
                .build());
        metrics.put("storeos.conn.max", MetricValue.builder()
                .key("storeos.conn.max")
                .value(ctMax)
                .unit("")
                .displayName("连接追踪最大值")
                .type(MetricType.INFO)
                .build());

        // 每设备连接数（按 src IP 聚合 conntrack，关联 DHCP/ARP 主机名）
        Map<String, String> ipHostnameMap = new LinkedHashMap<>();
        if (leases != null && leases.dhcp_leases != null) {
            for (StoreosClient.DhcpLease lease : leases.dhcp_leases) {
                if (lease.ipaddr != null && !lease.ipaddr.isEmpty() && lease.hostname != null && !lease.hostname.isEmpty()) {
                    ipHostnameMap.put(lease.ipaddr, lease.hostname);
                }
            }
        }
        for (StoreosClient.ArpEntry arp : arpEntries) {
            if (arp.ip != null && !arp.ip.isEmpty() && arp.hostname != null && !arp.hostname.isEmpty()) {
                ipHostnameMap.putIfAbsent(arp.ip, arp.hostname);
            }
        }

        List<StoreosClient.ConntrackEntry> conntrackList = client.fetchConntrackList();
        Map<String, Integer> deviceConns = new LinkedHashMap<>();
        for (StoreosClient.ConntrackEntry entry : conntrackList) {
            if (entry.src != null && !entry.src.isEmpty()) {
                deviceConns.merge(entry.src, 1, Integer::sum);
            }
        }
        // 按连接数排序，取 Top 20
        StringBuilder deviceConnsJson = new StringBuilder("[");
        deviceConns.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .forEach(e -> {
                    if (deviceConnsJson.length() > 1) deviceConnsJson.append(",");
                    String hostname = ipHostnameMap.getOrDefault(e.getKey(), "");
                    deviceConnsJson.append("{\"ip\":\"").append(escapeJson(e.getKey())).append("\",\"hostname\":\"").append(escapeJson(hostname)).append("\",\"conns\":").append(e.getValue()).append("}");
                });
        deviceConnsJson.append("]");
        metrics.put("storeos.conn.per_device", MetricValue.builder()
                .key("storeos.conn.per_device")
                .value(deviceConnsJson.toString())
                .unit("")
                .displayName("每设备连接数")
                .type(MetricType.INFO)
                .build());

        // 进程数
        List<StoreosClient.ProcessInfo> processes = client.fetchProcessList();
        metrics.put("storeos.process.count", MetricValue.builder()
                .key("storeos.process.count")
                .value(processes.size())
                .unit("")
                .displayName("进程数")
                .type(MetricType.GAUGE)
                .build());
    }

    // ===== Slow: 设备信息、磁盘、运行时间、流量统计 =====

    private void collectSlow(Map<String, MetricValue> metrics) {
        // 设备信息
        StoreosClient.SystemBoard board = client.fetchSystemBoard();
        if (board != null) {
            metrics.put("storeos.system.hostname", MetricValue.builder()
                    .key("storeos.system.hostname")
                    .value(board.hostname != null ? board.hostname : "")
                    .unit("")
                    .displayName("主机名")
                    .type(MetricType.INFO)
                    .build());
            metrics.put("storeos.system.model", MetricValue.builder()
                    .key("storeos.system.model")
                    .value(board.system != null ? board.system : "")
                    .unit("")
                    .displayName("设备型号")
                    .type(MetricType.INFO)
                    .build());
            metrics.put("storeos.system.kernel", MetricValue.builder()
                    .key("storeos.system.kernel")
                    .value(board.kernel != null ? board.kernel : "")
                    .unit("")
                    .displayName("内核版本")
                    .type(MetricType.INFO)
                    .build());
            if (board.release != null) {
                metrics.put("storeos.system.version", MetricValue.builder()
                        .key("storeos.system.version")
                        .value(board.release.description != null ? board.release.description : "")
                        .unit("")
                        .displayName("系统版本")
                        .type(MetricType.INFO)
                        .build());
                metrics.put("storeos.system.distribution", MetricValue.builder()
                        .key("storeos.system.distribution")
                        .value(board.release.distribution != null ? board.release.distribution : "")
                        .unit("")
                        .displayName("发行版")
                        .type(MetricType.INFO)
                        .build());
            }
        }

        // 系统信息（运行时间、磁盘）
        StoreosClient.SystemInfo info = client.fetchSystemInfo();
        if (info != null) {
            // 运行时间
            metrics.put("storeos.system.uptime", MetricValue.builder()
                    .key("storeos.system.uptime")
                    .value(info.uptime)
                    .unit("seconds")
                    .displayName("运行时间")
                    .type(MetricType.GAUGE)
                    .build());

            // 磁盘（root 分区）
            if (info.root != null && info.root.total > 0) {
                long usedMb = info.root.used / 1024;
                long totalMb = info.root.total / 1024;
                double diskPercent = Math.round(info.root.used * 10000.0 / info.root.total) / 100.0;

                metrics.put("storeos.disk.used", MetricValue.builder()
                        .key("storeos.disk.used")
                        .value(usedMb)
                        .unit("MB")
                        .displayName("已用磁盘")
                        .type(MetricType.GAUGE)
                        .build());
                metrics.put("storeos.disk.total", MetricValue.builder()
                        .key("storeos.disk.total")
                        .value(totalMb)
                        .unit("MB")
                        .displayName("总磁盘")
                        .type(MetricType.GAUGE)
                        .build());
                metrics.put("storeos.disk.usage", MetricValue.builder()
                        .key("storeos.disk.usage")
                        .value(diskPercent)
                        .unit("%")
                        .displayName("磁盘使用率")
                        .type(MetricType.GAUGE)
                        .build());
            }
        }

        // vnstat 流量统计
        StoreosClient.VnstatData vnstat = client.fetchVnstat();
        if (vnstat != null && vnstat.interfaces != null) {
            for (StoreosClient.VnstatInterface iface : vnstat.interfaces) {
                if ("br-lan".equals(iface.id) && iface.traffic != null && iface.traffic.total != null) {
                    metrics.put("storeos.traffic.total_rx", MetricValue.builder()
                            .key("storeos.traffic.total_rx")
                            .value(iface.traffic.total.rx)
                            .unit("KB")
                            .displayName("累计接收流量")
                            .type(MetricType.COUNTER)
                            .build());
                    metrics.put("storeos.traffic.total_tx", MetricValue.builder()
                            .key("storeos.traffic.total_tx")
                            .value(iface.traffic.total.tx)
                            .unit("KB")
                            .displayName("累计发送流量")
                            .type(MetricType.COUNTER)
                            .build());
                    break;
                }
            }
        }

        // 网口列表
        List<StoreosClient.NetworkInterface> ifaces = client.fetchNetworkInterfaces();
        if (ifaces != null && !ifaces.isEmpty()) {
            StringBuilder ifaceList = new StringBuilder();
            for (int i = 0; i < ifaces.size(); i++) {
                StoreosClient.NetworkInterface iface = ifaces.get(i);
                String name = iface.name;
                if (name == null || name.isEmpty()) continue;
                // 跳过 loopback
                if ("loopback".equals(name) || "lo".equals(name)) continue;

                if (i > 0) ifaceList.append("\n");
                String ip = "";
                if (iface.ipv4_address != null && !iface.ipv4_address.isEmpty()) {
                    ip = iface.ipv4_address.get(0).address;
                }
                String status = iface.up ? "UP" : "DOWN";
                ifaceList.append(String.format("%s [%s] %s %s", name, status, iface.proto != null ? iface.proto : "", ip));
            }
            metrics.put("storeos.net.interfaces", MetricValue.builder()
                    .key("storeos.net.interfaces")
                    .value(ifaceList.toString())
                    .unit("")
                    .displayName("网口列表")
                    .type(MetricType.INFO)
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

        if (address.startsWith("http://")) address = address.substring(7);
        if (address.startsWith("https://")) address = address.substring(8);
        while (address.endsWith("/")) address = address.substring(0, address.length() - 1);

        StoreosClient testClient = new StoreosClient(address, username, password);
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
        lastRxBytes = -1;
        lastTxBytes = -1;
        lastTrafficTime = null;
        log.info("OpenWrt plugin destroyed");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
