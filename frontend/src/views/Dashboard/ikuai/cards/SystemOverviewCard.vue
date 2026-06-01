<script setup lang="ts">
import { computed } from 'vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const bgColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.03)' : 'rgba(255,255,255,0.03)')
const accentBlue = '#64A0FF'

// 系统信息
const hostname = computed(() => {
  const v = props.metrics['system.hostname']?.value
  return typeof v === 'string' ? v : '--'
})

const version = computed(() => {
  const v = props.metrics['system.version']?.value
  return typeof v === 'string' ? v : '--'
})

// 运行时间
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
  if (days > 0) return `${days} 天 ${hours} 时 ${minutes} 分`
  if (hours > 0) return `${hours} 时 ${minutes} 分`
  return `${minutes} 分`
})

// 在线设备数
const deviceCount = computed(() => {
  const v = props.metrics['lan.device_count']?.value
  return typeof v === 'number' ? v : 0
})
</script>

<template>
  <div style="display:flex;flex-direction:column;width:100%;height:100%;padding:12px;gap:10px;">
    <!-- 顶部：主机名 + 版本 -->
    <div style="display:flex;align-items:center;justify-content:space-between;">
      <span style="font-size:13px;font-weight:600;" :style="{ color: textColor }">{{ hostname }}</span>
      <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;padding:2px 6px;border-radius:4px;"
            :style="{ background: bgColor, color: textTer }">
        {{ version }}
      </span>
    </div>

    <!-- 分隔线 -->
    <div style="height:1px;" :style="{ background: bgColor }"></div>

    <!-- 底部：运行时间 + 在线用户 -->
    <div style="display:flex;align-items:center;justify-content:space-between;">
      <!-- 运行时间 -->
      <div style="display:flex;flex-direction:column;gap:2px;">
        <span style="font-size:10px;" :style="{ color: textTer }">运行时间</span>
        <span style="font-size:12px;font-weight:500;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;"
              :style="{ color: textColor }">
          {{ uptimeStr }}
        </span>
      </div>

      <!-- 在线用户 -->
      <div style="display:flex;flex-direction:column;align-items:flex-end;gap:2px;">
        <span style="font-size:10px;" :style="{ color: textTer }">在线用户</span>
        <div style="display:flex;align-items:center;gap:4px;">
          <span style="font-size:16px;font-weight:700;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;"
                :style="{ color: accentBlue }">
            {{ deviceCount }}
          </span>
          <span style="font-size:10px;" :style="{ color: textSec }">人</span>
        </div>
      </div>
    </div>
  </div>
</template>
