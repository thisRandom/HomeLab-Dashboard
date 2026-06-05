import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listPlugins, getPluginConfig, savePluginConfig, togglePlugin, testSaveEnable } from '../api/plugin'
import type { PluginRecord } from '../api/plugin'

export const usePluginStore = defineStore('plugin', () => {
  const plugins = ref<PluginRecord[]>([])
  const ikuaiConfig = ref<Record<string, string>>({})
  const pluginConfigs = ref<Record<string, Record<string, string>>>({})
  const loading = ref(false)

  async function fetchPlugins() {
    const { data } = await listPlugins()
    plugins.value = data
  }

  // ===== Generic plugin methods =====

  async function fetchPluginConfig(pluginId: string) {
    const { data } = await getPluginConfig(pluginId)
    pluginConfigs.value[pluginId] = data
    return data
  }

  async function togglePluginEnabled(pluginId: string, enabled: boolean) {
    loading.value = true
    try {
      const { data } = await togglePlugin(pluginId, enabled)
      if (data.success) {
        const plugin = plugins.value.find(p => p.pluginId === pluginId)
        if (plugin) plugin.enabled = enabled
      }
      return data
    } finally {
      loading.value = false
    }
  }

  async function testSaveEnablePlugin(pluginId: string, config: Record<string, string>) {
    loading.value = true
    try {
      const { data } = await testSaveEnable(pluginId, config)
      if (data.success) {
        pluginConfigs.value[pluginId] = { ...config }
        const plugin = plugins.value.find(p => p.pluginId === pluginId)
        if (plugin) plugin.enabled = true
      }
      return data
    } finally {
      loading.value = false
    }
  }

  // ===== Legacy iKuai methods (kept for backward compatibility) =====

  async function fetchIkuaiConfig() {
    const { data } = await getPluginConfig('ikuai')
    ikuaiConfig.value = data
    pluginConfigs.value['ikuai'] = data
  }

  async function saveIkuaiConfig(config: Record<string, string>) {
    loading.value = true
    try {
      const { data } = await savePluginConfig('ikuai', config)
      if (data.success) {
        ikuaiConfig.value = { ...config }
        pluginConfigs.value['ikuai'] = { ...config }
      }
      return data
    } finally {
      loading.value = false
    }
  }

  async function toggleIkuai(enabled: boolean) {
    return togglePluginEnabled('ikuai', enabled)
  }

  async function testSaveEnableIkuai(config: Record<string, string>) {
    return testSaveEnablePlugin('ikuai', config)
  }

  return {
    plugins, ikuaiConfig, pluginConfigs, loading,
    fetchPlugins, fetchPluginConfig, togglePluginEnabled, testSaveEnablePlugin,
    fetchIkuaiConfig, saveIkuaiConfig, toggleIkuai, testSaveEnableIkuai,
  }
})
