<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import ThemeToggle from '@/components/common/ThemeToggle.vue'

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const collapsed = ref(false)

const navItems = [
  { key: 'config', title: '配置中心', icon: 'Settings' },
  { key: 'traffic', title: '流量账单', icon: 'File' },
  { key: 'radar', title: '网络雷达', icon: 'Wifi' },
  { key: 'hardware', title: '硬件矩阵', icon: 'Server' },
  { key: 'background', title: '仪表盘背景', icon: 'Picture' },
]

const selectedKeys = computed(() => [route.name as string])

function navigate(key: string) {
  router.push({ name: key })
}

function goDashboard() {
  window.open('/', '_blank')
}
</script>

<template>
  <a-layout class="app-layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      :width="220"
      :collapsed-width="60"
      collapsible
      class="app-sidebar"
    >
      <div class="sidebar-logo">
        <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="2" y="3" width="20" height="14" rx="2" />
          <path d="M8 21h8M12 17v4" />
        </svg>
        <span v-show="!collapsed">HomeLab</span>
      </div>
      <a-menu
        :selected-keys="selectedKeys"
        @menu-item-click="navigate"
      >
        <a-menu-item v-for="item in navItems" :key="item.key">
          <template #icon>
            <component :is="`icon-${item.icon}`" />
          </template>
          {{ item.title }}
        </a-menu-item>
      </a-menu>

      <div class="sidebar-footer">
        <a-button type="text" size="small" @click="goDashboard">
          <template #icon>
            <icon-dashboard />
          </template>
          <span v-show="!collapsed">返回仪表盘</span>
        </a-button>
      </div>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="app-header">
        <a-breadcrumb>
          <a-breadcrumb-item>管理后台</a-breadcrumb-item>
          <a-breadcrumb-item>
            {{ navItems.find((n) => n.key === route.name)?.title || route.name }}
          </a-breadcrumb-item>
        </a-breadcrumb>
        <ThemeToggle />
      </a-layout-header>

      <a-layout-content class="app-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.sidebar-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px;
  border-top: 1px solid var(--border-color);
}
</style>
