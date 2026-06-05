<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { usePluginStore } from '../stores/plugin'
import { getAllConfig, saveConfigBatch } from '../api/config'
import { testSaveEnable, testTemperature, getPluginSchema } from '../api/plugin'
import type { ConfigField } from '../api/plugin'
import type { PluginRecord } from '../api/plugin'
import { Message } from '@arco-design/web-vue'

const store = usePluginStore()

// Main plugin dialog state
const dialogVisible = ref(false)
const testing = ref(false)
const activePlugin = ref<PluginRecord | null>(null)
const detailVisible = ref(false)
const detailConfig = ref<Record<string, string>>({})

// Dynamic form for plugin config
const form = reactive<Record<string, string>>({})

// SSH dialog state
const sshVisible = ref(false)
const sshTesting = ref(false)
const tempTesting = ref(false)
const tempResult = ref('')
const sshForm = reactive<Record<string, string>>({
  ssh_host: '192.168.10.254',
  ssh_port: '22',
  ssh_user: 'root',
  ssh_password: '',
  ssh_key_path: '',
})

// Plugin config schema (fetched from backend)
const pluginSchema = ref<ConfigField[]>([])

// Polling interval settings
const pollingForm = reactive({
  realtime: 2000,
  frequent: 30000,
  slow: 300000,
})
const pollingLoading = ref(false)

onMounted(async () => {
  await store.fetchPlugins()
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

async function handleToggle(plugin: PluginRecord, val: string | number | boolean) {
  const newVal = !!val
  if (newVal) {
    activePlugin.value = plugin
    // Fetch schema from backend
    try {
      const { data } = await getPluginSchema(plugin.pluginId)
      pluginSchema.value = data || []
    } catch {
      pluginSchema.value = []
    }
    // Initialize form with defaults
    for (const field of pluginSchema.value) {
      form[field.name] = field.defaultValue || ''
    }
    dialogVisible.value = true
  } else {
    doDisable(plugin)
  }
}

async function doDisable(plugin: PluginRecord) {
  try {
    const result = await store.togglePluginEnabled(plugin.pluginId, false)
    if (result?.success) {
      Message.success('插件已禁用')
    } else {
      Message.error(result?.message || '操作失败')
    }
  } catch {
    Message.error('请求失败')
  }
}

async function handleDialogSubmit() {
  if (!activePlugin.value) return

  for (const field of pluginSchema.value) {
    if (field.required === false) continue
    if (!form[field.name]) {
      Message.warning(`请填写${field.label}`)
      return
    }
  }

  testing.value = true
  try {
    const result = await store.testSaveEnablePlugin(activePlugin.value.pluginId, { ...form })
    if (result?.success) {
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
  if (activePlugin.value) {
    const plugin = store.plugins.find(p => p.pluginId === activePlugin.value!.pluginId)
    if (plugin && !plugin.enabled) {
      // Revert switch state
    }
  }
  activePlugin.value = null
}

async function showDetail(plugin: PluginRecord) {
  activePlugin.value = plugin
  const config = await store.fetchPluginConfig(plugin.pluginId)
  detailConfig.value = config || {}
  detailVisible.value = true
}

// ===== SSH Config =====

async function openSshDialog(plugin: PluginRecord) {
  activePlugin.value = plugin
  // Load existing SSH config from DB
  const config = await store.fetchPluginConfig(plugin.pluginId)
  sshForm.ssh_host = config?.ssh_host || ''
  sshForm.ssh_port = config?.ssh_port || '22'
  sshForm.ssh_user = config?.ssh_user || 'root'
  sshForm.ssh_password = config?.ssh_password || ''
  sshForm.ssh_key_path = config?.ssh_key_path || ''
  sshVisible.value = true
}

async function testSsh() {
  if (!activePlugin.value) return

  // Build full config: merge API config (from DB) + SSH config (from form)
  const pluginConfig = await store.fetchPluginConfig(activePlugin.value.pluginId)
  const fullConfig = { ...pluginConfig, ...sshForm }

  sshTesting.value = true
  try {
    const { data } = await testSaveEnable(activePlugin.value.pluginId, fullConfig)
    if (data.success) {
      Message.success('SSH 连接成功，配置已保存')
      sshVisible.value = false
    } else {
      Message.error(data.message || 'SSH 连接失败')
    }
  } catch {
    Message.error('SSH 连接失败')
  } finally {
    sshTesting.value = false
  }
}

async function testReadTemp() {
  if (!activePlugin.value) return

  const pluginConfig = await store.fetchPluginConfig(activePlugin.value.pluginId)
  const fullConfig = { ...pluginConfig, ...sshForm }

  tempTesting.value = true
  tempResult.value = ''
  try {
    const { data } = await testTemperature(activePlugin.value.pluginId, fullConfig)
    if (data.success) {
      tempResult.value = data.temperature || ''
      Message.success('温度读取成功')
    } else {
      Message.error(data.message || '温度读取失败')
    }
  } catch {
    Message.error('温度读取失败')
  } finally {
    tempTesting.value = false
  }
}
</script>

<template>
  <div class="config-view">
    <a-typography-title :level="3">配置中心</a-typography-title>

    <!-- Dynamic plugin cards -->
    <a-card
      v-for="plugin in store.plugins"
      :key="plugin.pluginId"
      :title="plugin.displayName"
      :bordered="true"
    >
      <template #extra>
        <a-switch
          :model-value="plugin.enabled"
          :loading="store.loading"
          checked-text="启用"
          unchecked-text="禁用"
          @change="(val: any) => handleToggle(plugin, val)"
        />
      </template>

      <a-typography-paragraph type="secondary">
        {{ plugin.description || (plugin.enabled ? '插件已启用，正在采集数据' : '插件未启用，请开启后配置连接信息') }}
      </a-typography-paragraph>

      <a-space>
        <a-button v-if="plugin.enabled" type="outline" size="small" @click="showDetail(plugin)">
          查看连接信息
        </a-button>
        <a-button v-if="plugin.enabled && plugin.pluginId === 'pve'" type="outline" size="small" @click="openSshDialog(plugin)">
          SSH 配置
        </a-button>
      </a-space>
    </a-card>

    <!-- Detail Dialog (read-only) -->
    <a-modal
      v-model:visible="detailVisible"
      title="连接信息"
      :footer="false"
      width="400px"
    >
      <a-descriptions :column="1" bordered size="medium">
        <template v-for="(value, key) in detailConfig" :key="key">
          <a-descriptions-item :label="String(key)">
            {{ value }}
          </a-descriptions-item>
        </template>
      </a-descriptions>
    </a-modal>

    <!-- Enable Config Dialog (dynamic form) -->
    <a-modal
      v-model:visible="dialogVisible"
      :title="activePlugin ? `启用 ${activePlugin.displayName}` : '启用插件'"
      :mask-closable="false"
      :closable="!testing"
      :footer="false"
      @close="handleDialogClose"
    >
      <a-form v-if="activePlugin" :model="form" layout="vertical">
        <a-alert type="info" style="margin-bottom: 16px">
          请输入 {{ activePlugin.displayName }} 的连接信息，系统将校验连接后再启用插件。
        </a-alert>

        <template v-for="field in pluginSchema" :key="field.name">
          <a-form-item :label="field.label" :field="field.name" :required="field.required !== false">
            <a-input-password
              v-if="field.type === 'password'"
              v-model="form[field.name]"
              :placeholder="field.placeholder"
            />
            <a-input
              v-else
              v-model="form[field.name]"
              :placeholder="field.placeholder"
            />
          </a-form-item>
        </template>

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

    <!-- SSH Config Dialog -->
    <a-modal
      v-model:visible="sshVisible"
      title="SSH 配置"
      :mask-closable="false"
      :closable="!sshTesting"
      :footer="false"
    >
      <a-alert type="info" style="margin-bottom: 16px">
        配置 SSH 连接以读取 PVE 的 CPU 温度数据。
      </a-alert>

      <a-alert type="warning" style="margin-bottom: 16px;">
        <template #title>前置条件：安装 lm-sensors</template>
        <div style="font-size:12px;line-height:1.8;">
          <p style="margin:0;">SSH 登录 PVE 后执行以下命令：</p>
          <div style="background:rgba(0,0,0,0.04);border-radius:4px;padding:8px 10px;margin:6px 0;font-family:'SF Mono','Cascadia Code','Menlo',monospace;font-size:11px;">
            apt update && apt install -y lm-sensors<br>
            sensors-detect --auto
          </div>
          <p style="margin:4px 0 0;">安装完成后执行 <code>sensors</code> 能看到温度数据即可。</p>
        </div>
      </a-alert>

      <a-form :model="sshForm" layout="vertical">
        <a-form-item label="SSH 地址">
          <a-input v-model="sshForm.ssh_host" placeholder="与 PVE 地址相同即可" />
        </a-form-item>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="SSH 端口">
              <a-input v-model="sshForm.ssh_port" placeholder="22" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="SSH 用户名">
              <a-input v-model="sshForm.ssh_user" placeholder="root" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="SSH 密码（与密钥二选一）">
          <a-input-password v-model="sshForm.ssh_password" placeholder="输入 SSH 密码" />
        </a-form-item>

        <a-form-item label="SSH 私钥路径（与密码二选一）">
          <a-input v-model="sshForm.ssh_key_path" placeholder="如 C:/Users/xxx/.ssh/id_rsa" />
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="sshTesting" @click="testSsh">
              测试连接并保存
            </a-button>
            <a-button :loading="tempTesting" @click="testReadTemp">
              测试读取温度
            </a-button>
            <a-button :disabled="sshTesting || tempTesting" @click="sshVisible = false">
              取消
            </a-button>
          </a-space>
          <div v-if="tempResult" style="margin-top:8px;padding:8px 12px;background:rgba(74,222,128,0.1);border:1px solid rgba(74,222,128,0.3);border-radius:6px;font-size:12px;color:#4ADE80;">
            {{ tempResult }}
          </div>
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
