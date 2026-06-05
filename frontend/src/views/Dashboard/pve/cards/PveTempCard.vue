<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

// 历史数据存储（最近60个数据点，约2分钟）
const MAX_HISTORY = 60
const tempHistory = ref<number[]>([])
const timeLabels = ref<string[]>([])

// 当前温度
const temp = computed(() => {
  const v = props.metrics['cpu.temp']?.value
  return typeof v === 'number' ? v : 0
})

// 主题颜色
const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.85)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.35)' : 'rgba(255,255,255,0.3)')
const trackBg = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.08)')

// 温度颜色
const tempColor = computed(() => {
  if (temp.value >= 80) return '#FF6B6B'
  if (temp.value >= 60) return '#FFB547'
  return '#4ADE80'
})

const tempStatus = computed(() => {
  if (temp.value >= 80) return '高温'
  if (temp.value >= 60) return '警告'
  return '正常'
})

// 历史数据更新
watch(temp, (newVal) => {
  if (newVal > 0) {
    const now = new Date()
    const timeStr = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`

    tempHistory.value.push(newVal)
    timeLabels.value.push(timeStr)

    if (tempHistory.value.length > MAX_HISTORY) {
      tempHistory.value.shift()
      timeLabels.value.shift()
    }
  }
})

// 温度仪表盘配置
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
      width: 8,
      roundCap: true,
    },
    axisLine: {
      lineStyle: {
        width: 8,
        color: [[1, trackBg.value]],
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

// 历史曲线配置
const chartOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    backgroundColor: props.theme === 'light' ? 'rgba(255,255,255,0.95)' : 'rgba(20,20,25,0.95)',
    borderColor: props.theme === 'light' ? 'rgba(0,0,0,0.1)' : 'rgba(255,255,255,0.1)',
    textStyle: {
      color: textColor.value,
      fontSize: 11,
    },
    formatter: (params: any) => {
      if (params.length > 0) {
        const value = params[0].value
        return `${params[0].axisValue}<br/>温度: <b style="color:${getTempColor(value)}">${value}°C</b>`
      }
      return ''
    },
  },
  grid: {
    top: 8,
    right: 8,
    bottom: 20,
    left: 28,
  },
  xAxis: {
    type: 'category',
    data: timeLabels.value,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: {
      color: textTer.value,
      fontSize: 9,
      interval: Math.floor(timeLabels.value.length / 4),
    },
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    splitNumber: 4,
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: {
      color: trackBg.value,
    },
    axisLabel: {
      color: textTer.value,
      fontSize: 9,
      formatter: '{value}',
    },
  },
  series: [{
    type: 'line',
    data: tempHistory.value,
    smooth: true,
    symbol: 'none',
    lineStyle: {
      color: tempColor.value,
      width: 2,
    },
    areaStyle: {
      color: {
        type: 'linear',
        x: 0,
        y: 0,
        x2: 0,
        y2: 1,
        colorStops: [
          { offset: 0, color: `${tempColor.value}40` },
          { offset: 1, color: `${tempColor.value}05` },
        ],
      },
    },
    animation: true,
    animationDuration: 300,
  }],
}))

function getTempColor(value: number): string {
  if (value >= 80) return '#FF6B6B'
  if (value >= 60) return '#FFB547'
  return '#4ADE80'
}
</script>

<template>
  <div style="display:flex;flex-direction:column;width:100%;height:100%;padding:8px;gap:8px;">
    <!-- 上半部分：仪表盘 + 状态 -->
    <div style="display:flex;align-items:center;gap:8px;flex-shrink:0;">
      <!-- 仪表盘 -->
      <div style="width:56px;height:56px;position:relative;flex-shrink:0;">
        <v-chart :option="gaugeOption" autoresize />
        <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;pointer-events:none;">
          <span style="font-size:14px;font-weight:700;font-family:'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace;line-height:1;"
                :style="{ color: tempColor }">
            {{ temp > 0 ? temp : '--' }}
          </span>
          <span v-if="temp > 0" style="font-size:8px;margin-top:1px;" :style="{ color: textTer }">°C</span>
        </div>
      </div>

      <!-- 状态信息 -->
      <div style="flex:1;display:flex;flex-direction:column;gap:4px;min-width:0;">
        <div style="display:flex;align-items:center;gap:4px;">
          <div style="width:6px;height:6px;border-radius:50%;flex-shrink:0;" :style="{ background: tempColor }"></div>
          <span style="font-size:11px;font-weight:500;" :style="{ color: textSec }">
            {{ temp > 0 ? tempStatus : '无数据' }}
          </span>
        </div>
        <div style="font-size:10px;" :style="{ color: textTer }">
          最高: {{ tempHistory.length > 0 ? Math.max(...tempHistory) : '--' }}°C
        </div>
        <div style="font-size:10px;" :style="{ color: textTer }">
          最低: {{ tempHistory.length > 0 ? Math.min(...tempHistory) : '--' }}°C
        </div>
      </div>
    </div>

    <!-- 下半部分：历史曲线 -->
    <div style="flex:1;min-height:0;">
      <v-chart v-if="tempHistory.length > 1" :option="chartOption" autoresize />
      <div v-else style="display:flex;align-items:center;justify-content:center;height:100%;">
        <span style="font-size:10px;" :style="{ color: textTer }">等待数据...</span>
      </div>
    </div>
  </div>
</template>
