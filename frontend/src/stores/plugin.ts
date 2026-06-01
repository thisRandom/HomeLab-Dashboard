import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listPlugins, getPluginConfig, savePluginConfig, togglePlugin, testSaveEnable } from '../api/plugin'
import type { PluginRecord } from '../api/plugin'

export const usePluginStore = defineStore('plugin', () => {
  const plugins = ref<PluginRecord[]>([])
  const ikuaiConfig = ref<Record<string, string>>({})
  const loading = ref(false)

  async function fetchPlugins() {
    const { data } = await listPlugins()
    plugins.value = data
  }

  async function fetchIkuaiConfig() {
    const { data } = await getPluginConfig('ikuai')
    ikuaiConfig.value = data
  }

  async function saveIkuaiConfig(config: Record<string, string>) {
    loading.value = true
    try {
      const { data } = await savePluginConfig('ikuai', config)
      if (data.success) {
        ikuaiConfig.value = { ...config }
      }
      return data
    } finally {
      loading.value = false
    }
  }

  async function toggleIkuai(enabled: boolean) {
    loading.value = true
    try {
      const { data } = await togglePlugin('ikuai', enabled)
      if (data.success) {
        const plugin = plugins.value.find(p => p.pluginId === 'ikuai')
        if (plugin) plugin.enabled = enabled
      }
      return data
    } finally {
      loading.value = false
    }
  }

  async function testSaveEnableIkuai(config: Record<string, string>) {
    loading.value = true
    try {
      const { data } = await testSaveEnable('ikuai', config)
      if (data.success) {
        ikuaiConfig.value = { ...config }
        const plugin = plugins.value.find(p => p.pluginId === 'ikuai')
        if (plugin) plugin.enabled = true
      }
      return data
    } finally {
      loading.value = false
    }
  }

  return { plugins, ikuaiConfig, loading, fetchPlugins, fetchIkuaiConfig, saveIkuaiConfig, toggleIkuai, testSaveEnableIkuai }
})
