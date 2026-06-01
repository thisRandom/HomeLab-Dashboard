<script setup lang="ts">
import { computed } from 'vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const bgColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.03)' : 'rgba(255,255,255,0.03)')
const accentGreen = '#36D399'
const accentBlue = '#64A0FF'

interface Device {
  name: string
  ip: string
  os: string
}

const deviceCount = computed(() => {
  const v = props.metrics['lan.device_count']?.value
  return typeof v === 'number' ? v : 0
})

const devices = computed<Device[]>(() => {
  const raw = props.metrics['lan.devices']?.value
  if (typeof raw !== 'string' || !raw) return []

  const deviceMap = new Map<string, Device>()
  const lines = raw.split('\n').filter(l => l.trim())

  for (const line of lines) {
    const match = line.match(/\((\d+\.\d+\.\d+\.\d+)\)\s*\[(.+?)\]/)
    if (!match) continue

    const ip = match[1]
    const os = match[2]

    const beforeParens = line.substring(0, line.indexOf('(')).trim()
    const nameMatch = beforeParens.match(/(?:\]|\[.*?\])\s*(.+)$/) || [null, beforeParens]
    const name = (nameMatch[1] || beforeParens).trim()

    if (!name) continue

    if (!deviceMap.has(name)) {
      deviceMap.set(name, { name, ip, os })
    }
  }

  return Array.from(deviceMap.values())
})
</script>

<template>
  <div style="display:flex;flex-direction:column;width:100%;height:100%;padding:12px;gap:8px;">
    <!-- Header -->
    <div style="display:flex;align-items:center;justify-content:space-between;">
      <span style="font-size:13px;font-weight:600;" :style="{ color: textColor }">在线用户</span>
    </div>
    <!-- Device list -->
    <div style="flex:1;min-height:0;overflow-y:auto;overflow-x:hidden;display:flex;flex-direction:column;gap:4px;" :style="{ '::-webkit-scrollbar': { width: '3px' }, '::-webkit-scrollbar-thumb': { background: 'rgba(255,255,255,0.1)', borderRadius: '2px' } }">
      <div
        v-for="device in devices"
        :key="device.name"
        style="padding:6px 8px;border-radius:6px;"
        :style="{ background: bgColor }"
      >
        <div style="font-size:12px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" :style="{ color: textColor }">{{ device.name }}</div>
        <div style="font-size:10px;margin-top:2px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: accentBlue }">{{ device.ip }}</div>
      </div>
      <div v-if="devices.length === 0" style="flex:1;display:flex;align-items:center;justify-content:center;">
        <span style="font-size:11px;" :style="{ color: textTer }">采集中...</span>
      </div>
    </div>
  </div>
</template>
