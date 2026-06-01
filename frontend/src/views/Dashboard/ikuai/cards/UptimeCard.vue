<script setup lang="ts">
import { computed } from 'vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const trackBg = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.08)')

const uptime = computed(() => {
  const v = props.metrics['system.uptime']?.value
  return typeof v === 'number' ? v : 0
})

interface UptimeParts {
  days: number
  hours: number
  minutes: number
}

function parseUptime(seconds: number): UptimeParts {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return { days, hours, minutes }
}

const uptimeParts = computed(() => parseUptime(uptime.value))

// 主要显示（天数或小时）
const mainValue = computed(() => {
  if (uptime.value === 0) return '--'
  const { days, hours } = uptimeParts.value
  if (days > 0) return String(days)
  if (hours > 0) return String(hours)
  return String(uptimeParts.value.minutes)
})

// 单位
const mainUnit = computed(() => {
  if (uptime.value === 0) return ''
  const { days, hours } = uptimeParts.value
  if (days > 0) return '天'
  if (hours > 0) return '时'
  return '分'
})

// 详细信息
const detail = computed(() => {
  if (uptime.value === 0) return ''
  const { days, hours, minutes } = uptimeParts.value
  if (days > 0) return `${days}天 ${hours}时 ${minutes}分`
  if (hours > 0) return `${hours}时 ${minutes}分`
  return `${minutes}分`
})
</script>

<template>
  <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;padding:8px;gap:8px;">
    <!-- 主要数值 -->
    <div style="display:flex;align-items:baseline;gap:2px;">
      <span style="font-size:28px;font-weight:700;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;line-height:1;"
            :style="{ color: textColor }">
        {{ mainValue }}
      </span>
      <span v-if="mainUnit" style="font-size:12px;font-weight:500;" :style="{ color: textSec }">
        {{ mainUnit }}
      </span>
    </div>

    <!-- 标签 -->
    <span style="font-size:10px;" :style="{ color: textTer }">运行时间</span>

    <!-- 详细信息（如果超过1天显示） -->
    <div v-if="uptimeParts.days > 0" style="font-size:9px;text-align:center;padding:2px 6px;border-radius:4px;"
         :style="{ background: trackBg, color: textTer }">
      {{ detail }}
    </div>
  </div>
</template>
