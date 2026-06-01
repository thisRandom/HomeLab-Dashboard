import request from './request'

export interface MetricValue {
  key: string
  value: number | string
  unit: string
  displayName: string
  type: 'GAUGE' | 'COUNTER' | 'INFO'
}

export interface CollectedMetrics {
  pluginId: string
  collectedAt: string
  metrics: Record<string, MetricValue>
}

export function getRealtime() {
  return request.get<Record<string, CollectedMetrics>>('/metrics/realtime')
}

export function getFrequent() {
  return request.get<Record<string, CollectedMetrics>>('/metrics/frequent')
}

export function getSlow() {
  return request.get<Record<string, CollectedMetrics>>('/metrics/slow')
}
