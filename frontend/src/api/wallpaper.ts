import request from './request'

export interface Wallpaper {
  id: number
  filename: string
  originalName: string
  url: string
  fileSize: number
  sortOrder: number
  active: boolean
  createdAt: string
}

export interface UploadResult {
  success: boolean
  url?: string
  filename?: string
  message?: string
}

export function getWallpapers() {
  return request.get<Wallpaper[]>('/wallpapers')
}

export function uploadWallpaper(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<UploadResult>('/wallpapers/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteWallpaper(id: number) {
  return request.delete<{ success: boolean; message?: string }>(`/wallpapers/${id}`)
}

export function setActiveWallpaper(id: number) {
  return request.put<{ success: boolean; message?: string }>(`/wallpapers/${id}/active`)
}
