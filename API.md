# HomeLab Dashboard API 接口文档

> Base URL: `http://localhost:8080`
>
> 前端通过 Vite 代理 `/api` → 后端路径（去掉 `/api` 前缀）
>
> Nginx 配置示例：`location /api/ { proxy_pass http://backend:8080/; }`

---

## 通用响应结构

所有指标接口返回 `Map<pluginId, CollectedMetrics>`，禁用的插件不会出现在结果中。

```json
{
  "ikuai": {
    "pluginId": "ikuai",
    "collectedAt": "2026-05-31T12:00:00Z",
    "metrics": {
      "<metric_key>": {
        "key": "string",
        "value": "number | string",
        "unit": "string",
        "displayName": "string",
        "type": "GAUGE | COUNTER | INFO"
      }
    }
  }
}
```

---

## 1. 实时指标（高频）

```
GET /metrics/realtime
```

**描述**: 采集速率类指标，适合高频轮询（1~3 秒）。

**iKuai 返回指标**:

| 指标 key | 显示名 | 单位 | 类型 |
|----------|--------|------|------|
| stream.upload | 上行速率 | bps | GAUGE |
| stream.download | 下行速率 | bps | GAUGE |
| stream.connect_num | 连接数 | - | GAUGE |
| cpu.usage | CPU 使用率 | % | GAUGE |
| cpu.temp | CPU 温度 | °C | GAUGE |

**响应示例**:

```json
{
  "ikuai": {
    "pluginId": "ikuai",
    "collectedAt": "2026-05-31T12:00:00Z",
    "metrics": {
      "stream.upload": { "key": "stream.upload", "value": 8157, "unit": "bps", "displayName": "上行速率", "type": "GAUGE" },
      "cpu.usage": { "key": "cpu.usage", "value": 2, "unit": "%", "displayName": "CPU 使用率", "type": "GAUGE" }
    }
  }
}
```

---

## 2. 频繁指标（中频）

```
GET /metrics/frequent
```

**描述**: 采集设备列表和接口状态，适合中频轮询（10~30 秒）。

**iKuai 返回指标**:

| 指标 key | 显示名 | 单位 | 类型 |
|----------|--------|------|------|
| lan.device_count | 在线设备数 | - | GAUGE |
| lan.devices | 在线设备列表 | - | INFO |
| wan.{name}.status | WAN 接口状态 | - | INFO |
| wan.{name}.upload | WAN 上行速率 | bps | GAUGE |
| wan.{name}.download | WAN 下行速率 | bps | GAUGE |

---

## 3. 慢速指标（低频）

```
GET /metrics/slow
```

**描述**: 采集系统信息和累计数据，适合低频轮询（60~300 秒）。

**iKuai 返回指标**:

| 指标 key | 显示名 | 单位 | 类型 |
|----------|--------|------|------|
| memory.total | 总内存 | MB | GAUGE |
| memory.used | 已用内存 | MB | GAUGE |
| memory.usage | 内存使用率 | % | GAUGE |
| stream.total_up | 累计上行流量 | bytes | COUNTER |
| stream.total_down | 累计下行流量 | bytes | COUNTER |
| online_user.count | 在线用户数 | - | GAUGE |
| system.uptime | 运行时间 | seconds | GAUGE |
| system.hostname | 主机名 | - | INFO |
| system.version | 固件版本 | - | INFO |
| system.model | 设备型号 | - | INFO |

---

## 4. 插件管理

### 插件列表

```
GET /plugins
```

### 读取插件配置

```
GET /plugins/{pluginId}/config
```

### 保存插件配置

```
PUT /plugins/{pluginId}/config
```

请求体: `{"address": "http://...", "username": "admin", "password": "xxx"}`

### 启用/禁用插件

```
POST /plugins/{pluginId}/enable
```

请求体: `{"enabled": true}`

### 重载插件

```
POST /metrics/{pluginId}/reload
```

---

## 前端轮询建议

| 接口 | 建议轮询间隔 | 用途 |
|------|-------------|------|
| `/metrics/realtime` | 1~3 秒 | 仪表盘速率仪表盘、CPU 仪表盘 |
| `/metrics/frequent` | 10~30 秒 | 设备列表、WAN 状态卡片 |
| `/metrics/slow` | 60~300 秒 | 内存信息、版本信息、累计流量 |
