<script setup lang="ts">
import { computed } from 'vue'
import FlipText from '@/components/FlipText.vue'
import type { MetricValue } from '@/api/metrics'

const props = defineProps<{ metrics: Record<string, MetricValue>; theme?: 'dark' | 'light' }>()

const upVal = computed(() => {
  const v = props.metrics['stream.upload']?.value
  return typeof v === 'number' ? v : 0
})

const downVal = computed(() => {
  const v = props.metrics['stream.download']?.value
  return typeof v === 'number' ? v : 0
})

const textColor = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.9)' : 'rgba(255,255,255,0.9)')
const textSec = computed(() => props.theme === 'light' ? 'rgba(30,30,40,0.55)' : 'rgba(255,255,255,0.5)')
const trackBg = computed(() => props.theme === 'light' ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.08)')

// 渐变色
const upGradient = 'linear-gradient(90deg, #6C8EFF, #A78BFA)'
const downGradient = 'linear-gradient(90deg, #2DD4BF, #34D399)'

// 速度条宽度（相对最大值，假设最大100MB/s）
const MAX_SPEED = 104857600 // 100MB/s
const upWidth = computed(() => Math.min((upVal.value / MAX_SPEED) * 100, 100))
const downWidth = computed(() => Math.min((downVal.value / MAX_SPEED) * 100, 100))

function splitSpeed(bps: number): { num: string; unit: string } {
  if (bps === 0) return { num: '--', unit: '' }
  if (bps < 1024) return { num: String(bps), unit: 'bps' }
  if (bps < 1048576) return { num: (bps / 1024).toFixed(1), unit: 'KB/s' }
  return { num: (bps / 1048576).toFixed(2), unit: 'MB/s' }
}

const upSpeed = computed(() => splitSpeed(upVal.value))
const downSpeed = computed(() => splitSpeed(downVal.value))
</script>

<style scoped>
.speed-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.speed-header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.speed-icon {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.speed-icon--up {
  background: linear-gradient(135deg, #6C8EFF, #A78BFA);
}

.speed-icon--down {
  background: linear-gradient(135deg, #2DD4BF, #34D399);
}

.speed-label {
  font-size: 10px;
  font-weight: 500;
  opacity: 0.6;
}

.speed-value {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-left: 24px;
}

.speed-bar {
  height: 3px;
  border-radius: 2px;
  margin-left: 24px;
  margin-top: 2px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.speed-bar::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent);
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}
</style>

<template>
  <div style="display:flex;flex-direction:column;justify-content:center;height:100%;padding:8px 10px;gap:12px;">
    <!-- Upload -->
    <div class="speed-row">
      <div class="speed-header">
        <div class="speed-icon speed-icon--up">↑</div>
        <span class="speed-label" :style="{ color: textSec }">上传</span>
      </div>
      <div class="speed-value">
        <FlipText
          :text="upSpeed.num"
          :color="textColor"
          :font-size="16"
          :font-weight="700"
          font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
          min-width="4ch"
        />
        <FlipText
          :text="upSpeed.unit"
          :color="textSec"
          :font-size="10"
          min-width="4ch"
        />
      </div>
      <div class="speed-bar" :style="{ width: upWidth + '%', background: upGradient }"></div>
    </div>

    <!-- Download -->
    <div class="speed-row">
      <div class="speed-header">
        <div class="speed-icon speed-icon--down">↓</div>
        <span class="speed-label" :style="{ color: textSec }">下载</span>
      </div>
      <div class="speed-value">
        <FlipText
          :text="downSpeed.num"
          :color="textColor"
          :font-size="16"
          :font-weight="700"
          font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
          min-width="4ch"
        />
        <FlipText
          :text="downSpeed.unit"
          :color="textSec"
          :font-size="10"
          min-width="4ch"
        />
      </div>
      <div class="speed-bar" :style="{ width: downWidth + '%', background: downGradient }"></div>
    </div>
  </div>
</template>
