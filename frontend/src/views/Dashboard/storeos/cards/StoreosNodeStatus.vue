<script setup lang="ts">
import { computed } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

// Memory
const memUsed = computed(() => {
  const v = props.metrics['storeos.memory.used']?.value
  return Number(v) || 0
})
const memTotal = computed(() => {
  const v = props.metrics['storeos.memory.total']?.value
  return Number(v) || 0
})
const memUsage = computed(() => {
  const v = props.metrics['storeos.memory.usage']?.value
  return Number(v) || 0
})

// Load average
const load1 = computed(() => {
  const v = props.metrics['storeos.load.1']?.value
  return Number(v) || 0
})
const load5 = computed(() => {
  const v = props.metrics['storeos.load.5']?.value
  return Number(v) || 0
})
const load15 = computed(() => {
  const v = props.metrics['storeos.load.15']?.value
  return Number(v) || 0
})

// Connections
const connCount = computed(() => {
  const v = props.metrics['storeos.conn.count']?.value
  return Number(v) || 0
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
  <div style="display:flex;width:100%;height:100%;padding:8px 10px;gap:10px;overflow:hidden;">
    <!-- Left: Memory donut -->
    <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;flex-shrink:0;width:72px;">
      <div style="width:56px;height:56px;position:relative;">
        <v-chart :option="memOption" autoresize />
        <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;pointer-events:none;">
          <span style="font-size:11px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: '#A78BFA' }">
            {{ memTotal > 0 ? memUsage.toFixed(0) + '%' : '--' }}
          </span>
        </div>
      </div>
      <span style="font-size:9px;" :style="{ color: textSec }">
        {{ memTotal > 0 ? memUsed.toFixed(0) + '/' + memTotal.toFixed(0) + ' MB' : '内存' }}
      </span>
    </div>
    <!-- Right: Connections + Load -->
    <div style="flex:1;display:flex;flex-direction:column;justify-content:center;gap:8px;min-width:0;overflow:hidden;">
      <!-- Connections -->
      <div style="overflow:hidden;">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:3px;">
          <span style="font-size:10px;" :style="{ color: textSec }">连接数</span>
          <span style="font-size:10px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textColor }">
            {{ connCount > 0 ? connCount.toLocaleString() : '--' }}
          </span>
        </div>
        <div style="height:4px;border-radius:2px;overflow:hidden;" :style="{ background: trackBg }">
          <div style="height:100%;border-radius:2px;transition:width 0.8s cubic-bezier(0.4,0,0.2,1);"
               :style="{ width: Math.min(connCount / 50, 100) + '%', background: connCount > 400 ? '#FF6B6B' : '#64A0FF' }"></div>
        </div>
      </div>
      <!-- Load Average -->
      <div style="display:flex;gap:6px;">
        <div style="flex:1;text-align:center;min-width:0;">
          <div style="font-size:9px;" :style="{ color: textTer }">1min</div>
          <div style="font-size:11px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textColor }">{{ load1.toFixed(2) }}</div>
        </div>
        <div style="flex:1;text-align:center;min-width:0;">
          <div style="font-size:9px;" :style="{ color: textTer }">5min</div>
          <div style="font-size:11px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textColor }">{{ load5.toFixed(2) }}</div>
        </div>
        <div style="flex:1;text-align:center;min-width:0;">
          <div style="font-size:9px;" :style="{ color: textTer }">15min</div>
          <div style="font-size:11px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textColor }">{{ load15.toFixed(2) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
