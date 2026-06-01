import request from './request'

export interface SystemConfig {
  id: number
  configKey: string
  configValue: string
  valueType: string
  updatedAt: string
}

export function getAllConfig() {
  return request.get<SystemConfig[]>('/config')
}

export function getConfigByKey(key: string) {
  return request.get<{ key: string; value: string }>(`/config/${key}`)
}

export function saveConfig(key: string, value: string, valueType: string = 'string') {
  return request.put<string>('/config', { key, value, valueType })
}

export function saveConfigBatch(configs: Array<{ key: string; value: string; valueType?: string }>) {
  return request.put<string>('/config/batch', configs)
}

export interface UploadResult {
  success: boolean
  url?: string
  filename?: string
  message?: string
}

export function uploadBackgroundImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<UploadResult>('/config/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
