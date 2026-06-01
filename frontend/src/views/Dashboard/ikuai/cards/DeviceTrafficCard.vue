<script setup lang="ts">
import { computed } from 'vue'
import FlipText from '@/components/FlipText.vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

interface DeviceDetail {
  hostname: string
  ip: string
  mac: string
  upload: number
  download: number
  totalUp: number
  totalDown: number
  clientType: string
  signal: string
  ssid: string
}

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const bgColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.03)' : 'rgba(255,255,255,0.03)')

const devices = computed<DeviceDetail[]>(() => {
  const raw = props.metrics['lan.device_details']?.value
  if (typeof raw !== 'string' || !raw) return []
  try {
    const list = JSON.parse(raw) as DeviceDetail[]
    return list
      .filter(d => d.totalUp > 0 || d.totalDown > 0)
      .sort((a, b) => (b.totalUp + b.totalDown) - (a.totalUp + a.totalDown))
  } catch {
    return []
  }
})

function formatBytes(bytes: number): string {
  if (bytes === 0) return '--'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB'
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + 'MB'
  return (bytes / 1073741824).toFixed(2) + 'GB'
}
</script>

<template>
  <div class="dt-card">
    <div class="dt-header">
      <span class="dt-label" :style="{ color: textSec }">设备流量</span>
      <span class="dt-count" :style="{ color: textTer }">{{ devices.length }} 台活跃</span>
    </div>

    <div class="dt-list">
      <div
        v-for="device in devices"
        :key="device.mac + '_' + device.ip"
        class="dt-item"
        :style="{ background: bgColor }"
      >
        <div class="dt-item-top">
          <span class="dt-name" :style="{ color: textColor }">{{ device.hostname || device.ip }}</span>
        </div>
        <div class="dt-item-speeds">
          <div class="dt-speed">
            <span class="dt-arrow" style="color:#6C8EFF;">↑</span>
            <FlipText
              :text="formatBytes(device.totalUp)"
              :color="textColor"
              :font-size="11"
              :font-weight="600"
              font-family="'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace"
              min-width="4ch"
            />
          </div>
          <div class="dt-speed">
            <span class="dt-arrow" style="color:#2DD4BF;">↓</span>
            <FlipText
              :text="formatBytes(device.totalDown)"
              :color="textColor"
              :font-size="11"
              :font-weight="600"
              font-family="'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace"
              min-width="4ch"
            />
          </div>
        </div>
      </div>

      <div v-if="devices.length === 0" class="dt-empty">
        <span :style="{ color: textTer }">无活跃设备</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dt-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  padding: 10px;
  gap: 8px;
}

.dt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.dt-label {
  font-size: 11px;
  font-weight: 500;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.dt-count {
  font-size: 10px;
  font-family: 'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace;
}

.dt-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.dt-item {
  border-radius: 6px;
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.dt-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dt-name {
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.dt-item-speeds {
  display: flex;
  gap: 12px;
}

.dt-speed {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.dt-arrow {
  font-size: 10px;
  flex-shrink: 0;
}

.dt-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}
</style>
