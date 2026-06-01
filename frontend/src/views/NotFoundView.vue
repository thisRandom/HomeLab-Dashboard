<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <a-result status="404" title="404" subtitle="页面不存在">
      <template #extra>
        <a-button type="primary" @click="router.push('/')">
          返回首页
        </a-button>
      </template>
    </a-result>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--bg-primary);
}
</style>
