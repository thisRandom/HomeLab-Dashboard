<script setup lang="ts">
import { computed } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const temp = computed(() => {
  const v = props.metrics['pve.cpu.temp']?.value
  return typeof v === 'number' ? v : 0
})

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.85)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')

const tempColor = computed(() => {
  if (temp.value >= 80) return '#FF6B6B'  // 高温红色
  if (temp.value >= 60) return '#FFB547'  // 警告橙色
  return '#4ADE80'                        // 正常绿色
})

const tempStatus = computed(() => {
  if (temp.value >= 80) return '高温'
  if (temp.value >= 60) return '警告'
  return '正常'
})

// 温度表盘配置
const gaugeOption = computed(() => ({
  tooltip: { show: false },
  series: [{
    type: 'gauge',
    startAngle: 220,
    endAngle: -40,
    min: 0,
    max: 100,
    radius: '100%',
    progress: {
      show: true,
      width: 10,
      roundCap: true,
    },
    axisLine: {
      lineStyle: {
        width: 10,
        color: [[1, props.theme === 'light' ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)']],
      },
    },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    title: { show: false },
    detail: { show: false },
    data: [{ value: temp.value }],
    itemStyle: {
      color: tempColor.value,
    },
    animation: true,
    animationDuration: 500,
    animationDurationUpdate: 800,
    animationEasingUpdate: 'cubicInOut',
  }],
}))
</script>

<template>
  <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;padding:8px;gap:4px;">
    <!-- 温度表盘 -->
    <div style="width:72px;height:72px;position:relative;">
      <v-chart :option="gaugeOption" autoresize />
      <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;pointer-events:none;">
        <span style="font-size:20px;font-weight:700;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;line-height:1;"
              :style="{ color: tempColor }">
          {{ temp > 0 ? temp : '--' }}
        </span>
        <span v-if="temp > 0" style="font-size:10px;margin-top:2px;" :style="{ color: textSec }">°C</span>
      </div>
    </div>
    <!-- 状态标签 -->
    <div style="display:flex;align-items:center;gap:4px;">
      <div style="width:6px;height:6px;border-radius:50%;" :style="{ background: tempColor }"></div>
      <span style="font-size:11px;font-weight:500;" :style="{ color: textSec }">
        {{ temp > 0 ? tempStatus : '无数据' }}
      </span>
    </div>
  </div>
</template>
