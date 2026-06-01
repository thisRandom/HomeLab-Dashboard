<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { saveConfig } from '@/api/config'
import {
  getWallpapers,
  uploadWallpaper,
  deleteWallpaper,
  setActiveWallpaper,
} from '@/api/wallpaper'
import type { Wallpaper } from '@/api/wallpaper'

const BG_KEY = 'dashboard_background'
const THEME_KEY = 'card_theme'
const BLUR_KEY = 'card_blur'

const wallpapers = ref<Wallpaper[]>([])
const loading = ref(false)
const uploading = ref(false)
const activeId = ref<number | null>(null)
const cardTheme = ref<'dark' | 'light'>('dark')
const cardBlur = ref(20)
const previewBg = ref('linear-gradient(135deg, #0a0a0f 0%, #0d1117 50%, #0a0a0f 100%)')

const activeWallpaper = computed(() =>
  wallpapers.value.find(w => w.id === activeId.value)
)

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

async function loadWallpapers() {
  loading.value = true
  try {
    const { data } = await getWallpapers()
    wallpapers.value = data
    const active = data.find(w => w.active === true || w.isActive === true)
    if (active) {
      activeId.value = active.id
      previewBg.value = `url(${active.url}) center/cover no-repeat`
    }
  } catch {
    // use default
  } finally {
    loading.value = false
  }
}

async function loadTheme() {
  try {
    const { data } = await import('@/api/config').then(m => m.getAllConfig())
    const themeConfig = data.find(c => c.configKey === THEME_KEY)
    if (themeConfig?.configValue === 'light' || themeConfig?.configValue === 'dark') {
      cardTheme.value = themeConfig.configValue
    }
    const blurConfig = data.find(c => c.configKey === BLUR_KEY)
    if (blurConfig?.configValue) {
      const v = parseInt(blurConfig.configValue)
      if (!isNaN(v) && v >= 0 && v <= 50) cardBlur.value = v
    }
  } catch {
    // default dark
  }
}

async function handleUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    uploading.value = true
    try {
      const { data } = await uploadWallpaper(file)
      if (data.success && data.url) {
        Message.success('上传成功')
        await loadWallpapers()
      } else {
        Message.error(data.message || '上传失败')
      }
    } catch {
      Message.error('上传失败')
    } finally {
      uploading.value = false
    }
  }
  input.click()
}

async function selectWallpaper(wallpaper: Wallpaper) {
  activeId.value = wallpaper.id
  previewBg.value = `url(${wallpaper.url}) center/cover no-repeat`
  try {
    await setActiveWallpaper(wallpaper.id)
    await saveConfig(BG_KEY, `url(${wallpaper.url}) center/cover no-repeat`, 'string')
    Message.success('壁纸已切换')
  } catch {
    Message.error('切换失败')
  }
}

async function handleDelete(wallpaper: Wallpaper) {
  try {
    await deleteWallpaper(wallpaper.id)
    Message.success('已删除')
    if (activeId.value === wallpaper.id) {
      activeId.value = null
      previewBg.value = 'linear-gradient(135deg, #0a0a0f 0%, #0d1117 50%, #0a0a0f 100%)'
      await saveConfig(BG_KEY, previewBg.value, 'string')
    }
    await loadWallpapers()
  } catch {
    Message.error('删除失败')
  }
}

async function setCardTheme(theme: 'dark' | 'light') {
  cardTheme.value = theme
  try {
    await saveConfig(THEME_KEY, theme, 'string')
    Message.success(`卡片主题已切换为${theme === 'dark' ? '深色' : '浅色'}`)
  } catch {
    Message.error('保存失败')
  }
}

async function onBlurChange(val: number) {
  cardBlur.value = val
  try {
    await saveConfig(BLUR_KEY, String(val), 'number')
  } catch {
    // ignore
  }
}

onMounted(() => {
  loadWallpapers()
  loadTheme()
})
</script>

<template>
  <div class="bg-settings">
    <a-typography-title :level="3">仪表盘背景</a-typography-title>
    <a-typography-paragraph type="secondary">
      上传并管理壁纸图片，点击缩略图即可切换。支持深色/浅色卡片主题。
    </a-typography-paragraph>

    <a-card :loading="loading" class="settings-card">
      <!-- Upload section -->
      <div class="section">
        <div class="section-header">
          <span class="section-title">壁纸管理</span>
          <a-button type="primary" size="small" :loading="uploading" @click="handleUpload">
            <template #icon><icon-upload /></template>
            上传壁纸
          </a-button>
        </div>

        <div v-if="wallpapers.length === 0" class="empty-state">
          <a-empty description="暂无壁纸，点击上方按钮上传" />
        </div>

        <div v-else class="wallpaper-grid">
          <div
            v-for="w in wallpapers"
            :key="w.id"
            class="wallpaper-item"
            :class="{ 'wallpaper-item--active': activeId === w.id }"
            @click="selectWallpaper(w)"
          >
            <div class="wallpaper-thumb">
              <img :src="w.url" :alt="w.originalName || '壁纸'" />
              <div class="wallpaper-overlay">
                <span v-if="activeId === w.id" class="active-badge">当前</span>
                <button
                  class="delete-btn"
                  title="删除"
                  @click.stop="handleDelete(w)"
                >×</button>
              </div>
            </div>
            <div class="wallpaper-meta">
              <span class="wallpaper-name" :title="w.originalName || w.filename">
                {{ w.originalName || w.filename }}
              </span>
              <span class="wallpaper-size">{{ formatSize(w.fileSize) }}</span>
            </div>
          </div>
        </div>
      </div>

      <a-divider />

      <!-- Card theme -->
      <div class="section">
        <div class="section-title">卡片主题</div>
        <div class="theme-options">
          <div
            class="theme-option"
            :class="{ 'theme-option--active': cardTheme === 'dark' }"
            @click="setCardTheme('dark')"
          >
            <div class="theme-preview theme-preview--dark">
              <div class="theme-card">
                <div class="theme-card-line" />
                <div class="theme-card-line short" />
              </div>
            </div>
            <span class="theme-label">深色</span>
          </div>
          <div
            class="theme-option"
            :class="{ 'theme-option--active': cardTheme === 'light' }"
            @click="setCardTheme('light')"
          >
            <div class="theme-preview theme-preview--light">
              <div class="theme-card theme-card--light">
                <div class="theme-card-line" />
                <div class="theme-card-line short" />
              </div>
            </div>
            <span class="theme-label">浅色</span>
          </div>
        </div>
      </div>

      <a-divider />

      <!-- Blur intensity -->
      <div class="section">
        <div class="section-title">模糊强度</div>
        <div class="blur-control">
          <a-slider
            :model-value="cardBlur"
            :min="0"
            :max="50"
            :step="1"
            :show-tooltip="true"
            style="flex: 1"
            @change="onBlurChange"
          />
          <span class="blur-value">{{ cardBlur }}px</span>
        </div>
      </div>

      <a-divider />

      <!-- Preview -->
      <div class="section">
        <div class="section-title">预览效果</div>
        <div class="full-preview">
          <div
            class="full-preview-bg"
            :style="{ background: previewBg }"
          >
            <div
              class="preview-card"
              :class="cardTheme === 'light' ? 'preview-card--light' : ''"
              :style="{ backdropFilter: `blur(${cardBlur}px)`, WebkitBackdropFilter: `blur(${cardBlur}px)` }"
            >
              <div class="preview-card-body">
                <div class="preview-chart">
                  <div class="preview-chart-inner" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.bg-settings {
  padding: 0 20px;
}

.settings-card {
  margin-top: 16px;
}

.section {
  margin-bottom: 8px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
  margin-bottom: 12px;
}

.section-header .section-title {
  margin-bottom: 0;
}

.empty-state {
  padding: 32px 0;
}

.wallpaper-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.wallpaper-item {
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s;
  background: var(--color-bg-2);
}

.wallpaper-item:hover {
  border-color: var(--color-border-3);
}

.wallpaper-item--active {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.2);
}

.wallpaper-thumb {
  position: relative;
  height: 100px;
  overflow: hidden;
  background: #111;
}

.wallpaper-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.wallpaper-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}

.wallpaper-item:hover .wallpaper-overlay {
  opacity: 1;
}

.active-badge {
  background: rgb(var(--primary-6));
  color: #fff;
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.wallpaper-item--active .wallpaper-overlay {
  opacity: 1;
}

.delete-btn {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 80, 80, 0.85);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  padding: 0;
  transition: all 0.2s;
}

.delete-btn:hover {
  background: rgba(255, 60, 60, 0.95);
  transform: scale(1.1);
}

.wallpaper-meta {
  padding: 6px 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.wallpaper-name {
  font-size: 11px;
  color: var(--color-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100px;
}

.wallpaper-size {
  font-size: 10px;
  color: var(--color-text-4);
}

/* Theme selector */
.theme-options {
  display: flex;
  gap: 12px;
}

.theme-option {
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s;
  width: 120px;
}

.theme-option:hover {
  border-color: var(--color-border-3);
}

.theme-option--active {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.2);
}

.theme-preview {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
}

.theme-preview--dark {
  background: linear-gradient(135deg, #0a0a0f 0%, #0d1117 50%, #0a0a0f 100%);
}

.theme-preview--light {
  background: linear-gradient(135deg, #e8ecf0 0%, #f5f7fa 50%, #e8ecf0 100%);
}

.theme-card {
  width: 72px;
  height: 44px;
  background: rgba(15, 15, 20, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  backdrop-filter: blur(8px);
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.theme-card--light {
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(0, 0, 0, 0.1);
}

.theme-card-line {
  height: 6px;
  border-radius: 3px;
  background: rgba(100, 160, 255, 0.6);
}

.theme-card--light .theme-card-line {
  background: rgba(60, 120, 200, 0.6);
}

.theme-card-line.short {
  width: 60%;
}

.theme-label {
  display: block;
  text-align: center;
  padding: 6px;
  font-size: 12px;
  color: var(--color-text-2);
  background: var(--color-bg-2);
}

.blur-control {
  display: flex;
  align-items: center;
  gap: 12px;
}

.blur-value {
  font-size: 13px;
  color: var(--color-text-2);
  font-family: 'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Menlo', monospace;
  min-width: 42px;
  text-align: right;
}

/* Preview */
.full-preview {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
}

.full-preview-bg {
  min-height: 200px;
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-card {
  width: 200px;
  height: 200px;
  background: rgba(15, 15, 20, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  backdrop-filter: blur(20px);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.preview-card--light {
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(0, 0, 0, 0.1);
}

.preview-card-body {
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-chart {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: conic-gradient(
    rgba(100, 160, 255, 0.8) 0% 65%,
    rgba(255, 255, 255, 0.08) 65% 100%
  );
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-card--light .preview-chart {
  background: conic-gradient(
    rgba(60, 120, 200, 0.8) 0% 65%,
    rgba(0, 0, 0, 0.06) 65% 100%
  );
}

.preview-chart-inner {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: rgba(30, 30, 30, 0.95);
}

.preview-card--light .preview-chart-inner {
  background: rgba(240, 240, 245, 0.95);
}
</style>
