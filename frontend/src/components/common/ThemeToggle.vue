<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
</script>

<template>
  <a-tooltip :content="themeStore.theme === 'dark' ? '切换亮色模式' : '切换暗色模式'">
    <a-button
      type="text"
      shape="circle"
      @click="themeStore.toggle()"
    >
      <template #icon>
        <icon-sun v-if="themeStore.theme === 'dark'" />
        <icon-moon v-else />
      </template>
    </a-button>
  </a-tooltip>
</template>
