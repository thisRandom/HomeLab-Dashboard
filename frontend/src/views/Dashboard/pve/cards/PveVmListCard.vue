<script setup lang="ts">
import { computed } from 'vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const dividerColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')
const trackBg = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.08)')

interface VmInfo {
  vmid: number
  name: string
  type: string
  status: string
  cpu: number
  mem: number
  maxmem: number
  disk: number
  maxdisk: number
  netin: number
  netout: number
}

const vmCount = computed(() => {
  const v = props.metrics['pve.vm.count']?.value
  return typeof v === 'number' ? v : 0
})
const ctCount = computed(() => {
  const v = props.metrics['pve.ct.count']?.value
  return typeof v === 'number' ? v : 0
})
const runningCount = computed(() => {
  const v = props.metrics['pve.vm.running_count']?.value
  return typeof v === 'number' ? v : 0
})

const vmList = computed<VmInfo[]>(() => {
  const raw = props.metrics['pve.vm.details']?.value
  if (typeof raw !== 'string') return []
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
})

function memPercent(vm: VmInfo): number {
  if (!vm.maxmem || vm.maxmem === 0) return 0
  return Math.round(vm.mem * 100 / vm.maxmem)
}

function cpuPercent(vm: VmInfo): number {
  // cpu is a fraction 0~1, cpus is the count
  if (!vm.cpu) return 0
  return Math.round(vm.cpu * 100)
}

function formatMem(bytes: number): string {
  if (bytes === 0) return '0'
  const gb = bytes / (1024 * 1024 * 1024)
  if (gb >= 1) return gb.toFixed(1) + 'G'
  return Math.round(bytes / (1024 * 1024)) + 'M'
}

function memBarColor(percent: number): string {
  if (percent >= 90) return '#FF6B6B'
  if (percent >= 70) return '#F59E0B'
  return '#A78BFA'
}
</script>

<template>
  <div style="display:flex;flex-direction:column;width:100%;height:100%;padding:10px 12px;gap:6px;overflow:hidden;">
    <!-- Header -->
    <div style="display:flex;align-items:center;justify-content:space-between;flex-shrink:0;">
      <div style="display:flex;gap:8px;align-items:center;">
        <span style="font-size:11px;" :style="{ color: textSec }">
          <span style="font-weight:600;" :style="{ color: textColor }">{{ vmCount }}</span> 台虚拟机
        </span>
        <span style="font-size:11px;" :style="{ color: textSec }">
          <span style="font-weight:600;" :style="{ color: textColor }">{{ ctCount }}</span> 个容器
        </span>
      </div>
      <span style="font-size:10px;display:flex;align-items:center;gap:4px;" :style="{ color: textSec }">
        <span style="width:5px;height:5px;border-radius:50%;background:#4ADE80;display:inline-block;"></span>
        {{ runningCount }} 运行中
      </span>
    </div>

    <!-- Divider -->
    <div style="height:1px;flex-shrink:0;" :style="{ background: dividerColor }"></div>

    <!-- VM list -->
    <div style="flex:1;overflow-y:auto;min-height:0;">
      <div v-if="vmList.length === 0" style="text-align:center;padding:16px 0;">
        <span style="font-size:11px;" :style="{ color: textTer }">暂无数据</span>
      </div>
      <div v-for="vm in vmList" :key="vm.vmid"
           style="display:flex;flex-direction:column;gap:3px;padding:5px 0;border-bottom:1px solid;"
           :style="{ borderBottomColor: dividerColor }">
        <!-- Row 1: status + type + name + id -->
        <div style="display:flex;align-items:center;gap:6px;">
          <span style="width:6px;height:6px;border-radius:50%;flex-shrink:0;"
                :style="{ background: vm.status === 'running' ? '#4ADE80' : vm.status === 'paused' ? '#F59E0B' : 'rgba(255,255,255,0.15)' }"></span>
          <span style="font-size:9px;padding:1px 4px;border-radius:3px;font-weight:600;flex-shrink:0;letter-spacing:0.5px;"
                :style="{ background: vm.type === 'qemu' ? 'rgba(100,160,255,0.15)' : 'rgba(167,139,250,0.15)', color: vm.type === 'qemu' ? '#64A0FF' : '#A78BFA' }">
            {{ vm.type === 'qemu' ? 'VM' : 'CT' }}
          </span>
          <span style="font-size:11px;font-weight:500;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
                :style="{ color: textColor }">
            {{ vm.name || 'VM ' + vm.vmid }}
          </span>
          <span style="font-size:9px;flex-shrink:0;font-family:'SF Mono','Cascadia Code',monospace;"
                :style="{ color: textTer }">
            #{{ vm.vmid }}
          </span>
        </div>
        <!-- Row 2: CPU + Memory bars (only when running) -->
        <div v-if="vm.status === 'running'" style="display:flex;gap:8px;padding-left:12px;">
          <!-- CPU bar -->
          <div style="flex:1;display:flex;align-items:center;gap:4px;">
            <span style="font-size:9px;flex-shrink:0;width:22px;" :style="{ color: textTer }">CPU</span>
            <div style="flex:1;height:4px;border-radius:2px;overflow:hidden;" :style="{ background: trackBg }">
              <div style="height:100%;border-radius:2px;transition:width 0.8s cubic-bezier(0.4,0,0.2,1);"
                   :style="{ width: cpuPercent(vm) + '%', background: cpuPercent(vm) >= 80 ? '#FF6B6B' : '#64A0FF' }"></div>
            </div>
            <span style="font-size:9px;flex-shrink:0;width:24px;text-align:right;font-family:'SF Mono','Cascadia Code',monospace;"
                  :style="{ color: textSec }">{{ cpuPercent(vm) }}%</span>
          </div>
          <!-- Mem bar -->
          <div style="flex:1;display:flex;align-items:center;gap:4px;">
            <span style="font-size:9px;flex-shrink:0;width:28px;" :style="{ color: textTer }">RAM</span>
            <div style="flex:1;height:4px;border-radius:2px;overflow:hidden;" :style="{ background: trackBg }">
              <div style="height:100%;border-radius:2px;transition:width 0.8s cubic-bezier(0.4,0,0.2,1);"
                   :style="{ width: memPercent(vm) + '%', background: memBarColor(memPercent(vm)) }"></div>
            </div>
            <span style="font-size:9px;flex-shrink:0;text-align:right;font-family:'SF Mono','Cascadia Code',monospace;white-space:nowrap;"
                  :style="{ color: textSec }">{{ formatMem(vm.mem) }}/{{ formatMem(vm.maxmem) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
