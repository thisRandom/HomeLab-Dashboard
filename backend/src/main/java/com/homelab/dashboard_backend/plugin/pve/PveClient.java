package com.homelab.dashboard_backend.plugin.pve;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PVE (Proxmox Virtual Environment) API 客户端
 * 使用 Token 认证方式调用 PVE REST API
 */
@Slf4j
public class PveClient {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String baseUrl;       // e.g. https://192.168.10.254:8006/api2/json
    private final String nodeName;      // e.g. "pve"
    private final String apiToken;      // e.g. "user@realm!tokenid=uuid"
    private final String sshHost;       // PVE host for SSH, e.g. "192.168.10.254"
    private final int sshPort;          // SSH port, e.g. 22
    private final String sshUser;       // SSH user, e.g. "root"
    private final String sshPassword;   // SSH password (null if using key)
    private final String sshKeyPath;    // SSH private key path (null if using password)

    private final HttpClient httpClient;

    // SSH 长连接
    private Session sshSession;
    private final Object sshLock = new Object();
    private final java.util.concurrent.ScheduledExecutorService sshKillScheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ssh-kill-timer");
                t.setDaemon(true);
                return t;
            });

    public PveClient(String host, int port, String nodeName, String apiToken,
                     String sshHost, int sshPort, String sshUser, String sshPassword, String sshKeyPath) {
        this.baseUrl = String.format("https://%s:%d/api2/json", host, port);
        this.nodeName = nodeName;
        this.apiToken = apiToken;
        this.sshHost = sshHost;
        this.sshPort = sshPort;
        this.sshUser = sshUser;
        this.sshPassword = sshPassword;
        this.sshKeyPath = sshKeyPath;
        this.httpClient = createHttpClient();
    }

    private HttpClient createHttpClient() {
        try {
            // Trust all certificates (PVE uses self-signed certs by default)
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("Failed to create SSL context, falling back to default", e);
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
    }

    /**
     * 测试连接是否可达（不保存状态）
     */
    public boolean testConnection() {
        try {
            JsonNode result = callGet("/nodes/" + nodeName + "/status");
            return result != null && result.has("cpu");
        } catch (Exception e) {
            log.error("PVE connection test failed", e);
            return false;
        }
    }

    /**
     * 获取节点状态
     */
    public NodeStatus getNodeStatus() {
        JsonNode data = callGet("/nodes/" + nodeName + "/status");
        if (data == null) return null;
        return objectMapper.convertValue(data, NodeStatus.class);
    }

    /**
     * 获取集群资源列表（VM/CT）
     */
    public List<ClusterResource> getClusterResources() {
        JsonNode data = callGet("/cluster/resources?type=vm");
        if (data == null) return List.of();
        List<ClusterResource> resources = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode item : data) {
                resources.add(objectMapper.convertValue(item, ClusterResource.class));
            }
        }
        return resources;
    }

    /**
     * 获取 QEMU VM 详细状态
     */
    public VmStatus getVmStatus(String vmid) {
        JsonNode data = callGet("/nodes/" + nodeName + "/qemu/" + vmid + "/status/current");
        if (data == null) return null;
        return objectMapper.convertValue(data, VmStatus.class);
    }

    /**
     * 获取 LXC 容器详细状态
     */
    public VmStatus getLxcStatus(String vmid) {
        JsonNode data = callGet("/nodes/" + nodeName + "/lxc/" + vmid + "/status/current");
        if (data == null) return null;
        return objectMapper.convertValue(data, VmStatus.class);
    }

    /**
     * 在 PVE 节点上执行命令（通过 API execute 端点）
     * 返回命令输出文本，失败返回 null
     */
    public String executeCommand(String command) {
        try {
            String url = baseUrl + "/nodes/" + nodeName + "/execute";
            String body = "command=" + java.net.URLEncoder.encode(command, "UTF-8");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Authorization", "PVEAPIToken=" + apiToken)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("PVE execute [{}]: HTTP {} - {}", command, response.statusCode(), response.body());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data");
                return data.isTextual() ? data.asText() : data.toString();
            }
            return null;
        } catch (Exception e) {
            log.warn("PVE execute failed: {} - {}", command, e.getMessage());
            return null;
        }
    }

    /**
     * 建立 SSH 长连接（在 initialize 时调用一次）
     */
    public void connectSsh() {
        if (sshHost == null || sshHost.isEmpty()) return;
        synchronized (sshLock) {
            if (sshSession != null && sshSession.isConnected()) return;
            try {
                JSch jsch = new JSch();
                if (sshKeyPath != null && !sshKeyPath.isEmpty()) {
                    jsch.addIdentity(sshKeyPath);
                }
                sshSession = jsch.getSession(sshUser != null ? sshUser : "root", sshHost, sshPort);
                sshSession.setServerAliveInterval(5000);
                sshSession.setServerAliveCountMax(3);
                if (sshPassword != null && !sshPassword.isEmpty()) {
                    sshSession.setPassword(sshPassword);
                }
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                sshSession.setConfig(config);
                sshSession.connect(5000);
                log.info("SSH connected to {}:{}", sshHost, sshPort);
            } catch (Exception e) {
                log.warn("SSH connect failed to {}:{} - {}", sshHost, sshPort, e.getMessage());
                sshSession = null;
            }
        }
    }

    /**
     * 断开 SSH 长连接（在 destroy 时调用）
     */
    public void disconnectSsh() {
        synchronized (sshLock) {
            if (sshSession != null) {
                if (sshSession.isConnected()) {
                    sshSession.disconnect();
                    log.info("SSH disconnected from {}:{}", sshHost, sshPort);
                }
                sshSession = null;
            }
        }
    }

    /**
     * 彻底关闭 SSH 客户端资源（在 plugin destroy 时调用）
     */
    public void destroy() {
        disconnectSsh();
        sshKillScheduler.shutdownNow();
    }

    /**
     * 通过 SSH 在 PVE 节点上执行命令（复用长连接）
     * 返回命令输出文本，失败返回 null
     */
    public String sshExecute(String command) {
        if (sshHost == null || sshHost.isEmpty()) return null;

        synchronized (sshLock) {
            ChannelExec channel = null;
            java.util.concurrent.ScheduledFuture<?> killTimer = null;
            try {
                // 连接不存在或已断开，自动重连
                if (sshSession == null || !sshSession.isConnected()) {
                    log.info("SSH session lost, reconnecting...");
                    disconnectSsh();
                    connectSsh();
                    if (sshSession == null || !sshSession.isConnected()) {
                        return null;
                    }
                }

                channel = (ChannelExec) sshSession.openChannel("exec");
                channel.setCommand(command);
                channel.setInputStream(null);
                channel.setErrStream(null);

                java.io.InputStream in = channel.getInputStream();
                channel.connect(3000);

                // 2 秒超时保护：超时自动关闭 channel，阻塞的 read 会抛异常退出
                final ChannelExec ch = channel;
                killTimer = sshKillScheduler.schedule(() -> {
                    try { if (ch.isConnected()) ch.disconnect(); } catch (Exception ignored) {}
                }, 2, java.util.concurrent.TimeUnit.SECONDS);

                StringBuilder output = new StringBuilder();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    output.append(new String(buffer, 0, len));
                }

                return output.toString().trim();
            } catch (Exception e) {
                log.warn("SSH execute failed on {}: {} - {}", sshHost, command, e.getMessage());
                // 连接异常时断开，下次调用会自动重连
                disconnectSsh();
                return null;
            } finally {
                if (killTimer != null) killTimer.cancel(false);
                if (channel != null && channel.isConnected()) {
                    try { channel.disconnect(); } catch (Exception ignored) {}
                }
            }
        }
    }

    // ===== HTTP =====

    private JsonNode callGet(String path) {
        try {
            String url = baseUrl + path;
            log.info("PVE GET: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Authorization", "PVEAPIToken=" + apiToken)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("PVE Response [{}]: {}", response.statusCode(), response.body());

            if (response.statusCode() != 200) {
                log.warn("PVE API returned HTTP {}: {} - {}", response.statusCode(), path, response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("data");
        } catch (Exception e) {
            log.warn("PVE API call failed: {} - {}", path, e.getMessage());
            return null;
        }
    }

    // ===== DTOs =====

    @Data
    public static class NodeStatus {
        public double cpu;
        public long uptime;
        public MemoryInfo memory;
        public DiskInfo rootfs;
        public CpuInfo cpuinfo;
        public List<Double> loadavg;
        public String node;
        public String status;
    }

    @Data
    public static class MemoryInfo {
        public long used;
        public long total;
        public long free;
    }

    @Data
    public static class DiskInfo {
        public long used;
        public long total;
    }

    @Data
    public static class CpuInfo {
        public int cores;
        public String model;
    }

    @Data
    public static class ClusterResource {
        public int vmid;
        public String name;
        public String status;       // running, stopped, paused
        public String type;         // qemu, lxc
        public String node;
        public double cpu;
        public long mem;
        public long maxmem;
        public long disk;
        public long maxdisk;
        public long netin;
        public long netout;
        public long diskread;
        public long diskwrite;
    }

    @Data
    public static class VmStatus {
        public String status;
        public double cpu;
        public int cpus;
        public long mem;
        public long maxmem;
        public long maxdisk;
        public long diskread;
        public long diskwrite;
        public long netin;
        public long netout;
        public int pid;
        @JsonProperty("running-machine")
        public String runningMachine;
    }
}
