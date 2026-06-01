<script setup lang="ts">
import { computed } from 'vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')

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

const uptimeStr = computed(() => {
  if (uptime.value === 0) return '--'
  const { days, hours, minutes } = uptimeParts.value
  if (days > 0) return `${days}天`
  if (hours > 0) return `${hours}时`
  return `${minutes}分`
})

// 稳定性评估
const stability = computed(() => {
  if (uptime.value === 0) return { text: '无数据', color: textTer.value }
  const days = uptimeParts.value.days
  if (days >= 30) return { text: '非常稳定', color: '#4ADE80' }
  if (days >= 7) return { text: '稳定', color: '#36D399' }
  if (days >= 1) return { text: '一般', color: '#FFB547' }
  return { text: '刚启动', color: '#FF6B6B' }
})
</script>

<template>
  <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;padding:8px;gap:6px;">
    <!-- 图标 -->
    <div style="font-size:20px;">⏱️</div>

    <!-- 运行时间 -->
    <div style="display:flex;flex-direction:column;align-items:center;gap:2px;">
      <span style="font-size:20px;font-weight:700;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;"
            :style="{ color: textColor }">
        {{ uptimeStr }}
      </span>
      <span style="font-size:10px;" :style="{ color: textSec }">运行时间</span>
    </div>

    <!-- 稳定性标签 -->
    <div style="display:flex;align-items:center;gap:4px;">
      <div style="width:5px;height:5px;border-radius:50%;" :style="{ background: stability.color }"></div>
      <span style="font-size:10px;font-weight:500;" :style="{ color: stability.color }">
        {{ stability.text }}
      </span>
    </div>

    <!-- 详细时间（如果超过1天显示） -->
    <div v-if="uptimeParts.days > 0" style="font-size:9px;text-align:center;" :style="{ color: textTer }">
      {{ uptimeParts.days }}天 {{ uptimeParts.hours }}时 {{ uptimeParts.minutes }}分
    </div>
  </div>
</template>
