<script setup lang="ts">
import { computed } from 'vue'
import { VChart } from '@/plugins/echarts'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const used = computed(() => {
  const v = props.metrics['memory.used']?.value
  return typeof v === 'number' ? v : 0
})

const total = computed(() => {
  const v = props.metrics['memory.total']?.value
  return typeof v === 'number' ? v : 0
})

const usage = computed(() => {
  const v = props.metrics['memory.usage']?.value
  return typeof v === 'number' ? v : 0
})

const hasData = computed(() => total.value > 0)

const emptyColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)')
const freeColor = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)')

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
          { value: used.value, name: '已用', itemStyle: { color: '#A78BFA' } },
          { value: total.value - used.value, name: '空闲', itemStyle: { color: freeColor.value } },
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
  <div class="mem-card">
    <div class="chart-wrap">
      <v-chart :option="option" autoresize />
      <div class="mem-center">
        <span class="mem-pct" :class="{ 'mem-pct--empty': !hasData }">{{ hasData ? usage.toFixed(1) + '%' : '--' }}</span>
      </div>
    </div>
    <div class="mem-info">
      <span class="mem-detail">{{ hasData ? `${used.toFixed(0)} / ${total.toFixed(0)} MB` : '等待数据...' }}</span>
    </div>
  </div>
</template>

<style scoped>
.mem-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 6px;
  padding: 4px 0;
}

.chart-wrap {
  width: 72px;
  height: 72px;
  position: relative;
  flex-shrink: 0;
}

.mem-center {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.mem-pct {
  font-size: 12px;
  font-weight: 600;
  color: var(--card-accent, #A78BFA);
  font-family: 'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace;
}

.mem-pct--empty {
  color: var(--card-text-tertiary, rgba(255, 255, 255, 0.25));
}

.mem-info {
  text-align: center;
}

.mem-detail {
  font-size: 11px;
  color: var(--card-text-secondary, rgba(255, 255, 255, 0.5));
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
</style>
