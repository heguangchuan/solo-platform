import { get, post, del } from '@/utils/request'
import type {
  OnvifDevice,
  OnvifDevicePageRequest,
  OnvifDeviceRequest,
  OnvifPlayResult,
} from '@/types/api'

/* ==================== ONVIF 设备 ==================== */

export const getOnvifDevicePageApi = (data: OnvifDevicePageRequest) => {
  return post<{ total: number; rows: OnvifDevice[] }>('/api/onvif/device/page', data)
}

export const getOnvifDeviceDetailApi = (id: string) => {
  return get<OnvifDevice>(`/api/onvif/device/${id}`)
}

/** 保存后后端会同步探测一次（TCP + SOAP），接口耗时可能数秒 */
export const saveOnvifDeviceApi = (data: OnvifDeviceRequest) => {
  return post<void>('/api/onvif/device/save', data)
}

export const updateOnvifDeviceApi = (data: OnvifDeviceRequest) => {
  return post<void>('/api/onvif/device/update', data)
}

/** 主动探测一次（同步执行，耗时可能数秒） */
export const probeOnvifDeviceApi = (id: string) => {
  return post<void>(`/api/onvif/device/${id}/probe`)
}

export const deleteOnvifDeviceApi = (id: string) => {
  return del<void>(`/api/onvif/device/${id}`)
}

/* ==================== ONVIF 实时点播 ==================== */

export const startOnvifPlayApi = (deviceId: string) => {
  return post<OnvifPlayResult>('/api/onvif/play/start', { deviceId })
}

/** 幂等 —— streamId 不存在或会话已清理时同样返回成功 */
export const stopOnvifPlayApi = (streamId: string) => {
  return post<void>('/api/onvif/play/stop', { streamId })
}
