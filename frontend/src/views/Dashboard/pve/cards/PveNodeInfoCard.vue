<script setup lang="ts">
import { computed } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const dividerColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')

// Node name
const nodeName = computed(() => {
  const v = props.metrics['pve.system.node']?.value
  return typeof v === 'string' ? v : '--'
})

// Uptime
const uptime = computed(() => {
  const v = props.metrics['pve.system.uptime']?.value
  return typeof v === 'number' ? v : 0
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

// CPU info
const cpuModel = computed(() => {
  const v = props.metrics['pve.cpu.model']?.value
  if (typeof v !== 'string' || v === '') return '--'
  return v.replace(/\(R\)/g, '').replace(/\(TM\)/g, '').replace(/CPU\s+/g, '').trim()
})
const cpuCores = computed(() => {
  const v = props.metrics['pve.cpu.cores']?.value
  return typeof v === 'number' ? v : 0
})

// Disk
const diskUsed = computed(() => {
  const v = props.metrics['pve.disk.used']?.value
  return typeof v === 'number' ? v : 0
})
const diskTotal = computed(() => {
  const v = props.metrics['pve.disk.total']?.value
  return typeof v === 'number' ? v : 0
})
const diskUsage = computed(() => {
  const v = props.metrics['pve.disk.usage']?.value
  return typeof v === 'number' ? v : 0
})

const freeColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)')
const emptyColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')
const trackBg = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.08)')

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
    <!-- Top: Node name + uptime -->
    <div style="display:flex;align-items:center;justify-content:space-between;flex-shrink:0;">
      <span style="font-size:13px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
            :style="{ color: textColor }">
        {{ nodeName }}
      </span>
      <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;flex-shrink:0;"
            :style="{ color: textSec }">
        ⏱ {{ parseUptime(uptime) }}
      </span>
    </div>

    <!-- Divider -->
    <div style="height:1px;flex-shrink:0;" :style="{ background: dividerColor }"></div>

    <!-- Bottom: Disk donut + CPU info side by side -->
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
          {{ diskTotal > 0 ? diskUsed + '/' + diskTotal + ' GB' : '磁盘' }}
        </span>
      </div>

      <!-- CPU info -->
      <div style="flex:1;display:flex;flex-direction:column;justify-content:center;gap:4px;min-width:0;">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span style="font-size:10px;" :style="{ color: textTer }">CPU</span>
          <span style="font-size:10px;font-weight:500;" :style="{ color: textSec }">
            {{ cpuCores }} 核
          </span>
        </div>
        <div style="font-size:10px;line-height:1.3;word-break:break-all;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;"
              :style="{ color: textSec }" :title="cpuModel">
          {{ cpuModel }}
        </div>
      </div>
    </div>
  </div>
</template>
