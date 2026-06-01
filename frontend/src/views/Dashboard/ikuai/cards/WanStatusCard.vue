<script setup lang="ts">
import { computed } from 'vue'
import FlipText from '@/components/FlipText.vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

interface WanIface {
  name: string
  status: string
  upload: number
  download: number
}

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const bgColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.03)' : 'rgba(255,255,255,0.03)')
const borderColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')

const ifaces = computed<WanIface[]>(() => {
  const map = new Map<string, WanIface>()

  for (const [key, mv] of Object.entries(props.metrics)) {
    if (!key.startsWith('wan.')) continue
    const rest = key.slice(4) // "wan1.status"
    const dotIdx = rest.lastIndexOf('.')
    if (dotIdx === -1) continue
    const ifaceName = rest.substring(0, dotIdx)
    const field = rest.substring(dotIdx + 1)

    if (!map.has(ifaceName)) {
      map.set(ifaceName, { name: ifaceName, status: 'unknown', upload: 0, download: 0 })
    }
    const iface = map.get(ifaceName)!

    if (field === 'status') {
      iface.status = String(mv.value ?? 'unknown')
    } else if (field === 'upload') {
      iface.upload = typeof mv.value === 'number' ? mv.value : 0
    } else if (field === 'download') {
      iface.download = typeof mv.value === 'number' ? mv.value : 0
    }
  }

  return Array.from(map.values())
})

const onlineCount = computed(() => ifaces.value.filter(i => i.status === 'online').length)

function isOnline(status: string): boolean {
  return status === 'online'
}

function splitSpeed(bps: number): { num: string; unit: string } {
  if (bps === 0) return { num: '--', unit: '' }
  if (bps < 1024) return { num: String(bps), unit: 'bps' }
  if (bps < 1048576) return { num: (bps / 1024).toFixed(1), unit: 'KB/s' }
  if (bps < 1073741824) return { num: (bps / 1048576).toFixed(2), unit: 'MB/s' }
  return { num: (bps / 1073741824).toFixed(2), unit: 'GB/s' }
}
</script>

<template>
  <div class="wan-card">
    <!-- Summary bar -->
    <div class="wan-summary">
      <span class="wan-summary-label" :style="{ color: textSec }">WAN 口</span>
      <span class="wan-summary-count" :style="{ color: onlineCount > 0 ? '#36D399' : '#FF6B6B' }">
        {{ onlineCount }}/{{ ifaces.length }}
      </span>
    </div>

    <!-- WAN interface list -->
    <div class="wan-list">
      <div
        v-for="iface in ifaces"
        :key="iface.name"
        class="wan-item"
        :style="{ background: bgColor, border: `1px solid ${borderColor}` }"
      >
        <!-- Name + status dot -->
        <div class="wan-item-header">
          <span class="wan-name" :style="{ color: textColor }">{{ iface.name }}</span>
          <span
            class="wan-dot"
            :class="{ 'wan-dot--online': isOnline(iface.status) }"
          />
        </div>

        <!-- Speeds -->
        <div class="wan-speeds">
          <div class="wan-speed-row">
            <span class="wan-arrow" style="color:#6C8EFF;">↑</span>
            <FlipText
              :text="splitSpeed(iface.upload).num"
              :color="textColor"
              :font-size="12"
              :font-weight="600"
              font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
              min-width="3ch"
            />
            <span class="wan-unit" :style="{ color: textTer }">{{ splitSpeed(iface.upload).unit }}</span>
          </div>
          <div class="wan-speed-row">
            <span class="wan-arrow" style="color:#2DD4BF;">↓</span>
            <FlipText
              :text="splitSpeed(iface.download).num"
              :color="textColor"
              :font-size="12"
              :font-weight="600"
              font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
              min-width="3ch"
            />
            <span class="wan-unit" :style="{ color: textTer }">{{ splitSpeed(iface.download).unit }}</span>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-if="ifaces.length === 0" class="wan-empty">
        <span :style="{ color: textTer }">采集中...</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wan-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  padding: 10px;
  gap: 8px;
}

.wan-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.wan-summary-label {
  font-size: 11px;
  font-weight: 500;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.wan-summary-count {
  font-size: 13px;
  font-weight: 700;
  font-family: 'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace;
}

.wan-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
  overflow-y: auto;
}

.wan-item {
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.wan-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wan-name {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.wan-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 100, 100, 0.7);
  flex-shrink: 0;
}

.wan-dot--online {
  background: #36D399;
  box-shadow: 0 0 6px rgba(54, 211, 153, 0.4);
}

.wan-speeds {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wan-speed-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.wan-arrow {
  font-size: 11px;
  flex-shrink: 0;
}

.wan-unit {
  font-size: 10px;
  font-family: 'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace;
  margin-left: auto;
}
</style>
