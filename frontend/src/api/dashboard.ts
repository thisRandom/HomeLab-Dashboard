import request from './request'

export interface DashboardCard {
  id: number
  activeTemplate: string
  cardType: string
  title: string
  positionX: number
  positionY: number
  enabled: boolean
  sortOrder: number
}

export function getCards(template = 'network-overview') {
  return request.get<DashboardCard[]>('/dashboard/cards', { params: { template } })
}

export function saveCards(template: string, cards: { type: string; title: string; x: number; y: number }[]) {
  return request.post('/dashboard/cards', { template, cards })
}

export function setEditing(template: string, active: boolean) {
  return request.put('/dashboard/editing', null, { params: { template, active } })
}
