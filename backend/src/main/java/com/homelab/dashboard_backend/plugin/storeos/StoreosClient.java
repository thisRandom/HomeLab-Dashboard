package com.homelab.dashboard_backend.plugin.storeos;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * OpenWrt ubus HTTP API 客户端
 * 通过 LuCI 的 /ubus JSON-RPC 端点获取系统数据
 */
@Slf4j
public class StoreosClient {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String ZERO_SESSION = "00000000000000000000000000000000";
    private static final Duration SESSION_TTL = Duration.ofSeconds(250); // token 300s 有效期，提前 50s 刷新

    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient httpClient;

    private String sessionToken;
    private Instant loginTime;

    public StoreosClient(String host, String username, String password) {
        this.baseUrl = "http://" + host + "/ubus";
        this.username = username;
        this.password = password;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ===== 认证 =====

    /**
     * 登录获取 ubus session token
     */
    public synchronized boolean login() {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("username", username);
            params.put("password", password);

            // 直接用 callUbusRaw，避免经过 ensureLoggedIn 导致无限递归
            JsonNode rawResult = callUbusRaw(ZERO_SESSION, "session", "login", params);
            // ubus 返回 [0, {ubus_rpc_session: "...", ...}]
            if (rawResult != null && rawResult.isArray() && rawResult.size() >= 2) {
                JsonNode data = rawResult.get(1);
                if (data.has("ubus_rpc_session")) {
                    this.sessionToken = data.get("ubus_rpc_session").asText();
                    this.loginTime = Instant.now();
                    log.info("OpenWrt ubus login successful, token={}", sessionToken);
                    return true;
                }
            }
            log.error("OpenWrt ubus login failed: {}", rawResult);
            return false;
        } catch (Exception e) {
            log.error("OpenWrt ubus login error", e);
            return false;
        }
    }

    /**
     * 确保 session 有效，过期则重新登录
     */
    private void ensureLoggedIn() {
        if (sessionToken == null || loginTime == null || Instant.now().minus(SESSION_TTL).isAfter(loginTime)) {
            log.info("OpenWrt session expired, re-logging in...");
            login();
        }
    }

    public boolean isSessionValid() {
        if (sessionToken == null) return false;
        try {
            JsonNode result = callUbus("session", "access", Map.of());
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 通用 ubus 调用 =====

    /**
     * 调用 ubus 方法，返回 result 数组中的 data 部分
     */
    private JsonNode callUbus(String service, String method, Map<String, Object> params) {
        ensureLoggedIn();
        JsonNode result = callUbusRaw(sessionToken, service, method, params != null ? params : Map.of());
        if (result == null) return null;
        // ubus 返回 [0, {data}]，取第二个元素
        if (result.isArray() && result.size() >= 2) {
            return result.get(1);
        }
        return result;
    }

    /**
     * 原始 ubus JSON-RPC 调用
     */
    private JsonNode callUbusRaw(String token, String service, String method, Map<String, Object> params) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", 1);
            request.put("method", "call");
            request.put("params", List.of(token, service, method, params));

            String json = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("OpenWrt ubus HTTP {}: {} {}", response.statusCode(), service, method);
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                log.warn("OpenWrt ubus error: {} - {} {}", root.get("error"), service, method);
                return null;
            }
            return root.path("result");
        } catch (Exception e) {
            log.warn("OpenWrt ubus call failed: {} {}.{} - {}", e.getMessage(), service, method, e.getMessage());
            return null;
        }
    }

    // ===== 数据获取方法 =====

    /**
     * 系统信息（内存、负载、运行时间、磁盘）
     */
    public SystemInfo fetchSystemInfo() {
        JsonNode data = callUbus("system", "info", Map.of());
        if (data == null) return null;
        return objectMapper.convertValue(data, SystemInfo.class);
    }

    /**
     * 设备信息（型号、系统版本、内核）
     */
    public SystemBoard fetchSystemBoard() {
        JsonNode data = callUbus("system", "board", Map.of());
        if (data == null) return null;
        return objectMapper.convertValue(data, SystemBoard.class);
    }

    /**
     * 网络设备统计（每个网口的 rx/tx 字节数、包数）
     */
    public Map<String, NetworkDevice> fetchNetworkDevices() {
        JsonNode data = callUbus("luci-rpc", "getNetworkDevices", Map.of());
        if (data == null) return null;
        Map<String, NetworkDevice> devices = new LinkedHashMap<>();
        data.fields().forEachRemaining(entry -> {
            devices.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), NetworkDevice.class));
        });
        return devices;
    }

    /**
     * 网络接口状态（IP、协议、运行状态）
     */
    public List<NetworkInterface> fetchNetworkInterfaces() {
        JsonNode data = callUbus("network.interface", "dump", Map.of());
        if (data == null || !data.has("interface")) return List.of();
        List<NetworkInterface> ifaces = new ArrayList<>();
        for (JsonNode iface : data.get("interface")) {
            ifaces.add(objectMapper.convertValue(iface, NetworkInterface.class));
        }
        return ifaces;
    }

    /**
     * DHCP 客户端列表
     */
    public DhcpLeases fetchDHCPLeases() {
        JsonNode data = callUbus("luci-rpc", "getDHCPLeases", Map.of());
        if (data == null) return null;
        return objectMapper.convertValue(data, DhcpLeases.class);
    }

    /**
     * ARP 邻居表（在线设备，含静态 IP 设备）
     */
    public List<ArpEntry> fetchArpTable() {
        JsonNode data = callUbus("luci-rpc", "getHostHints", Map.of());
        if (data == null) return List.of();
        List<ArpEntry> entries = new ArrayList<>();
        // getHostHints 返回 {"hosts": {"ip": {"hostname": "...", "mac": "..."}}}
        JsonNode hosts = data.get("hosts");
        if (hosts != null && hosts.isObject()) {
            hosts.fields().forEachRemaining(entry -> {
                ArpEntry arp = new ArpEntry();
                arp.ip = entry.getKey();
                JsonNode info = entry.getValue();
                if (info != null) {
                    arp.hostname = info.has("hostname") ? info.get("hostname").asText("") : "";
                    arp.mac = info.has("mac") ? info.get("mac").asText("") : "";
                }
                entries.add(arp);
            });
        }
        return entries;
    }

    /**
     * 进程列表
     */
    public List<ProcessInfo> fetchProcessList() {
        JsonNode data = callUbus("luci", "getProcessList", Map.of());
        if (data == null || !data.has("result")) return List.of();
        List<ProcessInfo> processes = new ArrayList<>();
        for (JsonNode p : data.get("result")) {
            processes.add(objectMapper.convertValue(p, ProcessInfo.class));
        }
        return processes;
    }

    /**
     * 存储设备信息
     */
    public Map<String, BlockDevice> fetchBlockDevices() {
        JsonNode data = callUbus("luci", "getBlockDevices", Map.of());
        if (data == null) return null;
        Map<String, BlockDevice> devices = new LinkedHashMap<>();
        data.fields().forEachRemaining(entry -> {
            devices.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), BlockDevice.class));
        });
        return devices;
    }

    /**
     * 连接追踪列表（用于统计每设备连接数）
     */
    public List<ConntrackEntry> fetchConntrackList() {
        JsonNode data = callUbus("luci", "getConntrackList", Map.of());
        if (data == null) return List.of();
        // getConntrackList 返回 {"result": [...]}，callUbus 提取后 data 可能是整个对象
        JsonNode list = data.has("result") ? data.get("result") : data;
        if (!list.isArray()) return List.of();
        List<ConntrackEntry> entries = new ArrayList<>();
        for (JsonNode entry : list) {
            ConntrackEntry e = new ConntrackEntry();
            e.src = entry.has("src") ? entry.get("src").asText("") : "";
            e.dst = entry.has("dst") ? entry.get("dst").asText("") : "";
            e.layer3 = entry.has("layer3") ? entry.get("layer3").asText("") : "";
            entries.add(e);
        }
        return entries;
    }

    /**
     * 连接追踪计数
     */
    public String fetchConntrackCount() {
        JsonNode data = callUbus("file", "read", Map.of("path", "/proc/sys/net/netfilter/nf_conntrack_count"));
        if (data == null) return null;
        JsonNode fileData = data.get("data");
        return fileData != null ? fileData.asText().trim() : null;
    }

    /**
     * 连接追踪最大值
     */
    public String fetchConntrackMax() {
        JsonNode data = callUbus("file", "read", Map.of("path", "/proc/sys/net/netfilter/nf_conntrack_max"));
        if (data == null) return null;
        JsonNode fileData = data.get("data");
        return fileData != null ? fileData.asText().trim() : null;
    }

    /**
     * vnstat 流量统计
     */
    public VnstatData fetchVnstat() {
        JsonNode data = callUbus("file", "exec", Map.of(
                "command", "/usr/bin/vnstat",
                "params", List.of("--json", "a"),
                "env", List.of("PATH=/usr/bin:/bin")
        ));
        if (data == null) return null;
        String stdout = data.has("stdout") ? data.get("stdout").asText() : null;
        if (stdout == null || stdout.isEmpty()) return null;
        try {
            return objectMapper.readValue(stdout, VnstatData.class);
        } catch (Exception e) {
            log.warn("Failed to parse vnstat output: {}", e.getMessage());
            return null;
        }
    }

    // ===== DTOs =====

    @Data
    public static class SystemInfo {
        public long localtime;
        public long uptime;
        public List<Double> load;
        public MemoryInfo memory;
        public DiskInfo root;
        public DiskInfo tmp;
        public DiskInfo swap;
    }

    @Data
    public static class MemoryInfo {
        public long total;
        public long free;
        public long shared;
        public long buffered;
        public long available;
        public long cached;
    }

    @Data
    public static class DiskInfo {
        public long total;
        public long free;
        public long used;
        public long avail;
    }

    @Data
    public static class SystemBoard {
        public String kernel;
        public String hostname;
        public String system;
        public String model;
        public String board_name;
        public String rootfs_type;
        public ReleaseInfo release;
    }

    @Data
    public static class ReleaseInfo {
        public String distribution;
        public String version;
        public String revision;
        public String target;
        public String description;
        public String builddate;
    }

    @Data
    public static class NetworkDevice {
        public String name;
        public boolean wireless;
        public boolean up;
        public int mtu;
        public String mac;
        public String devtype;
        public Stats stats;
        public LinkInfo link;
        public List<IpAddr> ipaddrs;
    }

    @Data
    public static class Stats {
        public long rx_bytes;
        public long tx_bytes;
        public long rx_packets;
        public long tx_packets;
        public long rx_errors;
        public long tx_errors;
        public long rx_dropped;
        public long tx_dropped;
    }

    @Data
    public static class LinkInfo {
        public int speed;
        public String duplex;
        public boolean carrier;
    }

    @Data
    public static class IpAddr {
        public String address;
        public String netmask;
    }

    @Data
    public static class NetworkInterface {
        @com.fasterxml.jackson.annotation.JsonProperty("interface")
        public String name;
        public boolean up;
        public String proto;
        public String device;
        public long uptime;
        public List<Ipv4Address> ipv4_address;
    }

    @Data
    public static class Ipv4Address {
        public String address;
        public int mask;
    }

    @Data
    public static class DhcpLeases {
        public List<DhcpLease> dhcp_leases;
        public List<DhcpLease> dhcp6_leases;
    }

    @Data
    public static class DhcpLease {
        public String hostname;
        public String ipaddr;
        public String macaddr;
        public long leasetime;
    }

    @Data
    public static class ArpEntry {
        public String ip;
        public String hostname;
        public String mac;
    }

    @Data
    public static class ConntrackEntry {
        public String src;
        public String dst;
        public String layer3;
    }

    @Data
    public static class ProcessInfo {
        public String PID;
        public String USER;
        public String COMMAND;
        @com.fasterxml.jackson.annotation.JsonProperty("%CPU")
        public String cpu;
        @com.fasterxml.jackson.annotation.JsonProperty("%MEM")
        public String mem;
    }

    @Data
    public static class BlockDevice {
        public String dev;
        public long size;
        public String label;
        public String version;
        public String mount;
        public String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VnstatData {
        public String vnstatversion;
        public String jsonversion;
        public List<VnstatInterface> interfaces;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VnstatInterface {
        public String id;
        public String nick;
        public VnstatTraffic traffic;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VnstatTraffic {
        public VnstatTotal total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VnstatTotal {
        public long rx;
        public long tx;
    }
}
