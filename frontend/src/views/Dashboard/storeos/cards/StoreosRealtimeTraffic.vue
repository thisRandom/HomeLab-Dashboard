<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { VChart } from '@/plugins/echarts'
import FlipText from '@/components/FlipText.vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const MAX_POINTS = 30
const history = ref<{ time: string; rx: number; tx: number }[]>(
  Array.from({ length: MAX_POINTS }, () => ({ time: '', rx: 0, tx: 0 }))
)

const rxVal = computed(() => {
  const v = props.metrics['storeos.net.rx_rate']?.value
  return Number(v) || 0
})

const txVal = computed(() => {
  const v = props.metrics['storeos.net.tx_rate']?.value
  return Number(v) || 0
})

// Connections
const connVal = computed(() => {
  const v = props.metrics['storeos.conn.count']?.value
  return Number(v) || 0
})

const hasData = computed(() => rxVal.value > 0 || txVal.value > 0)

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

function formatSpeedTooltip(bps: number): string {
  if (bps === 0) return '0 bps'
  if (bps < 1024) return bps + ' bps'
  if (bps < 1048576) return (bps / 1024).toFixed(1) + ' KB/s'
  return (bps / 1048576).toFixed(2) + ' MB/s'
}

const rxSpeed = computed(() => splitSpeed(rxVal.value))
const txSpeed = computed(() => splitSpeed(txVal.value))

// Track history
watch([rxVal, txVal], ([rx, tx]) => {
  const now = new Date()
  const time = now.getMinutes().toString().padStart(2, '0') + ':' + now.getSeconds().toString().padStart(2, '0')
  history.value.push({ time, rx, tx })
  if (history.value.length > MAX_POINTS) {
    history.value.shift()
  }
})

onUnmounted(() => {
  history.value = []
})

// Connection gauge
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

const connGauge = computed(() => {
  const maxConn = 500
  const ratio = Math.min(connVal.value / maxConn, 1) * 100
  return percentGauge(ratio, '#FFB547', String(connVal.value))
})

// Line chart with smooth y-axis
const currentYMax = ref(100)
let yMaxTimer: ReturnType<typeof setInterval> | null = null

function getTargetYMax(): number {
  const rxs = history.value.map(h => h.rx / 1024)
  const txs = history.value.map(h => h.tx / 1024)
  const allVals = [...rxs, ...txs].filter(v => v > 0)
  const maxVal = allVals.length > 0 ? Math.max(...allVals) : 100
  return Math.ceil(maxVal * 1.3 / 10) * 10 || 100
}

watch([rxVal, txVal], () => {
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
  const rxs = history.value.map(h => h.rx / 1024)
  const txs = history.value.map(h => h.tx / 1024)

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
            const label = p.seriesIndex === 0 ? '↓ 接收' : '↑ 发送'
            const color = p.seriesIndex === 0 ? '#64A0FF' : '#A78BFA'
            html += `<br/>${label}: <b style="color:${color}">${formatSpeedTooltip(p.value * 1024)}</b>`
          })
          return html
        }
        return ''
      },
    },
    series: [
      {
        type: 'line',
        data: rxs,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5, color: '#64A0FF' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(100,160,255,0.3)' },
              { offset: 1, color: 'rgba(100,160,255,0)' },
            ],
          },
        },
        animationDuration: 800,
        animationEasing: 'cubicInOut',
      },
      {
        type: 'line',
        data: txs,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5, color: '#A78BFA' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(167,139,250,0.3)' },
              { offset: 1, color: 'rgba(167,139,250,0)' },
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
    <!-- Top: speed + connection gauge -->
    <div style="display:flex;align-items:center;justify-content:space-between;">
      <div style="display:flex;flex-direction:column;gap:6px;">
        <div style="display:flex;align-items:baseline;">
          <span style="color:#64A0FF;font-size:14px;font-weight:600;margin-right:5px;">↓</span>
          <FlipText
            :text="rxSpeed.num"
            :color="textColor"
            :font-size="20"
            :font-weight="700"
            font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
            min-width="5ch"
          />
          <FlipText
            :text="rxSpeed.unit"
            :color="textSec"
            :font-size="11"
            min-width="4ch"
            style="margin-left:2px;"
          />
        </div>
        <div style="display:flex;align-items:baseline;">
          <span style="color:#A78BFA;font-size:14px;font-weight:600;margin-right:5px;">↑</span>
          <FlipText
            :text="txSpeed.num"
            :color="textColor"
            :font-size="20"
            :font-weight="700"
            font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
            min-width="5ch"
          />
          <FlipText
            :text="txSpeed.unit"
            :color="textSec"
            :font-size="11"
            min-width="4ch"
            style="margin-left:2px;"
          />
        </div>
      </div>
      <!-- Connection gauge -->
      <div style="width:62px;height:62px;">
        <v-chart :option="connGauge" autoresize />
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
