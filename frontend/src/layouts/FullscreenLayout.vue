<script setup lang="ts">
</script>

<template>
  <div class="fullscreen-layout">
    <router-view v-slot="{ Component }">
      <transition name="page-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<style scoped>
.fullscreen-layout {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
</style>
