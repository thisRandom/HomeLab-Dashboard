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

function splitSpeed(bps: number): { num: string; unit: string } {
  if (bps === 0) return { num: '--', unit: '' }
  if (bps < 1024) return { num: String(bps), unit: 'bps' }
  if (bps < 1048576) return { num: (bps / 1024).toFixed(1), unit: 'KB/s' }
  return { num: (bps / 1048576).toFixed(2), unit: 'MB/s' }
}

const upSpeed = computed(() => splitSpeed(upVal.value))
const downSpeed = computed(() => splitSpeed(downVal.value))
</script>

<template>
  <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;padding:8px;gap:16px;">
    <!-- Upload -->
    <div style="display:flex;flex-direction:column;align-items:center;gap:2px;">
      <span style="color:#6C8EFF;font-size:14px;">↑</span>
      <div style="display:flex;align-items:baseline;">
        <FlipText
          :text="upSpeed.num"
          :color="textColor"
          :font-size="18"
          :font-weight="700"
          font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
          min-width="4ch"
        />
        <FlipText
          :text="upSpeed.unit"
          :color="textSec"
          :font-size="10"
          min-width="3ch"
          style="margin-left:2px;"
        />
      </div>
    </div>

    <!-- Divider -->
    <div style="width:40px;height:1px;background:rgba(255,255,255,0.08);"></div>

    <!-- Download -->
    <div style="display:flex;flex-direction:column;align-items:center;gap:2px;">
      <span style="color:#2DD4BF;font-size:14px;">↓</span>
      <div style="display:flex;align-items:baseline;">
        <FlipText
          :text="downSpeed.num"
          :color="textColor"
          :font-size="18"
          :font-weight="700"
          font-family="'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace"
          min-width="4ch"
        />
        <FlipText
          :text="downSpeed.unit"
          :color="textSec"
          :font-size="10"
          min-width="3ch"
          style="margin-left:2px;"
        />
      </div>
    </div>
  </div>
</template>
