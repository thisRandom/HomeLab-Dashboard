<script setup lang="ts">
import { computed } from 'vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const connCount = computed(() => {
  const v = props.metrics['stream.connect_num']?.value
  return typeof v === 'number' ? v : 0
})

const cpuUsage = computed(() => {
  const v = props.metrics['cpu.usage']?.value
  return typeof v === 'number' ? v : 0
})

const cpuTemp = computed(() => {
  const v = props.metrics['cpu.temp']?.value
  return typeof v === 'number' ? v : 0
})

const hasConn = computed(() => connCount.value > 0)
const hasCpu = computed(() => cpuUsage.value > 0)

const trackBg = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.08)')
const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.85)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.35)' : 'rgba(255,255,255,0.3)')
</script>

<template>
  <div style="display:flex;flex-direction:column;justify-content:center;height:100%;gap:10px;padding:8px 12px;">
    <!-- 连接数 -->
    <div style="display:flex;align-items:center;gap:8px;">
      <span :style="{ fontSize:'11px', color:textSec, fontFamily:'-apple-system,BlinkMacSystemFont,PingFang SC,Microsoft YaHei,sans-serif', width:'28px', flexShrink:'0' }">连接</span>
      <div :style="{ flex:'1', height:'6px', borderRadius:'3px', background:trackBg, overflow:'hidden' }">
        <div :style="{ width: hasConn ? '100%' : '0%', height:'100%', borderRadius:'3px', background:'#FFB547', transition:'width 0.8s cubic-bezier(0.4,0,0.2,1)' }"></div>
      </div>
      <span :style="{ fontSize:'11px', color:textColor, fontFamily:'SF Mono,Cascadia Code,JetBrains Mono,Menlo,monospace', minWidth:'36px', textAlign:'right', flexShrink:'0' }">{{ hasConn ? connCount.toLocaleString() : '--' }}</span>
    </div>
    <!-- CPU -->
    <div style="display:flex;align-items:center;gap:8px;">
      <span :style="{ fontSize:'11px', color:textSec, fontFamily:'-apple-system,BlinkMacSystemFont,PingFang SC,Microsoft YaHei,sans-serif', width:'28px', flexShrink:'0' }">CPU</span>
      <div :style="{ flex:'1', height:'6px', borderRadius:'3px', background:trackBg, overflow:'hidden' }">
        <div :style="{ width: hasCpu ? cpuUsage + '%' : '0%', height:'100%', borderRadius:'3px', background: cpuUsage >= 80 ? '#FF6B6B' : '#64A0FF', transition:'width 0.8s cubic-bezier(0.4,0,0.2,1)' }"></div>
      </div>
      <span :style="{ fontSize:'11px', color:textColor, fontFamily:'SF Mono,Cascadia Code,JetBrains Mono,Menlo,monospace', minWidth:'36px', textAlign:'right', flexShrink:'0' }">{{ hasCpu ? cpuUsage.toFixed(0) + '%' : '--' }}</span>
    </div>
    <!-- 温度 -->
    <div v-if="hasCpu && cpuTemp > 0" style="display:flex;justify-content:space-between;align-items:center;padding-top:2px;">
      <span :style="{ fontSize:'10px', color:textTer, fontFamily:'-apple-system,BlinkMacSystemFont,PingFang SC,Microsoft YaHei,sans-serif' }">温度</span>
      <span :style="{ fontSize:'10px', color:textTer, fontFamily:'SF Mono,Cascadia Code,JetBrains Mono,Menlo,monospace' }">{{ cpuTemp }}°C</span>
    </div>
  </div>
</template>
