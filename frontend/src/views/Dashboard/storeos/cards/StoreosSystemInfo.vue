<script setup lang="ts">
import { computed } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

// Device info
const hostname = computed(() => {
  const v = props.metrics['storeos.system.hostname']?.value
  return typeof v === 'string' && v !== '' ? v : '--'
})
const kernel = computed(() => {
  const v = props.metrics['storeos.system.kernel']?.value
  return typeof v === 'string' && v !== '' ? v : '--'
})
const version = computed(() => {
  const v = props.metrics['storeos.system.version']?.value
  if (typeof v !== 'string' || v === '') return '--'
  // 截取 "iStoreOS 24.10.6 2026041710" 中的版本号部分
  const match = v.match(/iStoreOS\s+([\d.]+)/)
  return match ? match[1] : v
})

// Uptime
const uptime = computed(() => {
  const v = props.metrics['storeos.system.uptime']?.value
  return Number(v) || 0
})

function parseUptime(seconds: number): string {
  if (seconds === 0) return '--'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days}天 ${hours}时`
  if (hours > 0) return `${hours}时 ${minutes}分`
  return `${minutes}分`
}

// Disk
const diskUsed = computed(() => {
  const v = props.metrics['storeos.disk.used']?.value
  return Number(v) || 0
})
const diskTotal = computed(() => {
  const v = props.metrics['storeos.disk.total']?.value
  return Number(v) || 0
})
const diskUsage = computed(() => {
  const v = props.metrics['storeos.disk.usage']?.value
  return Number(v) || 0
})

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const dividerColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')
const freeColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)')
const emptyColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')

const diskOption = computed(() => ({
  tooltip: { show: false },
  series: [{
    type: 'pie',
    radius: ['55%', '78%'],
    center: ['50%', '50%'],
    silent: true,
    label: { show: false },
    data: diskTotal.value > 0
      ? [
          { value: diskUsed.value, name: '已用', itemStyle: { color: '#F59E0B' } },
          { value: diskTotal.value - diskUsed.value, name: '空闲', itemStyle: { color: freeColor.value } },
        ]
      : [{ value: 1, name: '无数据', itemStyle: { color: emptyColor.value } }],
    emphasis: { disabled: true },
    animation: true,
    animationDuration: 500,
    animationDurationUpdate: 800,
    animationEasingUpdate: 'cubicInOut',
  }],
}))
</script>

<template>
  <div style="display:flex;flex-direction:column;width:100%;height:100%;padding:10px 12px;gap:8px;overflow:hidden;">
    <!-- Top: Hostname + uptime -->
    <div style="display:flex;align-items:center;justify-content:space-between;flex-shrink:0;">
      <span style="font-size:13px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
            :style="{ color: textColor }">
        {{ hostname }}
      </span>
      <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;flex-shrink:0;"
            :style="{ color: textSec }">
        ⏱ {{ parseUptime(uptime) }}
      </span>
    </div>

    <!-- Divider -->
    <div style="height:1px;flex-shrink:0;" :style="{ background: dividerColor }"></div>

    <!-- Bottom: Disk donut + System info side by side -->
    <div style="display:flex;gap:10px;flex:1;min-height:0;align-items:center;">
      <!-- Disk donut -->
      <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;gap:0;flex-shrink:0;width:80px;">
        <div style="width:60px;height:60px;position:relative;">
          <v-chart :option="diskOption" autoresize />
          <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;pointer-events:none;">
            <span style="font-size:11px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: '#F59E0B' }">
              {{ diskTotal > 0 ? diskUsage.toFixed(0) + '%' : '--' }}
            </span>
          </div>
        </div>
        <span style="font-size:9px;" :style="{ color: textSec }">
          {{ diskTotal > 0 ? diskUsed + '/' + diskTotal + ' MB' : '磁盘' }}
        </span>
      </div>

      <!-- System info -->
      <div style="flex:1;display:flex;flex-direction:column;justify-content:center;gap:4px;min-width:0;">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span style="font-size:10px;" :style="{ color: textTer }">系统</span>
          <span style="font-size:10px;font-weight:500;" :style="{ color: textSec }">
            {{ version }}
          </span>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span style="font-size:10px;" :style="{ color: textTer }">内核</span>
          <span style="font-size:10px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:120px;"
                :style="{ color: textSec }" :title="kernel">
            {{ kernel }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
