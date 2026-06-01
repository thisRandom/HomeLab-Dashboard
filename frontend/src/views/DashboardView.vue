<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, markRaw } from 'vue'
import { getCards, saveCards, setEditing } from '../api/dashboard'
import { getRealtime, getFrequent, getSlow } from '../api/metrics'
import { getAllConfig } from '../api/config'
import { listPlugins } from '../api/plugin'
import type { DashboardCard } from '../api/dashboard'
import type { MetricValue } from '../api/metrics'
import type { PluginRecord } from '../api/plugin'
import TrafficCard from './Dashboard/ikuai/cards/TrafficCard.vue'
import SpeedCard from './Dashboard/ikuai/cards/SpeedCard.vue'
import RealtimeSpeedCard from './Dashboard/ikuai/cards/RealtimeSpeedCard.vue'
import DeviceListCard from './Dashboard/ikuai/cards/DeviceListCard.vue'
import SystemStatsCard from './Dashboard/ikuai/cards/SystemStatsCard.vue'
import TemperatureCard from './Dashboard/ikuai/cards/TemperatureCard.vue'
import WanStatusCard from './Dashboard/ikuai/cards/WanStatusCard.vue'
import CpuTempCard from './Dashboard/ikuai/cards/CpuTempCard.vue'
import UptimeCard from './Dashboard/ikuai/cards/UptimeCard.vue'
import SystemOverviewCard from './Dashboard/ikuai/cards/SystemOverviewCard.vue'
import DeviceTrafficCard from './Dashboard/ikuai/cards/DeviceTrafficCard.vue'

const COLS = 12
const GAP = 2
const TEMPLATE = 'network-overview'
const DEFAULT_BG = 'linear-gradient(135deg, #0a0a0f 0%, #0d1117 50%, #0a0a0f 100%)'

interface CardDef {
  type: string
  title: string
  icon: string
  w: number
  h: number
  freq: 'realtime' | 'frequent' | 'slow'
  pluginId: string
  component: ReturnType<typeof markRaw>
}

interface PlacedCard {
  id: string
  type: string
  title: string
  x: number
  y: number
  w: number
  h: number
}

const cardDefs: CardDef[] = [
  { type: 'traffic', title: '累计流量', icon: '📡', w: 1, h: 1, freq: 'slow', pluginId: 'ikuai', component: markRaw(TrafficCard) },
  { type: 'speed',   title: '实时速率', icon: '⚡', w: 1, h: 1, freq: 'realtime', pluginId: 'ikuai', component: markRaw(SpeedCard) },
  { type: 'temperature', title: 'CPU温度', icon: '🌡️', w: 1, h: 1, freq: 'realtime', pluginId: 'ikuai', component: markRaw(TemperatureCard) },
  { type: 'uptime', title: '运行时间', icon: '⏱️', w: 1, h: 1, freq: 'slow', pluginId: 'ikuai', component: markRaw(UptimeCard) },
  { type: 'systemOverview', title: '系统总览', icon: '🖥️', w: 2, h: 1, freq: 'slow', pluginId: 'ikuai', component: markRaw(SystemOverviewCard) },
  { type: 'cpuTemp', title: 'CPU温度监控', icon: '📈', w: 2, h: 2, freq: 'realtime', pluginId: 'ikuai', component: markRaw(CpuTempCard) },
  { type: 'wanStatus', title: 'WAN 状态', icon: '🌐', w: 1, h: 2, freq: 'realtime', pluginId: 'ikuai', component: markRaw(WanStatusCard) },
  { type: 'systemStats', title: '系统状态', icon: '📊', w: 2, h: 1, freq: 'realtime', pluginId: 'ikuai', component: markRaw(SystemStatsCard) },
  { type: 'realtimeSpeed', title: '实时速率', icon: '⚡', w: 3, h: 2, freq: 'realtime', pluginId: 'ikuai', component: markRaw(RealtimeSpeedCard) },
  { type: 'deviceList', title: '在线用户', icon: '📱', w: 2, h: 3, freq: 'slow', pluginId: 'ikuai', component: markRaw(DeviceListCard) },
  { type: 'deviceTraffic', title: '设备流量', icon: '📊', w: 2, h: 2, freq: 'realtime', pluginId: 'ikuai', component: markRaw(DeviceTrafficCard) },
]

const editing = ref(false)
const showPicker = ref(false)
const placed = ref<PlacedCard[]>([])
const loading = ref(false)
const containerRef = ref<HTMLElement | null>(null)
const containerW = ref(1200)
const vh = ref(window.innerHeight)
const dashboardBg = ref(DEFAULT_BG)
const cardTheme = ref<'dark' | 'light'>('dark')
const cardBlur = ref(20)
const enabledPlugins = ref<Set<string>>(new Set())
const activePluginTab = ref('')

// Metrics data
const metrics = ref<Record<string, MetricValue>>({})

let realtimeTimer: ReturnType<typeof setInterval> | null = null
let frequentTimer: ReturnType<typeof setInterval> | null = null
let slowTimer: ReturnType<typeof setInterval> | null = null

// Polling intervals (default values, can be configured in admin)
const pollingIntervals = ref({
  realtime: 2000,
  frequent: 30000,
  slow: 300000,
})

// 页面可见性控制
const isPageVisible = ref(true)
let lastHiddenTime: number | null = null
const HIDDEN_THRESHOLD = 60000 // 60秒，超过这个时间需要全量刷新

const cell = computed(() => {
  const w = containerW.value - (COLS - 1) * GAP
  return Math.floor(w / COLS)
})

const gridRows = computed(() => {
  const minRows = Math.floor((vh.value - 48) / (cell.value + GAP))
  const contentRows = placed.value.length > 0
    ? Math.max(...placed.value.map(c => c.y + c.h))
    : 0
  return Math.max(contentRows, minRows, 4)
})

const unplacedCards = computed(() => {
  const used = new Set(placed.value.map(c => c.type))
  return cardDefs.filter(c => !used.has(c.type) && enabledPlugins.value.has(c.pluginId))
})

function isPluginEnabled(pluginId: string): boolean {
  return enabledPlugins.value.has(pluginId)
}

function getPluginStatus(pluginId: string): 'enabled' | 'disabled' | 'unknown' {
  if (enabledPlugins.value.size === 0) return 'unknown'
  return enabledPlugins.value.has(pluginId) ? 'enabled' : 'disabled'
}

const pluginTabs = computed(() => {
  const ids = new Set(cardDefs.filter(c => enabledPlugins.value.has(c.pluginId)).map(c => c.pluginId))
  return Array.from(ids)
})

const pluginNames: Record<string, string> = {
  ikuai: 'iKuai',
  pve: 'PVE',
  openwrt: 'OpenWrt',
  fnos: 'FnOS',
}

function getPluginCards(pluginId: string) {
  const placedList = placed.value || []
  return cardDefs.filter(c => c.pluginId === pluginId && isPluginEnabled(c.pluginId) && !placedList.some(p => p.type === c.type))
}

function getCardDef(type: string): CardDef | undefined {
  return cardDefs.find(c => c.type === type)
}

function toPlaced(apiCard: DashboardCard): PlacedCard {
  const def = getCardDef(apiCard.cardType)
  return {
    id: `${apiCard.cardType}-${apiCard.id}`,
    type: apiCard.cardType,
    title: apiCard.title,
    x: apiCard.positionX,
    y: apiCard.positionY,
    w: def?.w ?? 1,
    h: def?.h ?? 1,
  }
}

function mergeMetrics(data: Record<string, { metrics: Record<string, MetricValue> }> | null) {
  if (!data) return
  for (const plugin of Object.values(data)) {
    if (plugin?.metrics) {
      Object.assign(metrics.value, plugin.metrics)
    }
  }
}

async function fetchRealtime() {
  try { const { data } = await getRealtime(TEMPLATE); mergeMetrics(data) } catch {}
}

async function fetchFrequent() {
  try { const { data } = await getFrequent(TEMPLATE); mergeMetrics(data) } catch {}
}

async function fetchSlow() {
  try { const { data } = await getSlow(TEMPLATE); mergeMetrics(data) } catch {}
}

function startPolling() {
  // 如果页面不可见，不启动轮询
  if (!isPageVisible.value) return

  fetchRealtime()
  fetchFrequent()
  fetchSlow()
  realtimeTimer = setInterval(fetchRealtime, pollingIntervals.value.realtime)
  frequentTimer = setInterval(fetchFrequent, pollingIntervals.value.frequent)
  slowTimer = setInterval(fetchSlow, pollingIntervals.value.slow)
}

function stopPolling() {
  if (realtimeTimer) clearInterval(realtimeTimer)
  if (frequentTimer) clearInterval(frequentTimer)
  if (slowTimer) clearInterval(slowTimer)
}

// 页面可见性变化处理
function handleVisibilityChange() {
  if (document.hidden) {
    // 页面不可见，暂停轮询
    isPageVisible.value = false
    lastHiddenTime = Date.now()
    stopPolling()
  } else {
    // 页面重新可见
    isPageVisible.value = true
    const hiddenDuration = lastHiddenTime ? Date.now() - lastHiddenTime : 0
    lastHiddenTime = null

    // 如果离开时间较长，做一次全量数据加载
    if (hiddenDuration > HIDDEN_THRESHOLD) {
      fetchRealtime()
      fetchFrequent()
      fetchSlow()
    }

    // 恢复轮询
    startPolling()
  }
}

async function fetchPlugins() {
  try {
    const { data } = await listPlugins()
    enabledPlugins.value = new Set(data.filter(p => p.enabled).map(p => p.pluginId))
  } catch {
    // ignore
  }
}

function measureContainer() {
  if (containerRef.value) {
    containerW.value = containerRef.value.clientWidth
  }
  vh.value = window.innerHeight
}

async function loadBackground() {
  try {
    const { data } = await getAllConfig()
    const bgConfig = data.find(c => c.configKey === 'dashboard_background')
    if (bgConfig?.configValue) {
      dashboardBg.value = bgConfig.configValue
    }
    const themeConfig = data.find(c => c.configKey === 'card_theme')
    if (themeConfig?.configValue === 'light' || themeConfig?.configValue === 'dark') {
      cardTheme.value = themeConfig.configValue
    }
    const blurConfig = data.find(c => c.configKey === 'card_blur')
    if (blurConfig?.configValue) {
      const v = parseInt(blurConfig.configValue)
      if (!isNaN(v) && v >= 0 && v <= 50) cardBlur.value = v
    }
    // Load polling intervals
    const realtimeConfig = data.find(c => c.configKey === 'polling_realtime_interval')
    const frequentConfig = data.find(c => c.configKey === 'polling_frequent_interval')
    const slowConfig = data.find(c => c.configKey === 'polling_slow_interval')
    if (realtimeConfig?.configValue) {
      const v = parseInt(realtimeConfig.configValue)
      if (!isNaN(v) && v >= 500) pollingIntervals.value.realtime = v
    }
    if (frequentConfig?.configValue) {
      const v = parseInt(frequentConfig.configValue)
      if (!isNaN(v) && v >= 1000) pollingIntervals.value.frequent = v
    }
    if (slowConfig?.configValue) {
      const v = parseInt(slowConfig.configValue)
      if (!isNaN(v) && v >= 5000) pollingIntervals.value.slow = v
    }
  } catch {
    // use default
  }
}

onMounted(async () => {
  window.addEventListener('resize', measureContainer)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  loading.value = true
  try {
    const { data } = await getCards(TEMPLATE)
    placed.value = data.map(toPlaced)
  } catch {
    // empty dashboard
  } finally {
    loading.value = false
  }
  measureContainer()
  await loadBackground()
  await fetchPlugins()
  startPolling()
})

onUnmounted(() => {
  window.removeEventListener('resize', measureContainer)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  stopPolling()
})

// Drag state
const dragging = ref<{ cardId: string; startX: number; startY: number; origX: number; origY: number } | null>(null)

function onDragStart(e: MouseEvent, card: PlacedCard) {
  if (!editing.value) return
  e.preventDefault()
  dragging.value = {
    cardId: card.id,
    startX: e.clientX,
    startY: e.clientY,
    origX: card.x,
    origY: card.y,
  }
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

function onDragMove(e: MouseEvent) {
  if (!dragging.value) return
  const dx = e.clientX - dragging.value.startX
  const dy = e.clientY - dragging.value.startY
  const step = cell.value + GAP
  const card = placed.value.find(c => c.id === dragging.value!.cardId)
  if (!card) return

  const newX = Math.max(0, Math.min(COLS - card.w, dragging.value.origX + Math.round(dx / step)))
  const newY = Math.max(0, dragging.value.origY + Math.round(dy / step))

  if (!hasConflict(newX, newY, card.w, card.h, card.id)) {
    card.x = newX
    card.y = newY
  }
}

function onDragEnd() {
  dragging.value = null
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
}

async function toggleEdit() {
  if (editing.value) {
    await saveLayout()
    // 保存后退出编辑模式，通知后端
    try { await setEditing(TEMPLATE, false) } catch {}
  } else {
    editing.value = true
    // 进入编辑模式，通知后端返回全量指标
    try { await setEditing(TEMPLATE, true) } catch {}
  }
}

function addCard(card: CardDef) {
  const pos = findPosition(card.w, card.h)
  if (!pos) return
  placed.value.push({
    id: `${card.type}-${Date.now()}`,
    type: card.type,
    title: card.title,
    x: pos.x,
    y: pos.y,
    w: card.w,
    h: card.h,
  })
  showPicker.value = false
}

function removeCard(id: string) {
  placed.value = placed.value.filter(c => c.id !== id)
}

async function saveLayout() {
  const cards = placed.value.map(c => ({
    type: c.type,
    title: c.title,
    x: c.x,
    y: c.y,
  }))
  await saveCards(TEMPLATE, cards)
  editing.value = false
}

function findPosition(w: number, h: number): { x: number; y: number } | null {
  for (let y = 0; y <= gridRows.value - h; y++) {
    for (let x = 0; x <= COLS - w; x++) {
      if (!hasConflict(x, y, w, h)) return { x, y }
    }
  }
  return null
}

function hasConflict(x: number, y: number, w: number, h: number, excludeId?: string): boolean {
  return placed.value.some(c =>
    c.id !== excludeId &&
    x < c.x + c.w && x + w > c.x && y < c.y + c.h && y + h > c.y
  )
}

function openAdmin() {
  window.open('/admin/config', '_blank')
}

function openPicker() {
  if (pluginTabs.value.length > 0 && !activePluginTab.value) {
    activePluginTab.value = pluginTabs.value[0]
  }
  showPicker.value = true
}
</script>

<template>
  <div class="dashboard" :class="{ 'dashboard--editing': editing, 'theme-light': cardTheme === 'light' }" :style="{ background: dashboardBg, backgroundAttachment: 'fixed', '--card-blur': cardBlur + 'px' }">
    <div ref="containerRef" class="grid-wrapper">
      <template v-if="editing">
        <div class="grid-bg">
          <div v-for="n in COLS * gridRows" :key="n" class="grid-cell" />
        </div>
        <div class="col-lines">
          <div v-for="i in COLS + 1" :key="i" class="col-line" />
        </div>
        <div class="row-lines">
          <div v-for="i in gridRows + 1" :key="i" class="row-line" />
        </div>
        <div class="col-labels">
          <span v-for="i in COLS" :key="i" class="label">{{ i - 1 }}</span>
        </div>
        <div class="row-labels">
          <span v-for="i in gridRows" :key="i" class="label">{{ i - 1 }}</span>
        </div>
      </template>

      <!-- Placed cards -->
      <div
        v-for="card in placed"
        :key="card.id"
        class="grid-card"
        :class="{
          'grid-card--dragging': editing,
          'grid-card--disabled': getCardDef(card.type) && !isPluginEnabled(getCardDef(card.type)!.pluginId)
        }"
        :style="{
          left: card.x * (cell + GAP) + 'px',
          top: card.y * (cell + GAP) + 'px',
          width: card.w * cell + (card.w - 1) * GAP + 'px',
          height: card.h * cell + (card.h - 1) * GAP + 'px',
        }"
        @mousedown="onDragStart($event, card)"
      >
        <button v-if="editing" class="card-remove" @click.stop="removeCard(card.id)">×</button>
        <div
          v-if="getCardDef(card.type) && !isPluginEnabled(getCardDef(card.type)!.pluginId)"
          class="card-plugin-hint"
        >
          <span class="hint-icon">⚠</span>
          <span class="hint-text">插件已禁用</span>
        </div>
        <div class="card-body">
          <component
            :is="getCardDef(card.type)?.component"
            v-if="getCardDef(card.type)?.component"
            :metrics="metrics"
            :theme="cardTheme"
          />
          <span v-else class="card-placeholder">{{ card.w }}×{{ card.h }}</span>
        </div>
      </div>
    </div>

    <!-- Picker -->
    <Transition name="fade">
      <div v-if="showPicker" class="picker-overlay" @click.self="showPicker = false">
        <div class="picker-panel">
          <button class="picker-close" @click="showPicker = false">×</button>
          <div v-if="pluginTabs.length > 0" class="picker-tabs">
            <button
              v-for="tab in pluginTabs"
              :key="tab"
              class="picker-tab"
              :class="{ 'picker-tab--active': activePluginTab === tab }"
              @click="activePluginTab = tab"
            >
              {{ pluginNames[tab] || tab }}
              <span class="picker-tab-count">{{ getPluginCards(tab).length }}</span>
            </button>
          </div>
          <div class="picker-body">
            <div v-if="activePluginTab && getPluginCards(activePluginTab).length > 0" class="picker-grid">
              <div
                v-for="card in getPluginCards(activePluginTab)"
                :key="card.type"
                class="picker-item"
                :style="{ width: (card.w * 110) + 'px', height: (card.h * 110 + 36) + 'px' }"
                @click="addCard(card)"
              >
                <div class="picker-preview">
                  <div class="picker-preview-inner">
                    <component :is="card.component" :metrics="metrics" :theme="cardTheme" />
                  </div>
                </div>
                <div class="picker-meta">
                  <span class="picker-title">{{ card.title }}</span>
                  <span class="picker-size">{{ card.w }}×{{ card.h }}</span>
                </div>
              </div>
            </div>
            <div v-else class="picker-empty">该插件没有可用组件</div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Toolbar -->
    <div class="toolbar">
      <button class="tool-btn" title="管理后台" @click="openAdmin">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5">
          <circle cx="12" cy="12" r="3" />
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
        </svg>
      </button>
      <button
        class="tool-btn"
        :class="{ 'tool-btn--active': editing }"
        title="编辑布局"
        @click="toggleEdit"
      >
        <svg v-if="!editing" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5">
          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
          <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
        </svg>
        <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5">
          <polyline points="20 6 9 17 4 12" />
        </svg>
      </button>
    </div>

    <div v-if="editing" class="edit-bar">
      <span>编辑模式</span>
    </div>

    <Transition name="fade">
      <div v-if="editing" class="bottom-right">
        <button v-if="unplacedCards.length > 0" class="edit-btn" @click="openPicker">添加组件</button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.dashboard {
  width: 100vw;
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  position: relative;
  user-select: none;
  --card-text-primary: rgba(255, 255, 255, 0.9);
  --card-text-secondary: rgba(255, 255, 255, 0.5);
  --card-text-tertiary: rgba(255, 255, 255, 0.3);
  --card-accent: rgba(100, 160, 255, 0.9);
  --card-fill: rgba(255, 255, 255, 0.08);
  --chart-fill: rgba(255, 255, 255, 0.06);
  --card-blur: 20px;
}

.grid-wrapper {
  position: relative;
  width: 100%;
}

.grid-bg {
  display: grid;
  grid-template-columns: repeat(v-bind('COLS'), 1fr);
  grid-auto-rows: v-bind('cell + "px"');
  gap: v-bind('GAP + "px"');
}

.grid-cell {
  background: rgba(255, 255, 255, 0.015);
  border: 1px solid rgba(255, 255, 255, 0.04);
  border-radius: 4px;
}

.col-lines { position: absolute; inset: 0; pointer-events: none; }
.col-line { position: absolute; top: 0; bottom: 0; width: 1px; background: rgba(255, 255, 255, 0.06); }
.row-lines { position: absolute; inset: 0; pointer-events: none; }
.row-line { position: absolute; left: 0; right: 0; height: 1px; background: rgba(255, 255, 255, 0.06); }

.col-labels {
  position: absolute; top: 0; left: 0; right: 0;
  display: grid; grid-template-columns: repeat(v-bind('COLS'), 1fr);
  gap: v-bind('GAP + "px"'); pointer-events: none;
}
.col-labels .label {
  text-align: center; font-size: 10px; color: rgba(255, 255, 255, 0.25);
  font-family: 'SF Mono', 'Cascadia Code', 'Menlo', monospace; line-height: 16px;
}

.row-labels {
  position: absolute; top: 0; left: 0; bottom: 0; width: 20px;
  display: grid; grid-template-rows: repeat(v-bind('gridRows'), 1fr);
  gap: v-bind('GAP + "px"'); pointer-events: none;
}
.row-labels .label {
  display: flex; align-items: flex-start; justify-content: center;
  font-size: 10px; color: rgba(255, 255, 255, 0.25); font-family: 'SF Mono', 'Cascadia Code', 'Menlo', monospace; padding-top: 2px;
}

.grid-card {
  position: absolute;
  background: rgba(15, 15, 20, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  display: flex; flex-direction: column; overflow: hidden;
  backdrop-filter: blur(var(--card-blur));
  -webkit-backdrop-filter: blur(var(--card-blur));
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  z-index: 5;
}
.grid-card--dragging { cursor: grab; }
.grid-card--dragging:active { cursor: grabbing; }
.dashboard--editing .grid-card { border: 1px dashed rgba(100, 160, 255, 0.4); }
.grid-card--disabled { opacity: 0.7; }
.grid-card--disabled::after {
  content: '';
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 8px,
    rgba(255, 100, 100, 0.03) 8px,
    rgba(255, 100, 100, 0.03) 16px
  );
  pointer-events: none;
  border-radius: 12px;
}

.card-plugin-hint {
  position: absolute;
  top: 6px;
  left: 6px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(255, 100, 100, 0.15);
  border: 1px solid rgba(255, 100, 100, 0.3);
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 10px;
  color: rgba(255, 150, 150, 0.9);
}
.hint-icon { font-size: 11px; }
.hint-text { font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif; }

/* Light theme */
.theme-light {
  --card-text-primary: rgba(30, 30, 40, 0.9);
  --card-text-secondary: rgba(30, 30, 40, 0.55);
  --card-text-tertiary: rgba(30, 30, 40, 0.35);
  --card-accent: rgba(60, 120, 200, 0.9);
  --card-fill: rgba(0, 0, 0, 0.06);
  --chart-fill: rgba(0, 0, 0, 0.06);
}
.theme-light .grid-card {
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(0, 0, 0, 0.1);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.8);
}
.theme-light .dashboard--editing .grid-card { border: 1px dashed rgba(60, 120, 200, 0.4); }

.card-remove {
  position: absolute; top: 6px; right: 6px; z-index: 10;
  width: 18px; height: 18px; border-radius: 50%; border: none;
  background: rgba(255, 80, 80, 0.8); color: #fff; font-size: 12px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  line-height: 1; padding: 0; transition: all 0.2s;
  opacity: 0;
}
.dashboard--editing .card-remove { opacity: 1; }
.card-remove:hover { background: rgba(255, 60, 60, 0.95); transform: scale(1.1); }

.card-body { flex: 1; display: flex; align-items: center; justify-content: center; padding: 0; overflow: hidden; }
.card-placeholder { font-size: 12px; color: rgba(255, 255, 255, 0.3); font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif; }

.picker-overlay {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.6);
  display: flex; align-items: center; justify-content: center;
  z-index: 200;
}
.picker-panel {
  background: rgba(25, 25, 30, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px; width: calc(8 / 12 * (100vw - 48px)); max-height: 92vh;
  overflow: hidden; display: flex; flex-direction: column; position: relative;
  box-sizing: border-box;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
.picker-close {
  position: absolute; top: 12px; right: 12px; z-index: 10;
  width: 28px; height: 28px; border-radius: 50%; border: none;
  background: rgba(255, 255, 255, 0.1); color: rgba(255, 255, 255, 0.7);
  font-size: 16px; cursor: pointer; display: flex; align-items: center;
  justify-content: center; padding: 0; transition: all 0.2s;
}
.picker-close:hover { background: rgba(255, 255, 255, 0.18); color: #fff; }

.picker-tabs {
  display: flex; gap: 0; padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.picker-tab {
  padding: 10px 16px; font-size: 13px; font-weight: 500;
  color: rgba(255, 255, 255, 0.5); background: none; border: none;
  cursor: pointer; position: relative; transition: color 0.2s;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.picker-tab:hover { color: rgba(255, 255, 255, 0.8); }
.picker-tab--active {
  color: rgba(100, 160, 255, 0.95);
}
.picker-tab--active::after {
  content: ''; position: absolute; bottom: -1px; left: 16px; right: 16px;
  height: 2px; background: rgba(100, 160, 255, 0.8); border-radius: 1px;
}
.picker-tab-count {
  margin-left: 6px; font-size: 11px; font-weight: 400;
  color: rgba(255, 255, 255, 0.3); font-family: 'SF Mono', 'Cascadia Code', monospace;
}

.picker-body { padding: 16px 20px; overflow-y: auto; }
.picker-grid { display: flex; flex-wrap: wrap; gap: 12px; }

.picker-item {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px; background: rgba(255, 255, 255, 0.03);
  cursor: pointer; transition: all 0.2s; overflow: hidden;
  display: flex; flex-direction: column;
}
.picker-item:hover {
  border-color: rgba(100, 160, 255, 0.4);
  background: rgba(100, 160, 255, 0.06);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}

.picker-preview {
  flex: 1; border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden; padding: 8px;
  display: flex; align-items: center; justify-content: center;
}

.picker-meta { padding: 8px 10px; display: flex; justify-content: space-between; align-items: center; }
.picker-title { font-size: 12px; color: rgba(255, 255, 255, 0.75); }
.picker-size { font-size: 10px; color: rgba(255, 255, 255, 0.3); font-family: 'SF Mono', 'Cascadia Code', 'Menlo', monospace; }

.picker-empty { text-align: center; color: rgba(255, 255, 255, 0.35); padding: 32px 0; }

.toolbar {
  position: fixed; bottom: 24px; left: 24px;
  display: flex; gap: 12px; z-index: 100;
}
.tool-btn {
  width: 44px; height: 44px; border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.12); background: rgba(20, 20, 25, 0.8);
  color: rgba(255, 255, 255, 0.95); display: flex; align-items: center;
  justify-content: center; cursor: pointer; transition: all 0.25s;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}
.tool-btn:hover {
  background: rgba(40, 40, 50, 0.9);
  color: #fff;
  border-color: rgba(255, 255, 255, 0.25);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}
.tool-btn--active {
  background: rgba(20, 20, 25, 0.8);
  border-color: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.95);
}

.edit-bar {
  position: fixed; top: 24px; left: 50%; transform: translateX(-50%);
  background: rgba(100, 160, 255, 0.15); border: 1px solid rgba(100, 160, 255, 0.3);
  color: rgba(100, 160, 255, 0.95); padding: 6px 18px; border-radius: 20px;
  font-size: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2); z-index: 100;
}

.bottom-right {
  position: fixed; bottom: 24px; right: 24px;
  display: flex; gap: 10px; z-index: 100;
}
.edit-btn {
  background: rgba(20, 20, 25, 0.85); border: 1px solid rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.85); padding: 8px 16px; border-radius: 8px;
  font-size: 12px; cursor: pointer; transition: all 0.25s;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}
.edit-btn:hover {
  background: rgba(40, 40, 50, 0.95);
  border-color: rgba(255, 255, 255, 0.2);
}
.edit-btn--primary {
  background: rgba(100, 160, 255, 0.25);
  border-color: rgba(100, 160, 255, 0.5);
  color: #fff;
}
.edit-btn--primary:hover {
  background: rgba(100, 160, 255, 0.4);
  border-color: rgba(100, 160, 255, 0.6);
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
