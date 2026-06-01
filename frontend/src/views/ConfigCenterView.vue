<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { usePluginStore } from '../stores/plugin'
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

onMounted(async () => {
  await store.fetchPlugins()
  const plugin = store.plugins.find(p => p.pluginId === 'ikuai')
  if (plugin) enabled.value = plugin.enabled

  // Load existing config for dialog default values
  await store.fetchIkuaiConfig()
  form.address = store.ikuaiConfig.address || ''
  form.username = store.ikuaiConfig.username || ''
  form.password = store.ikuaiConfig.password || ''
})

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
  </div>
</template>

<style scoped>
.config-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>
