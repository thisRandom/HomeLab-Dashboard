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

export function getRealtime(template = 'network-overview') {
  return request.get<Record<string, CollectedMetrics>>('/metrics/realtime', { params: { template } })
}

export function getFrequent(template = 'network-overview') {
  return request.get<Record<string, CollectedMetrics>>('/metrics/frequent', { params: { template } })
}

export function getSlow(template = 'network-overview') {
  return request.get<Record<string, CollectedMetrics>>('/metrics/slow', { params: { template } })
}
