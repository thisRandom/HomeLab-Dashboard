import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import FullscreenLayout from '@/layouts/FullscreenLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: FullscreenLayout,
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: 'HomeLab Dashboard' },
        },
      ],
    },
    {
      path: '/admin',
      component: DefaultLayout,
      children: [
        {
          path: '',
          redirect: { name: 'config' },
        },
        {
          path: 'config',
          name: 'config',
          component: () => import('@/views/ConfigCenterView.vue'),
          meta: { title: '配置中心' },
        },
        {
          path: 'traffic',
          name: 'traffic',
          component: () => import('@/views/TrafficBillingView.vue'),
          meta: { title: '流量账单' },
        },
        {
          path: 'radar',
          name: 'radar',
          component: () => import('@/views/NetworkRadarView.vue'),
          meta: { title: '网络雷达' },
        },
        {
          path: 'hardware',
          name: 'hardware',
          component: () => import('@/views/HardwareMatrixView.vue'),
          meta: { title: '硬件矩阵' },
        },
        {
          path: 'background',
          name: 'background',
          component: () => import('@/views/BackgroundSettingsView.vue'),
          meta: { title: '仪表盘背景' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || 'HomeLab'
  document.title = `${title} - HomeLab Dashboard`
})

export default router
