# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HomeLab Dashboard backend — Spring Boot 3 Java service providing the plugin engine, REST APIs, WebSocket push, and MySQL persistence for the home network monitoring platform.

## Commands

```bash
./mvnw spring-boot:run        # Start dev server
./mvnw test                   # Run all tests
./mvnw package                # Build executable jar (skip tests)
./mvnw clean install          # Clean build
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Architecture

### Tech Stack
- **Framework**: Spring Boot 3.5 (Java 17)
- **Build**: Maven with wrapper
- **ORM**: MyBatis (mybatis-spring-boot-starter 3.0.5) — 使用注解方式写 SQL（@Select/@Insert/@Update/@Delete），不使用 XML mapper 文件
- **Database**: MySQL 8.0+ (utf8mb4)
- **WebSocket**: Spring WebSocket with STOMP protocol
- **Utilities**: Lombok

### Package Structure
```
com.homelab.dashboard_backend/
├── DashboardBackendApplication.java   # Entry point
├── plugin/         # Plugin engine (to be built)
│   ├── CollectorPlugin.java         # Plugin interface
│   ├── CollectedMetrics.java        # Standard metrics model
│   └── PluginEngine.java            # SPI discovery, lifecycle
├── config/         # REST API for plugin configuration
├── controller/     # API controllers
├── service/        # Business logic
├── mapper/         # MyBatis mappers
├── model/          # Entity classes
└── websocket/      # STOMP configuration
```

### Plugin System
The core architectural pattern. Plugins implement `CollectorPlugin` interface:
- `getPluginId()` / `getDisplayName()` — Identity
- `getConfigSchema()` — Returns `List<ConfigField>` for dynamic form rendering
- `initialize(Map<String, String> config)` — Inject user config at runtime
- `collect()` — Returns `CollectedMetrics` with standardized `MetricValue` entries
- `isHealthy()` / `destroy()` — Lifecycle management

Plugins are discovered via Java SPI at startup and can be enabled/disabled at runtime without restart.

### Data Model
`CollectedMetrics` contains:
- `pluginId` — Source plugin
- `collectedAt` — Timestamp
- `metrics` — Map of `MetricValue` (key, value, unit, displayName, type=GAUGE/COUNTER/INFO)

### WebSocket STOMP Topics
- `/topic/metrics` — All plugin metrics broadcast
- `/topic/metrics/{pluginId}` — Per-plugin metrics
- `/topic/alerts` — Alert notifications

### Database Tables
- `plugin_config` — Plugin settings (encrypted config_json)
- `dashboard_layout` — Dashboard template/layout config
- `traffic_billing` — Daily traffic records (upload/download bytes)
- `metric_snapshot` — Historical metric snapshots (JSON)
- `config_store` — System config (encrypted values)

### Security
Sensitive config values are AES-256-GCM encrypted. Encryption key read from `HOMELAB_SECRET_KEY` environment variable.

## Configuration

Main config: `src/main/resources/application.yaml`

Currently minimal — database connection, server port, and app name will be added as development progresses.
