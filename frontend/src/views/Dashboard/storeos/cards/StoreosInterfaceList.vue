<script setup lang="ts">
import { computed } from 'vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.85)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const dividerColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')
const accentGreen = '#36D399'
const accentRed = '#FF6B6B'
const accentBlue = '#64A0FF'

interface Iface {
  name: string
  status: string
  proto: string
  ip: string
}

const interfaces = computed<Iface[]>(() => {
  const raw = props.metrics['storeos.net.interfaces']?.value
  if (typeof raw !== 'string' || !raw) return []
  const result: Iface[] = []
  const lines = raw.split('\n').filter(l => l.trim())
  for (const line of lines) {
    // 格式: "name [STATUS] proto ip"
    const match = line.match(/^(\S+)\s+\[(\w+)\]\s+(\S*)\s*(.*)$/)
    if (match) {
      result.push({
        name: match[1],
        status: match[2],
        proto: match[3],
        ip: match[4].trim(),
      })
    }
  }
  return result
})
</script>

<template>
  <div style="display:flex;flex-direction:column;width:100%;height:100%;padding:10px 12px;gap:8px;overflow:hidden;">
    <!-- Header -->
    <div style="display:flex;align-items:center;justify-content:space-between;flex-shrink:0;">
      <span style="font-size:13px;font-weight:600;" :style="{ color: textColor }">网口状态</span>
      <span style="font-size:11px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;" :style="{ color: textSec }">{{ interfaces.length }}</span>
    </div>
    <!-- Interface list -->
    <div style="flex:1;min-height:0;overflow-y:auto;display:flex;flex-direction:column;gap:6px;">
      <div v-for="iface in interfaces" :key="iface.name" style="display:flex;align-items:center;gap:8px;">
        <!-- Status dot -->
        <span style="width:6px;height:6px;border-radius:50%;flex-shrink:0;"
              :style="{ background: iface.status === 'UP' ? accentGreen : accentRed }"></span>
        <!-- Name + proto -->
        <div style="flex:1;min-width:0;">
          <div style="font-size:12px;font-weight:500;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
               :style="{ color: textColor }">{{ iface.name }}</div>
          <div style="font-size:10px;" :style="{ color: textTer }">{{ iface.proto }}</div>
        </div>
        <!-- IP -->
        <span style="font-size:10px;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;flex-shrink:0;max-width:110px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
              :style="{ color: accentBlue }" :title="iface.ip">
          {{ iface.ip || '--' }}
        </span>
      </div>
      <div v-if="interfaces.length === 0" style="flex:1;display:flex;align-items:center;justify-content:center;">
        <span style="font-size:11px;" :style="{ color: textTer }">采集中...</span>
      </div>
    </div>
  </div>
</template>
