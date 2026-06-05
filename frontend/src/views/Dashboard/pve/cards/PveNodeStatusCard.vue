<script setup lang="ts">
import { computed } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

// CPU
const cpuUsage = computed(() => {
  const v = props.metrics['pve.cpu.usage']?.value
  return typeof v === 'number' ? v : 0
})

// Memory
const memUsed = computed(() => {
  const v = props.metrics['pve.memory.used']?.value
  return typeof v === 'number' ? v : 0
})
const memTotal = computed(() => {
  const v = props.metrics['pve.memory.total']?.value
  return typeof v === 'number' ? v : 0
})
const memUsage = computed(() => {
  const v = props.metrics['pve.memory.usage']?.value
  return typeof v === 'number' ? v : 0
})

// Load average
const load1 = computed(() => {
  const v = props.metrics['pve.loadavg.1']?.value
  return typeof v === 'number' ? v : 0
})
const load5 = computed(() => {
  const v = props.metrics['pve.loadavg.5']?.value
  return typeof v === 'number' ? v : 0
})
const load15 = computed(() => {
  const v = props.metrics['pve.loadavg.15']?.value
  return typeof v === 'number' ? v : 0
})

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.85)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.35)' : 'rgba(255,255,255,0.3)')
const trackBg = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.08)')
const freeColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)')
const emptyColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')

const memOption = computed(() => ({
  tooltip: { show: false },
  series: [{
    type: 'pie',
    radius: ['55%', '78%'],
    center: ['50%', '50%'],
    silent: true,
    label: { show: false },
    data: memTotal.value > 0
      ? [
          { value: memUsed.value, name: '已用', itemStyle: { color: '#A78BFA' } },
          { value: memTotal.value - memUsed.value, name: '空闲', itemStyle: { color: freeColor.value } },
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
  <div style="display:flex;width:100%;height:100%;padding:10px 12px;gap:12px;">
    <!-- Left: Memory donut -->
    <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;flex-shrink:0;width:80px;">
      <div style="width:64px;height:64px;position:relative;">
        <v-chart :option="memOption" autoresize />
        <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;pointer-events:none;">
          <span style="font-size:12px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: '#A78BFA' }">
            {{ memTotal > 0 ? memUsage.toFixed(0) + '%' : '--' }}
          </span>
        </div>
      </div>
      <span style="font-size:10px;" :style="{ color: textSec }">
        {{ memTotal > 0 ? memUsed.toFixed(1) + '/' + memTotal.toFixed(1) + ' GB' : '内存' }}
      </span>
    </div>
    <!-- Right: CPU + Load -->
    <div style="flex:1;display:flex;flex-direction:column;justify-content:center;gap:10px;min-width:0;">
      <!-- CPU -->
      <div>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px;">
          <span style="font-size:11px;" :style="{ color: textSec }">CPU</span>
          <span style="font-size:11px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textColor }">{{ cpuUsage.toFixed(1) }}%</span>
        </div>
        <div style="height:5px;border-radius:3px;overflow:hidden;" :style="{ background: trackBg }">
          <div style="height:100%;border-radius:3px;transition:width 0.8s cubic-bezier(0.4,0,0.2,1);" :style="{ width: cpuUsage + '%', background: cpuUsage >= 80 ? '#FF6B6B' : '#64A0FF' }"></div>
        </div>
      </div>
      <!-- Load Average -->
      <div style="display:flex;gap:8px;">
        <div style="flex:1;text-align:center;">
          <div style="font-size:10px;" :style="{ color: textTer }">1min</div>
          <div style="font-size:12px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textColor }">{{ load1.toFixed(2) }}</div>
        </div>
        <div style="flex:1;text-align:center;">
          <div style="font-size:10px;" :style="{ color: textTer }">5min</div>
          <div style="font-size:12px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textColor }">{{ load5.toFixed(2) }}</div>
        </div>
        <div style="flex:1;text-align:center;">
          <div style="font-size:10px;" :style="{ color: textTer }">15min</div>
          <div style="font-size:12px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textColor }">{{ load15.toFixed(2) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
