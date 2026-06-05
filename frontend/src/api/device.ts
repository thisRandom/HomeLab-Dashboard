import request from './request'

export interface DeviceDetail {
  hostname: string
  ip: string
  mac: string
  upload: number
  download: number
  totalUp: number
  totalDown: number
  clientType: string
  signal: string
  ssid: string
}

export function getDevices() {
  return request.get<DeviceDetail[]>('/devices')
}
