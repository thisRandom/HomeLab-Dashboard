# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HomeLab Dashboard is a pluggable, configurable home network monitoring platform for HomeLab/All-in-One enthusiasts. It features plugin-based data sources, modular dashboard templates, and a zero-code frontend configuration UI.

**Tech Stack**: Vue 3 + TypeScript + Spring Boot 3 + MySQL + MyBatis + Docker

## Repository Structure

- `frontend/` — Vue 3 + Vite + TypeScript SPA (dashboard, config UI, charts)
- `backend/` — Spring Boot 3 Java service (plugin engine, REST API, WebSocket)

## Development Commands

### Frontend
```bash
cd frontend
npm install
npm run dev          # Start dev server
npm run build        # Type-check + production build
npm run build-only   # Build without type-check
npm run type-check   # Run vue-tsc
```

### Backend
```bash
cd backend
./mvnw spring-boot:run        # Start dev server
./mvnw test                   # Run tests
./mvnw package                # Build jar
```

## Architecture

### Plugin System (Core Design)
The backend uses a plugin architecture for data source collection. Each plugin implements `CollectorPlugin` interface with methods: `getPluginId()`, `getConfigSchema()`, `initialize()`, `collect()`, `isHealthy()`, `destroy()`.

Plugins are discovered via Java SPI at startup. Users enable/configure plugins through the frontend Config UI. All plugin data is standardized to `CollectedMetrics` with `MetricValue` entries (GAUGE/COUNTER/INFO types).

### Data Flow
```
Plugin Layer (iKuai/OpenWrt/PVE/FnOS) 
  → Plugin Engine (lifecycle, SPI discovery)
    → Backend Service (清洗, @Scheduled tasks, WebSocket push)
      → Frontend (ECharts visualization, real-time STOMP updates)
```

### WebSocket Topics
- `/topic/metrics` — All enabled plugin metrics
- `/topic/metrics/{pluginId}` — Per-plugin metrics
- `/topic/alerts` — Alert notifications

### Database (MySQL 8.0+)
Key tables: `plugin_config`, `dashboard_layout`, `traffic_billing`, `metric_snapshot`, `config_store`

Sensitive configs are AES-256-GCM encrypted, key from `HOMELAB_SECRET_KEY` env var.

## API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/plugins` | GET | List all plugins |
| `/api/plugins/{id}/config` | PUT | Save plugin config |
| `/api/plugins/{id}/test` | POST | Test plugin connection |
| `/api/plugins/{id}/enable` | POST | Enable plugin |
| `/api/metrics/realtime` | GET | All real-time metrics |
| `/api/traffic/monthly` | GET | Monthly traffic billing |

## Key Dependencies

### Frontend
- ECharts (vue-echarts) — Data visualization
- STOMP WebSocket (@stomp/stompjs, sockjs-client) — Real-time push
- Vue Grid Layout — Dashboard drag-and-drop (Phase 2)
- Pinia — State management

### Backend
- Spring Boot WebSocket (STOMP) — Real-time push
- MyBatis — SQL mapping
- Lombok — Boilerplate reduction

## Project Status

Currently in early development (Phase 1-2). The frontend has a default Vite scaffold; backend has Spring Boot skeleton with basic dependencies. See PRD.md for full feature roadmap.

## Git Workflow

### Remote Repositories
- `origin` → https://git.lantech.top/Wjh/HomeLab-Dashboard.git (个人服务器)
- `github` → https://github.com/thisRandom/HomeLab-Dashboard.git (GitHub)

### Commit & Push Policy
**重要：不要自动推送代码到远程仓库！**

工作流程：
1. 完成代码修改后，只在本地创建 commit
2. 等待用户确认修改无误后，再执行 `git push`
3. 用户确认后，同时推送到两个远程仓库：
   ```bash
   git push origin master && git push github master
   ```

### 本地提交规范
```bash
# 查看修改状态
git status

# 添加修改文件
git add .

# 创建本地提交（不推送）
git commit -m "提交信息"

# 等待用户确认后推送
git push origin master && git push github master
```

### 分支管理
- `master` — 主分支，稳定版本
- 开发时可创建功能分支，完成后合并到 master
