<script setup lang="ts">
import { ref, watch, onUnmounted, computed } from 'vue'

const props = withDefaults(defineProps<{
  text: string
  color?: string
  fontSize?: number
  fontWeight?: number
  fontFamily?: string
  minWidth?: string
}>(), {
  color: 'inherit',
  fontSize: 16,
  fontWeight: 400,
  fontFamily: 'inherit',
  minWidth: 'auto'
})

const displayText = ref(props.text)
const oldText = ref('')
const isAnimating = ref(false)
let animTimer: ReturnType<typeof setTimeout> | null = null

const lineHeight = computed(() => props.fontSize * 1.2 + 'px')

watch(() => props.text, (newVal, oldVal) => {
  if (newVal === oldVal || isAnimating.value) return
  if (animTimer) clearTimeout(animTimer)

  oldText.value = oldVal || ''
  displayText.value = newVal
  isAnimating.value = true

  animTimer = setTimeout(() => {
    isAnimating.value = false
    oldText.value = ''
    animTimer = null
  }, 350)
})

onUnmounted(() => {
  if (animTimer) clearTimeout(animTimer)
})
</script>

<template>
  <span
    class="flip-text"
    :style="{
      color,
      fontSize: fontSize + 'px',
      fontWeight,
      fontFamily,
      minWidth,
      height: lineHeight
    }"
  >
    <span v-if="isAnimating && oldText" class="flip-roll">
      <span class="flip-roll-old">{{ oldText }}</span>
      <span class="flip-roll-new">{{ displayText }}</span>
    </span>
    <span v-else class="flip-static">{{ displayText }}</span>
  </span>
</template>

<style scoped>
.flip-text {
  display: inline-block;
  overflow: hidden;
  vertical-align: bottom;
  position: relative;
}

.flip-static {
  display: block;
  white-space: pre;
}

.flip-roll {
  display: block;
  position: relative;
  animation: rollUp 0.35s cubic-bezier(0.35, 0, 0.25, 1) forwards;
}

.flip-roll-old,
.flip-roll-new {
  display: block;
  white-space: pre;
}

.flip-roll-old {
  position: relative;
}

.flip-roll-new {
  position: absolute;
  top: 100%;
  left: 0;
}

@keyframes rollUp {
  0% {
    transform: translateY(0);
  }
  100% {
    transform: translateY(-100%);
  }
}
</style>
