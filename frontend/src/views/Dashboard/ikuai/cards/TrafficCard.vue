<script setup lang="ts">
import { computed } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const upVal = computed(() => {
  const v = props.metrics['stream.total_up']?.value
  return typeof v === 'number' ? v : 0
})

const downVal = computed(() => {
  const v = props.metrics['stream.total_down']?.value
  return typeof v === 'number' ? v : 0
})

const hasData = computed(() => upVal.value > 0 || downVal.value > 0)

const emptyColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')

const totalUp = computed(() => toBytes(upVal.value))
const totalDown = computed(() => toBytes(downVal.value))

function toBytes(val: number): string {
  if (val === 0) return '--'
  if (val < 1024) return val + ' B'
  if (val < 1048576) return (val / 1024).toFixed(1) + ' KB'
  if (val < 1073741824) return (val / 1048576).toFixed(1) + ' MB'
  return (val / 1073741824).toFixed(2) + ' GB'
}

const option = computed(() => ({
  tooltip: { show: false },
  series: [{
    type: 'pie',
    radius: ['50%', '75%'],
    center: ['50%', '50%'],
    silent: true,
    label: { show: false },
    data: hasData.value
      ? [
          { value: upVal.value, name: '上行', itemStyle: { color: '#6C8EFF' } },
          { value: downVal.value, name: '下行', itemStyle: { color: '#2DD4BF' } },
        ]
      : [{ value: 1, name: '无数据', itemStyle: { color: emptyColor.value } }],
    emphasis: { disabled: true },
    animation: true,
    animationDuration: 500,
    animationDurationUpdate: 800,
    animationEasingUpdate: 'cubicInOut',
  }],
}))
</script>

<template>
  <div class="traffic-card">
    <div class="chart-wrap">
      <v-chart :option="option" autoresize />
    </div>
    <div class="traffic-legend">
      <div class="legend-item">
        <span class="dot" style="background: #6C8EFF" />
        <span class="label">↑</span>
        <span class="value">{{ totalUp }}</span>
      </div>
      <div class="legend-item">
        <span class="dot" style="background: #2DD4BF" />
        <span class="label">↓</span>
        <span class="value">{{ totalDown }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.traffic-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 2px;
  padding: 6px 0 10px;
}

.chart-wrap {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
}

.traffic-legend {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.label {
  font-size: 11px;
  color: var(--card-text-secondary, rgba(255, 255, 255, 0.5));
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.value {
  font-size: 11px;
  color: var(--card-text-primary, rgba(255, 255, 255, 0.85));
  font-family: 'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace;
  margin-left: auto;
}
</style>
