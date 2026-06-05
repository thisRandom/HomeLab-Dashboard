<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { VChart } from '@/plugins/echarts'
import FlipText from '@/components/FlipText.vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const MAX_POINTS = 30
const history = ref<{ time: string; up: number; down: number }[]>(
  Array.from({ length: MAX_POINTS }, () => ({ time: '', up: 0, down: 0 }))
)

const upVal = computed(() => {
  const v = props.metrics['stream.upload']?.value
  return typeof v === 'number' ? v : 0
})

const downVal = computed(() => {
  const v = props.metrics['stream.download']?.value
  return typeof v === 'number' ? v : 0
})

const hasData = computed(() => upVal.value > 0 || downVal.value > 0)

// CPU and connection metrics
const cpuVal = computed(() => {
  const v = props.metrics['cpu.usage']?.value
  return typeof v === 'number' ? v : 0
})

const connVal = computed(() => {
  const v = props.metrics['stream.connect_num']?.value
  return typeof v === 'number' ? v : 0
})

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const textTer = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.3)' : 'rgba(255,255,255,0.3)')
const lineBg = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.04)' : 'rgba(255,255,255,0.04)')

function splitSpeed(bps: number): { num: string; unit: string } {
  if (bps === 0) return { num: '--', unit: '' }
  if (bps < 1024) return { num: String(bps), unit: 'bps' }
  if (bps < 1048576) return { num: (bps / 1024).toFixed(1), unit: 'KB/s' }
  return { num: (bps / 1048576).toFixed(2), unit: 'MB/s' }
}

function formatCompact(bps: number): string {
  if (bps === 0) return '--'
  if (bps < 1024) return bps + ''
  if (bps < 1048576) return (bps / 1024).toFixed(1) + 'K'
  return (bps / 1048576).toFixed(1) + 'M'
}

function formatSpeedTooltip(kbps: number): string {
  if (kbps === 0) return '0 B/s'
  if (kbps < 1) return '< 1 KB/s'
  if (kbps < 1024) return kbps.toFixed(1) + ' KB/s'
  return (kbps / 1024).toFixed(2) + ' MB/s'
}

const upSpeed = computed(() => splitSpeed(upVal.value))
const downSpeed = computed(() => splitSpeed(downVal.value))

// Track history
watch([upVal, downVal], ([up, down]) => {
  const now = new Date()
  const time = now.getMinutes().toString().padStart(2, '0') + ':' + now.getSeconds().toString().padStart(2, '0')
  history.value.push({ time, up, down })
  if (history.value.length > MAX_POINTS) {
    history.value.shift()
  }
})

onUnmounted(() => {
  history.value = []
})

// CPU and connection gauges
function percentGauge(value: number, color: string, label: string) {
  const ratio = Math.max(0.02, value / 100)
  return {
    series: [{
      type: 'gauge',
      center: ['50%', '55%'],
      radius: '100%',
      startAngle: 225,
      endAngle: -45,
      min: 0,
      max: 1,
      pointer: {
        icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
        length: '55%',
        width: 6,
        offsetCenter: [0, '-10%'],
        itemStyle: { color },
      },
      progress: {
        show: true,
        width: 10,
        roundCap: true,
        itemStyle: { color },
      },
      axisLine: {
        lineStyle: {
          width: 10,
          color: [[1, 'rgba(255,255,255,0.06)']],
        },
      },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      detail: {
        offsetCenter: [0, '55%'],
        fontSize: 11,
        fontWeight: 600,
        color,
        fontFamily: "'SF Mono','Cascadia Code','JetBrains Mono','Menlo',monospace",
        formatter: () => label,
      },
      title: { show: false },
      data: [{ value: ratio }],
      animationDuration: 800,
      animationDurationUpdate: 800,
      animationEasingUpdate: 'cubicInOut',
    }],
  }
}

const cpuGauge = computed(() => {
  const color = cpuVal.value >= 80 ? '#FF6B6B' : '#64A0FF'
  return percentGauge(cpuVal.value, color, Math.round(cpuVal.value) + '%')
})

const connGauge = computed(() => {
  const maxConn = 500
  const ratio = Math.min(connVal.value / maxConn, 1) * 100
  return percentGauge(ratio, '#FFB547', String(connVal.value))
})

// Line chart
// Smooth y-axis max interpolation
const currentYMax = ref(100)
let yMaxTimer: ReturnType<typeof setInterval> | null = null

function getTargetYMax(): number {
  const ups = history.value.map(h => h.up / 1024)
  const downs = history.value.map(h => h.down / 1024)
  const allVals = [...ups, ...downs].filter(v => v > 0)
  const maxVal = allVals.length > 0 ? Math.max(...allVals) : 100
  return Math.ceil(maxVal * 1.3 / 10) * 10 || 100
}

watch([upVal, downVal], () => {
  if (!yMaxTimer) {
    yMaxTimer = setInterval(() => {
      const target = getTargetYMax()
      const diff = target - currentYMax.value
      if (Math.abs(diff) < 1) {
        currentYMax.value = target
        clearInterval(yMaxTimer!)
        yMaxTimer = null
      } else {
        currentYMax.value += diff * 0.15
      }
    }, 50)
  }
})

onUnmounted(() => {
  if (yMaxTimer) clearInterval(yMaxTimer)
})

const lineOption = computed(() => {
  const times = history.value.map(h => h.time)
  const ups = history.value.map(h => h.up / 1024)
  const downs = history.value.map(h => h.down / 1024)

  return {
    grid: { top: 4, right: 0, bottom: 4, left: 0 },
    xAxis: {
      type: 'category',
      data: times,
      boundaryGap: false,
      show: false,
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: currentYMax.value,
      show: false,
    },
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      zIndex: 10000,
      backgroundColor: props.theme === 'light' ? 'rgba(255,255,255,0.95)' : 'rgba(20,20,25,0.95)',
      borderColor: props.theme === 'light' ? 'rgba(0,0,0,0.1)' : 'rgba(255,255,255,0.1)',
      textStyle: {
        color: textColor.value,
        fontSize: 11,
      },
      formatter: (params: any) => {
        if (params.length > 0) {
          const time = params[0].axisValue
          let html = time
          params.forEach((p: any) => {
            const label = p.seriesIndex === 0 ? '↑ 上传' : '↓ 下载'
            const color = p.seriesIndex === 0 ? '#6C8EFF' : '#2DD4BF'
            html += `<br/>${label}: <b style="color:${color}">${formatSpeedTooltip(p.value)}</b>`
          })
          return html
        }
        return ''
      },
    },
    series: [
      {
        type: 'line',
        data: ups,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5, color: '#6C8EFF' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(108,142,255,0.3)' },
              { offset: 1, color: 'rgba(108,142,255,0)' },
            ],
          },
        },
        animationDuration: 800,
        animationEasing: 'cubicInOut',
      },
      {
        type: 'line',
        data: downs,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5, color: '#2DD4BF' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(45,212,191,0.3)' },
              { offset: 1, color: 'rgba(45,212,191,0)' },
            ],
          },
        },
        animationDuration: 800,
        animationEasing: 'cubicInOut',
      },
    ],
  }
})
</script>

<template>
  <div style="display:flex;flex-direction:column;width:100%;height:100%;padding:10px 8px 4px;gap:6px;">
    <!-- Top: speed + gauges -->
    <div style="display:flex;align-items:center;justify-content:space-between;">
      <div style="display:flex;flex-direction:column;gap:6px;">
        <div style="display:flex;align-items:baseline;">
          <span style="color:#6C8EFF;font-size:14px;font-weight:600;margin-right:5px;">↑</span>
          <FlipText
            :text="upSpeed.num"
            :color="textColor"
            :font-size="20"
            :font-weight="700"
            font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
            min-width="5ch"
          />
          <FlipText
            :text="upSpeed.unit"
            :color="textSec"
            :font-size="11"
            min-width="4ch"
            style="margin-left:2px;"
          />
        </div>
        <div style="display:flex;align-items:baseline;">
          <span style="color:#2DD4BF;font-size:14px;font-weight:600;margin-right:5px;">↓</span>
          <FlipText
            :text="downSpeed.num"
            :color="textColor"
            :font-size="20"
            :font-weight="700"
            font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
            min-width="5ch"
          />
          <FlipText
            :text="downSpeed.unit"
            :color="textSec"
            :font-size="11"
            min-width="4ch"
            style="margin-left:2px;"
          />
        </div>
      </div>
      <!-- CPU and Connection gauges -->
      <div style="display:flex;gap:0;">
        <div style="width:62px;height:62px;">
          <v-chart :option="cpuGauge" autoresize />
        </div>
        <div style="width:62px;height:62px;">
          <v-chart :option="connGauge" autoresize />
        </div>
      </div>
    </div>
    <!-- Bottom: trend line -->
    <div style="flex:1;min-height:0;border-radius:6px;overflow:hidden;" :style="{ background: lineBg }">
      <v-chart v-if="history.length > 1" :option="lineOption" autoresize />
      <div v-else style="display:flex;align-items:center;justify-content:center;height:100%;">
        <span :style="{ fontSize:'10px', color:textTer }">采集中...</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>
