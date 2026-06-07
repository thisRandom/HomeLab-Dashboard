package com.homelab.dashboard_backend.plugin.ikuai;

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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
public class IkuaiClient {

    private static final String LOGIN_PATH = "/Action/login";
    private static final String CALL_PATH = "/Action/call";
    private static final int LOGIN_SUCCESS = 10000;
    // V3: Result=10014, V4: code=1008
    private static final int SESSION_EXPIRED_V3 = 10014;
    private static final int SESSION_EXPIRED_V4 = 1008;
    // V3: Result=30000, V4: code=0
    private static final int CALL_SUCCESS_V3 = 30000;
    private static final int CALL_SUCCESS_V4 = 0;
    private static final String SALT = "salt_11";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final String baseUrl;
    private final String username;
    private final String password;

    private HttpClient httpClient;
    private Instant loginTime;
    private static final Duration SESSION_TTL = Duration.ofMinutes(25);

    public IkuaiClient(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.password = password;
        this.httpClient = createHttpClient();
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new java.net.CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public synchronized boolean login() {
        try {
            String md5Password = md5Hex(password);
            String base64Password = Base64.getEncoder().encodeToString((SALT + password).getBytes(StandardCharsets.UTF_8));

            Map<String, String> body = new LinkedHashMap<>();
            body.put("username", username);
            body.put("passwd", md5Password);
            body.put("pass", base64Password);

            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + LOGIN_PATH))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());

            int result = node.path("Result").asInt(0);
            if (result == LOGIN_SUCCESS) {
                loginTime = Instant.now();
                log.info("iKuai login successful: {}", baseUrl);
                return true;
            } else {
                log.error("iKuai login failed: {} - {}", result, node.path("ErrMsg").asText());
                return false;
            }
        } catch (Exception e) {
            log.error("iKuai login error", e);
            return false;
        }
    }

    public synchronized boolean isSessionValid() {
        if (loginTime == null) return false;
        if (Instant.now().isAfter(loginTime.plus(SESSION_TTL))) return false;

        try {
            Map<String, Object> param = Map.of("TYPE", "mod_passwd");
            CallReq req = new CallReq("webuser", "show", param);
            String json = objectMapper.writeValueAsString(req);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + CALL_PATH))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            int result = node.path("Result").asInt(0);
            return result != SESSION_EXPIRED;
        } catch (Exception e) {
            log.warn("Session check failed", e);
            return false;
        }
    }

    public synchronized void ensureLoggedIn() {
        if (!isSessionValid()) {
            log.info("Session expired or invalid, re-logging in...");
            httpClient = createHttpClient();
            login();
        }
    }

    public HomepageData fetchHomepage() {
        ensureLoggedIn();
        CallReq req = new CallReq("homepage", "show", Map.of("TYPE", "sysstat"));
        JsonNode data = callApi(req);
        if (data != null) {
            log.debug("Homepage raw sysstat: {}", data.path("sysstat"));
        }
        HomepageData result = new HomepageData();
        if (data != null) {
            result.sysstat = objectMapper.convertValue(data.path("sysstat"), HomepageData.Sysstat.class);
            result.acStatus = objectMapper.convertValue(data.path("ac_status"), HomepageData.AcStatus.class);
        }
        return result;
    }

    public List<LanDevice> fetchLanDevices() {
        ensureLoggedIn();
        List<LanDevice> devices = new ArrayList<>();

        CallReq req4 = new CallReq("monitor_lanip", "show", Map.of("TYPE", "data"));
        JsonNode data4 = callApi(req4);
        if (data4 != null) {
            log.debug("LAN IPv4 raw data: {}", data4);
        }
        if (data4 != null && data4.has("data")) {
            for (JsonNode item : data4.path("data")) {
                devices.add(objectMapper.convertValue(item, LanDevice.class));
            }
        }

        CallReq req6 = new CallReq("monitor_lanipv6", "show", Map.of("TYPE", "data"));
        JsonNode data6 = callApi(req6);
        if (data6 != null && data6.has("data")) {
            for (JsonNode item : data6.path("data")) {
                devices.add(objectMapper.convertValue(item, LanDevice.class));
            }
        }

        return devices;
    }

    public IfaceData fetchIfaceData() {
        ensureLoggedIn();
        CallReq req = new CallReq("monitor_iface", "show", Map.of("TYPE", "iface_check,iface_stream"));
        JsonNode data = callApi(req);
        IfaceData result = new IfaceData();
        if (data != null) {
            if (data.has("iface_check")) {
                for (JsonNode item : data.path("iface_check")) {
                    result.ifaceCheck.add(objectMapper.convertValue(item, IfaceCheck.class));
                }
            }
            if (data.has("iface_stream")) {
                for (JsonNode item : data.path("iface_stream")) {
                    result.ifaceStream.add(objectMapper.convertValue(item, IfaceStream.class));
                }
            }
        }
        return result;
    }

    private JsonNode callApi(CallReq req) {
        try {
            String json = objectMapper.writeValueAsString(req);
            log.debug("[{}] Request: {}", req.funcName, json);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + CALL_PATH))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            log.debug("[{}] Response: {}", req.funcName, response.body());
            int result = node.path("Result").asInt(0);

            if (result == SESSION_EXPIRED) {
                log.warn("Session expired during API call, re-logging in...");
                ensureLoggedIn();
                return callApi(req);
            }

            if (result != CALL_SUCCESS) {
                log.error("iKuai API call failed: {} - {}", result, node.path("ErrMsg").asText());
                return null;
            }

            return node.path("Data");
        } catch (Exception e) {
            log.error("iKuai API call error: {}", req.funcName, e);
            return null;
        }
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ===== Request DTO =====

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class CallReq {
        @JsonProperty("func_name")
        String funcName;
        String action;
        Object param;
    }

    // ===== Homepage DTOs =====

    @Data
    public static class HomepageData {
        public Sysstat sysstat;
        @JsonProperty("ac_status")
        public AcStatus acStatus;

        @Data
        public static class Sysstat {
            public List<String> cpu;
            @JsonProperty("cputemp")
            public List<Integer> cpuTemp;
            public List<String> freq;
            public String hostname;
            public Memory memory;
            @JsonProperty("online_user")
            public OnlineUser onlineUser;
            public Stream stream;
            public int uptime;
            @JsonProperty("verinfo")
            public VerInfo verInfo;
        }

        @Data
        public static class Memory {
            public long total;
            public long available;
            public long free;
            public long cached;
            public long buffers;
            public String used;
        }

        @Data
        public static class OnlineUser {
            public int count;
            @JsonProperty("count_2g")
            public int count2g;
            @JsonProperty("count_5g")
            public int count5g;
            public int countWired;
            public int countWireless;
        }

        @Data
        public static class Stream {
            @JsonProperty("connect_num")
            public int connectNum;
            public int upload;
            public int download;
            @JsonProperty("total_up")
            public long totalUp;
            @JsonProperty("total_down")
            public long totalDown;
        }

        @Data
        public static class VerInfo {
            public String modelname;
            public String verstring;
            public String version;
            @JsonProperty("build_date")
            public long buildDate;
            public String arch;
            public String sysbit;
        }

        @Data
        public static class AcStatus {
            @JsonProperty("ap_count")
            public int apCount;
            @JsonProperty("ap_online")
            public int apOnline;
        }
    }

    // ===== LAN Device DTOs =====

    @Data
    public static class LanDevice {
        public String mac;
        public String hostname;
        public String comment;
        @JsonProperty("ip_addr")
        public String ipAddr;
        public int id;
        public String uprate;
        public String downrate;
        public int upload;
        public int download;
        @JsonProperty("total_up")
        public long totalUp;
        @JsonProperty("total_down")
        public long totalDown;
        public String signal;
        public String ssid;
        public String frequencies;
        @JsonProperty("client_type")
        public String clientType;
        @JsonProperty("client_device")
        public String clientDevice;
        @JsonProperty("link_addr")
        public String linkAddr;
        public String apname;
    }

    // ===== WAN Interface DTOs =====

    @Data
    public static class IfaceData {
        public List<IfaceCheck> ifaceCheck = new ArrayList<>();
        public List<IfaceStream> ifaceStream = new ArrayList<>();
    }

    @Data
    public static class IfaceCheck {
        public int id;
        @JsonProperty("interface")
        public String iface;
        @JsonProperty("parent_interface")
        public String parentInterface;
        @JsonProperty("ip_addr")
        public String ipAddr;
        public String gateway;
        public String internet;
        public String updatetime;
        public String result;
        public String errmsg;
        public String comment;
    }

    @Data
    public static class IfaceStream {
        @JsonProperty("interface")
        public String iface;
        public String comment;
        @JsonProperty("ip_addr")
        public String ipAddr;
        @JsonProperty("connect_num")
        public String connectNum;
        public int upload;
        public int download;
        @JsonProperty("total_up")
        public long totalUp;
        @JsonProperty("total_down")
        public long totalDown;
    }
}
