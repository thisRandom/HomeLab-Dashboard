import request from './request'

export interface PluginRecord {
  pluginId: string
  displayName: string
  description: string
  icon: string
  version: string
  enabled: boolean
  status: string
}

export function listPlugins() {
  return request.get<PluginRecord[]>('/plugins')
}

export interface ConfigField {
  name: string
  label: string
  type: string
  required?: boolean
  defaultValue?: string
  placeholder?: string
}

export function getPluginSchema(pluginId: string) {
  return request.get<ConfigField[]>(`/plugins/${pluginId}/schema`)
}

export function getPluginConfig(pluginId: string) {
  return request.get<Record<string, string>>(`/plugins/${pluginId}/config`)
}

export interface ConfigSaveResult {
  success: boolean
  message: string
}

export function savePluginConfig(pluginId: string, config: Record<string, string>) {
  return request.put<ConfigSaveResult>(`/plugins/${pluginId}/config`, config)
}

export function togglePlugin(pluginId: string, enabled: boolean) {
  return request.post<ConfigSaveResult>(`/plugins/${pluginId}/enable`, { enabled })
}

export function testSaveEnable(pluginId: string, config: Record<string, string>) {
  return request.post<ConfigSaveResult>(`/plugins/${pluginId}/test-save-enable`, config)
}

export interface TestTemperatureResult {
  success: boolean
  message: string
  temperature?: string
}

export function testTemperature(pluginId: string, config: Record<string, string>) {
  return request.post<TestTemperatureResult>(`/plugins/${pluginId}/test-temperature`, config)
}
