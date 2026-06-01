# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HomeLab Dashboard frontend — Vue 3 + TypeScript + Vite single-page application for the home network monitoring dashboard.

## Commands

```bash
npm install          # Install dependencies
npm run dev          # Start Vite dev server
npm run build        # Type-check + production build
npm run build-only   # Build without type-check
npm run type-check   # Run vue-tsc type checking
npm run preview      # Preview production build
```

## Architecture

### Tech Stack
- **Framework**: Vue 3 (Composition API with `<script setup>`)
- **Language**: TypeScript
- **Build Tool**: Vite 7
- **State Management**: Pinia
- **Routing**: Vue Router 4 (history mode)
- **Charts**: ECharts via vue-echarts
- **WebSocket**: STOMP protocol via @stomp/stompjs + sockjs-client
- **Grid Layout**: vue-grid-layout (for dashboard drag-and-drop)

### Project Structure
```
src/
├── main.ts           # App entry, Pinia + Router setup
├── App.vue           # Root component
├── router/           # Vue Router configuration
├── stores/           # Pinia state stores
└── components/       # Vue components (to be built)
```

### Path Alias
`@` maps to `./src` (configured in vite.config.ts)

### Node Version
Requires Node.js `^20.19.0 || >=22.12.0`

## Development Notes

- UI is dark-mode focused (dashboard/monitoring style)
- Charts use ECharts — refer to vue-echarts docs for component API
- WebSocket connects to Spring Boot backend STOMP endpoint
- Dashboard modules are card-based; each card checks its required plugin data source
- Phase 2 will add Vue Grid Layout for drag-and-drop dashboard customization

## Card Registration

### 卡片开发规范

所有 Dashboard 卡片都位于 `src/views/Dashboard/{pluginId}/cards/` 目录下。

**重要：新增或修改卡片后，必须在 `DashboardView.vue` 中注册！**

### 注册步骤

1. **创建卡片组件**
   - 位置：`src/views/Dashboard/{pluginId}/cards/{CardName}.vue`
   - Props 接口：`{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }`
   - 使用 `computed` 处理 metrics 数据
   - 支持深色/浅色主题

2. **在 DashboardView.vue 中导入**
   ```typescript
   import { CardName } from './Dashboard/{pluginId}/cards/{CardName}.vue'
   ```

3. **添加到 cardDefs 数组**
   ```typescript
   { 
     type: 'cardType',           // 唯一标识符
     title: '卡片标题',           // 显示名称
     icon: '🎯',                // 图标
     w: 1,                      // 宽度（网格单位）
     h: 1,                      // 高度（网格单位）
     freq: 'realtime',          // 数据频率：realtime | frequent | slow
     pluginId: 'ikuai',         // 所属插件ID
     component: markRaw(CardName) // 组件引用
   }
   ```

### 卡片类型定义

```typescript
interface CardDef {
  type: string          // 卡片唯一标识
  title: string         // 显示标题
  icon: string          // 图标 emoji
  w: number            // 宽度（1-12 网格单位）
  h: number            // 高度（网格单位）
  freq: 'realtime' | 'frequent' | 'slow'  // 数据更新频率
  pluginId: string     // 所属插件ID
  component: ReturnType<typeof markRaw>  // Vue 组件
}
```

### 数据频率说明

- `realtime`：每 2 秒更新（实时数据）
- `frequent`：每 30 秒更新（频繁数据）
- `slow`：每 5 分钟更新（慢速数据）

### 现有卡片示例

| 卡片 | 类型 | 尺寸 | 频率 | 插件 |
|------|------|------|------|------|
| TrafficCard | traffic | 1×1 | slow | ikuai |
| SpeedCard | speed | 1×1 | realtime | ikuai |
| TemperatureCard | temperature | 1×1 | realtime | ikuai |
| SystemStatsCard | systemStats | 2×1 | realtime | ikuai |
| RealtimeSpeedCard | realtimeSpeed | 3×2 | realtime | ikuai |
| DeviceListCard | deviceList | 2×3 | slow | ikuai |
