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
