<script setup lang="ts">
import { computed } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.85)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const trackBg = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.08)')

const connCount = computed(() => {
  const v = props.metrics['storeos.conn.count']?.value
  return Number(v) || 0
})
const connMax = computed(() => {
  const v = props.metrics['storeos.conn.max']?.value
  return Number(v) || 0
})
const connUsage = computed(() => {
  const v = props.metrics['storeos.conn.usage']?.value
  return Number(v) || 0
})

interface DeviceConn {
  ip: string
  hostname: string
  conns: number
}

const topDevices = computed<DeviceConn[]>(() => {
  const raw = props.metrics['storeos.conn.per_device']?.value
  if (typeof raw !== 'string' || !raw || raw === '[]') return []
  try {
    const arr = JSON.parse(raw)
    return arr.map((d: any) => ({ ip: d.ip || '', hostname: d.hostname || '', conns: Number(d.conns) || 0 }))
  } catch {
    return []
  }
})

const maxConns = computed(() => {
  if (topDevices.value.length === 0) return 1
  return Math.max(...topDevices.value.map(d => d.conns))
})

// Conntrack gauge color
const gaugeColor = computed(() => {
  if (connUsage.value >= 80) return '#FF6B6B'
  if (connUsage.value >= 50) return '#FFB547'
  return '#64A0FF'
})

const gaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    center: ['50%', '60%'],
    radius: '100%',
    startAngle: 225,
    endAngle: -45,
    min: 0,
    max: 100,
    pointer: {
      icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
      length: '55%',
      width: 5,
      offsetCenter: [0, '-10%'],
      itemStyle: { color: gaugeColor.value },
    },
    progress: {
      show: true,
      width: 8,
      roundCap: true,
      itemStyle: { color: gaugeColor.value },
    },
    axisLine: {
      lineStyle: { width: 8, color: [[1, trackBg.value]] },
    },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    detail: {
      offsetCenter: [0, '55%'],
      fontSize: 11,
      fontWeight: 600,
      color: gaugeColor.value,
      fontFamily: "'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace",
      formatter: () => (Number(connUsage.value) || 0).toFixed(1) + '%',
    },
    title: { show: false },
    data: [{ value: connUsage.value }],
    animationDuration: 800,
    animationDurationUpdate: 800,
    animationEasingUpdate: 'cubicInOut',
  }],
}))
</script>

<template>
  <div style="display:flex;width:100%;height:100%;padding:8px 10px;gap:10px;overflow:hidden;">
    <!-- Left: Conntrack gauge -->
    <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;flex-shrink:0;width:80px;">
      <div style="width:72px;height:72px;">
        <v-chart :option="gaugeOption" autoresize />
      </div>
      <div style="text-align:center;">
        <div style="font-size:9px;" :style="{ color: textTer }">连接数</div>
        <div style="font-size:11px;font-weight:600;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textColor }">
          {{ connCount.toLocaleString() }}
        </div>
      </div>
    </div>
    <!-- Right: Top devices by connections -->
    <div style="flex:1;display:flex;flex-direction:column;gap:4px;min-width:0;overflow:hidden;">
      <div style="font-size:10px;flex-shrink:0;" :style="{ color: textTer }">连接数 Top 设备</div>
      <div style="flex:1;min-height:0;overflow-y:auto;display:flex;flex-direction:column;gap:3px;">
        <div v-for="device in topDevices.slice(0, 8)" :key="device.ip" style="display:flex;align-items:center;gap:6px;">
          <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;flex-shrink:0;width:90px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
                :style="{ color: textSec }" :title="device.hostname ? device.hostname + ' (' + device.ip + ')' : device.ip">
            {{ device.hostname || device.ip }}
          </span>
          <div style="flex:1;height:3px;border-radius:2px;overflow:hidden;" :style="{ background: trackBg }">
            <div style="height:100%;border-radius:2px;transition:width 0.6s ease;"
                 :style="{ width: (device.conns / maxConns * 100) + '%', background: gaugeColor }"></div>
          </div>
          <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;flex-shrink:0;text-align:right;min-width:24px;"
                :style="{ color: textColor }">{{ device.conns }}</span>
        </div>
        <div v-if="topDevices.length === 0" style="flex:1;display:flex;align-items:center;justify-content:center;">
          <span style="font-size:10px;" :style="{ color: textTer }">采集中...</span>
        </div>
      </div>
    </div>
  </div>
</template>
