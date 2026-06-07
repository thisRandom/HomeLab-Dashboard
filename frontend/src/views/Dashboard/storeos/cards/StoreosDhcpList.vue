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

interface DhcpClient {
  hostname: string
  ip: string
  mac: string
}

const clientCount = computed(() => {
  const v = props.metrics['storeos.dhcp.count']?.value
  return typeof v === 'number' ? v : 0
})

const clients = computed<DhcpClient[]>(() => {
  const raw = props.metrics['storeos.dhcp.clients']?.value
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
      <span style="font-size:13px;font-weight:600;" :style="{ color: textColor }">DHCP 客户端</span>
      <span style="font-size:11px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textSec }">{{ clientCount }}</span>
    </div>
    <!-- Client list -->
    <div style="flex:1;min-height:0;overflow-y:auto;overflow-x:hidden;display:flex;flex-direction:column;gap:4px;">
      <div
        v-for="client in clients"
        :key="client.mac"
        style="padding:6px 8px;border-radius:6px;"
        :style="{ background: bgColor }"
      >
        <div style="font-size:12px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" :style="{ color: textColor }">
          {{ client.hostname || '未知设备' }}
        </div>
        <div style="display:flex;justify-content:space-between;margin-top:2px;">
          <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: accentBlue }">{{ client.ip }}</span>
          <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textTer }">{{ client.mac }}</span>
        </div>
      </div>
      <div v-if="clients.length === 0" style="flex:1;display:flex;align-items:center;justify-content:center;">
        <span style="font-size:11px;" :style="{ color: textTer }">暂无客户端</span>
      </div>
    </div>
  </div>
</template>
