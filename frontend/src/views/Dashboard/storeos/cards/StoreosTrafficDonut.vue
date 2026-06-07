<script setup lang="ts">
import { computed } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const rxVal = computed(() => {
  const v = props.metrics['storeos.traffic.total_rx']?.value
  return Number(v) || 0
})
const txVal = computed(() => {
  const v = props.metrics['storeos.traffic.total_tx']?.value
  return Number(v) || 0
})

const hasData = computed(() => rxVal.value > 0 || txVal.value > 0)
const emptyColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')
const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.85)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')

// vnstat 返回的是 KB
function formatTraffic(kb: number): string {
  if (kb === 0) return '--'
  if (kb < 1024) return kb + ' KB'
  if (kb < 1048576) return (kb / 1024).toFixed(1) + ' MB'
  return (kb / 1048576).toFixed(2) + ' GB'
}

const totalRx = computed(() => formatTraffic(rxVal.value))
const totalTx = computed(() => formatTraffic(txVal.value))

const option = computed(() => ({
  tooltip: { show: false },
  series: [{
    type: 'pie',
    radius: ['50%', '75%'],
    center: ['50%', '50%'],
    silent: true,
    label: { show: false },
    data: hasData.value
      ? [
          { value: rxVal.value, name: '接收', itemStyle: { color: '#64A0FF' } },
          { value: txVal.value, name: '发送', itemStyle: { color: '#A78BFA' } },
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
  <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;gap:2px;padding:6px 0 10px;">
    <div style="width:72px;height:72px;flex-shrink:0;">
      <v-chart :option="option" autoresize />
    </div>
    <div style="display:flex;flex-direction:column;gap:4px;width:100%;padding:0 12px;">
      <div style="display:flex;align-items:center;gap:5px;">
        <span style="width:6px;height:6px;border-radius:50%;flex-shrink:0;background:#64A0FF;"></span>
        <span style="font-size:11px;" :style="{ color: textSec }">↓</span>
        <span style="font-size:11px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;margin-left:auto;" :style="{ color: textColor }">{{ totalRx }}</span>
      </div>
      <div style="display:flex;align-items:center;gap:5px;">
        <span style="width:6px;height:6px;border-radius:50%;flex-shrink:0;background:#A78BFA;"></span>
        <span style="font-size:11px;" :style="{ color: textSec }">↑</span>
        <span style="font-size:11px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;margin-left:auto;" :style="{ color: textColor }">{{ totalTx }}</span>
      </div>
    </div>
  </div>
</template>
