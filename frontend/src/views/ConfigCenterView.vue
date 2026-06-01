<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { usePluginStore } from '../stores/plugin'
import { getAllConfig, saveConfigBatch } from '../api/config'
import { Message } from '@arco-design/web-vue'

const store = usePluginStore()

const enabled = ref(false)
const dialogVisible = ref(false)
const testing = ref(false)
const detailVisible = ref(false)
const detailConfig = reactive({ address: '', username: '', password: '' })

const form = reactive({
  address: '',
  username: '',
  password: '',
})

// Polling interval settings
const pollingForm = reactive({
  realtime: 2000,
  frequent: 30000,
  slow: 300000,
})
const pollingLoading = ref(false)

onMounted(async () => {
  await store.fetchPlugins()
  const plugin = store.plugins.find(p => p.pluginId === 'ikuai')
  if (plugin) enabled.value = plugin.enabled

  // Load existing config for dialog default values
  await store.fetchIkuaiConfig()
  form.address = store.ikuaiConfig.address || ''
  form.username = store.ikuaiConfig.username || ''
  form.password = store.ikuaiConfig.password || ''

  // Load polling interval settings
  await loadPollingConfig()
})

async function loadPollingConfig() {
  try {
    const { data } = await getAllConfig()
    const realtimeConfig = data.find(c => c.configKey === 'polling_realtime_interval')
    const frequentConfig = data.find(c => c.configKey === 'polling_frequent_interval')
    const slowConfig = data.find(c => c.configKey === 'polling_slow_interval')
    if (realtimeConfig?.configValue) pollingForm.realtime = parseInt(realtimeConfig.configValue) || 2000
    if (frequentConfig?.configValue) pollingForm.frequent = parseInt(frequentConfig.configValue) || 30000
    if (slowConfig?.configValue) pollingForm.slow = parseInt(slowConfig.configValue) || 300000
  } catch {
    // use defaults
  }
}

async function savePollingConfig() {
  // Validate values
  if (pollingForm.realtime < 500) {
    Message.warning('实时数据间隔不能小于 500ms')
    return
  }
  if (pollingForm.frequent < 1000) {
    Message.warning('频繁数据间隔不能小于 1000ms')
    return
  }
  if (pollingForm.slow < 5000) {
    Message.warning('慢速数据间隔不能小于 5000ms')
    return
  }

  pollingLoading.value = true
  try {
    await saveConfigBatch([
      { key: 'polling_realtime_interval', value: String(pollingForm.realtime), valueType: 'number' },
      { key: 'polling_frequent_interval', value: String(pollingForm.frequent), valueType: 'number' },
      { key: 'polling_slow_interval', value: String(pollingForm.slow), valueType: 'number' },
    ])
    Message.success('轮询间隔设置已保存')
  } catch {
    Message.error('保存失败')
  } finally {
    pollingLoading.value = false
  }
}

function handleToggle(val: string | number | boolean) {
  const newVal = !!val
  if (newVal) {
    // Enabling — open dialog to collect config
    dialogVisible.value = true
  } else {
    // Disabling — direct action
    doDisable()
  }
}

async function doDisable() {
  try {
    const result = await store.toggleIkuai(false)
    if (result?.success) {
      enabled.value = false
      Message.success('插件已禁用')
    } else {
      Message.error(result?.message || '操作失败')
    }
  } catch {
    Message.error('请求失败')
  }
}

async function handleDialogSubmit() {
  if (!form.address || !form.username || !form.password) {
    Message.warning('请填写完整的配置信息')
    return
  }
  testing.value = true
  try {
    const result = await store.testSaveEnableIkuai({
      address: form.address,
      username: form.username,
      password: form.password,
    })
    if (result?.success) {
      enabled.value = true
      dialogVisible.value = false
      Message.success(result.message)
    } else {
      Message.error(result?.message || '连接失败')
    }
  } catch {
    Message.error('请求失败')
  } finally {
    testing.value = false
  }
}

function handleDialogClose() {
  // If dialog closed without success, switch back to disabled
  if (!store.plugins.find(p => p.pluginId === 'ikuai')?.enabled) {
    enabled.value = false
  }
}

async function showDetail() {
  await store.fetchIkuaiConfig()
  detailConfig.address = store.ikuaiConfig.address || ''
  detailConfig.username = store.ikuaiConfig.username || ''
  detailConfig.password = store.ikuaiConfig.password || ''
  detailVisible.value = true
}
</script>

<template>
  <div class="config-view">
    <a-typography-title :level="3">配置中心</a-typography-title>

    <a-card title="iKuai 路由器" :bordered="true">
      <template #extra>
        <a-switch
          v-model="enabled"
          :loading="store.loading"
          checked-text="启用"
          unchecked-text="禁用"
          @change="handleToggle"
        />
      </template>

      <a-typography-paragraph type="secondary">
        {{ enabled ? '插件已启用，正在采集数据' : '插件未启用，请开启后配置连接信息' }}
      </a-typography-paragraph>

      <a-button v-if="enabled" type="outline" size="small" @click="showDetail">
        查看连接信息
      </a-button>
    </a-card>

    <!-- Detail Dialog (read-only) -->
    <a-modal
      v-model:visible="detailVisible"
      title="连接信息"
      :footer="false"
      width="400px"
    >
      <a-descriptions :column="1" bordered size="medium">
        <a-descriptions-item label="管理地址">
          {{ detailConfig.address }}
        </a-descriptions-item>
        <a-descriptions-item label="用户名">
          {{ detailConfig.username }}
        </a-descriptions-item>
        <a-descriptions-item label="密码">
          {{ detailConfig.password }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <!-- Enable Config Dialog -->
    <a-modal
      v-model:visible="dialogVisible"
      title="启用 iKuai 插件"
      :mask-closable="false"
      :closable="!testing"
      :footer="false"
      @close="handleDialogClose"
    >
      <a-form :model="form" layout="vertical">
        <a-alert type="info" style="margin-bottom: 16px">
          请输入 iKuai 路由器的连接信息，系统将校验连接后再启用插件。
        </a-alert>

        <a-form-item label="管理地址" field="address" required>
          <a-input v-model="form.address" placeholder="http://192.168.10.1" />
        </a-form-item>

        <a-form-item label="用户名" field="username" required>
          <a-input v-model="form.username" placeholder="admin" />
        </a-form-item>

        <a-form-item label="密码" field="password" required>
          <a-input-password v-model="form.password" placeholder="请输入密码" />
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="testing" @click="handleDialogSubmit">
              连接并启用
            </a-button>
            <a-button :disabled="testing" @click="dialogVisible = false">
              取消
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Polling Interval Settings -->
    <a-card title="数据轮询间隔" :bordered="true">
      <template #extra>
        <a-button type="primary" :loading="pollingLoading" @click="savePollingConfig">
          保存设置
        </a-button>
      </template>

      <a-alert type="info" style="margin-bottom: 16px">
        调整前端数据刷新频率。修改后需刷新页面生效。
      </a-alert>

      <a-form :model="pollingForm" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="实时数据间隔 (ms)">
              <a-input-number
                v-model="pollingForm.realtime"
                :min="500"
                :max="10000"
                :step="500"
                placeholder="2000"
              />
              <a-typography-text type="secondary" style="font-size: 12px; margin-top: 4px; display: block;">
                默认 2000ms (2秒)，用于实时速率等高频数据
              </a-typography-text>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="频繁数据间隔 (ms)">
              <a-input-number
                v-model="pollingForm.frequent"
                :min="1000"
                :max="60000"
                :step="5000"
                placeholder="30000"
              />
              <a-typography-text type="secondary" style="font-size: 12px; margin-top: 4px; display: block;">
                默认 30000ms (30秒)，用于系统状态等中频数据
              </a-typography-text>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="慢速数据间隔 (ms)">
              <a-input-number
                v-model="pollingForm.slow"
                :min="5000"
                :max="3600000"
                :step="60000"
                placeholder="300000"
              />
              <a-typography-text type="secondary" style="font-size: 12px; margin-top: 4px; display: block;">
                默认 300000ms (5分钟)，用于累计流量等低频数据
              </a-typography-text>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.config-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>
