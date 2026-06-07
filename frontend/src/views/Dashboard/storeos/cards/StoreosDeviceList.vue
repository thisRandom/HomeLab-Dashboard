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
const accentOrange = '#FFB547'

interface Device {
  hostname: string
  ip: string
  mac: string
  source: string
}

const deviceCount = computed(() => {
  const v = props.metrics['storeos.device.count']?.value
  return typeof v === 'number' ? v : 0
})

const devices = computed<Device[]>(() => {
  const raw = props.metrics['storeos.device.details']?.value
  if (typeof raw !== 'string' || !raw || raw === '[]') return []
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
})
</script>

<template>
  <div style="display:flex;flex-direction:column;width:100%;height:100%;padding:12px;gap:8px;">
    <!-- Header -->
    <div style="display:flex;align-items:center;justify-content:space-between;">
      <span style="font-size:13px;font-weight:600;" :style="{ color: textColor }">在线设备</span>
      <span style="font-size:11px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textSec }">{{ deviceCount }}</span>
    </div>
    <!-- Device list -->
    <div style="flex:1;min-height:0;overflow-y:auto;overflow-x:hidden;display:flex;flex-direction:column;gap:4px;">
      <div
        v-for="device in devices"
        :key="device.mac || device.ip"
        style="padding:6px 8px;border-radius:6px;"
        :style="{ background: bgColor }"
      >
        <div style="display:flex;align-items:center;justify-content:space-between;">
          <span style="font-size:12px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:140px;"
                :style="{ color: textColor }">
            {{ device.hostname || '未知设备' }}
          </span>
          <span style="font-size:9px;padding:1px 4px;border-radius:3px;flex-shrink:0;"
                :style="{ background: device.source === 'dhcp' ? 'rgba(54,211,153,0.15)' : 'rgba(255,181,71,0.15)', color: device.source === 'dhcp' ? accentGreen : accentOrange }">
            {{ device.source === 'dhcp' ? 'DHCP' : '静态' }}
          </span>
        </div>
        <div style="display:flex;justify-content:space-between;margin-top:2px;">
          <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: accentBlue }">{{ device.ip }}</span>
          <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textTer }">{{ device.mac }}</span>
        </div>
      </div>
      <div v-if="devices.length === 0" style="flex:1;display:flex;align-items:center;justify-content:center;">
        <span style="font-size:11px;" :style="{ color: textTer }">暂无设备</span>
      </div>
    </div>
  </div>
</template>
