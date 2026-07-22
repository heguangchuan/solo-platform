import type {
  OnvifDevice,
  OnvifDevicePageRequest,
  OnvifPlayResultVO,
  OnvifPlayStartRequest,
  OnvifPlayStopRequest,
  PageResult,
} from '@/types/api'
import { del, get, post } from '@/utils/request'

/** 设备分页 */
export function getOnvifDevicePageApi(data: OnvifDevicePageRequest) {
  return post<PageResult<OnvifDevice>>('/api/onvif/device/page', data)
}

/** 设备详情 */
export function getOnvifDeviceApi(id: string | number) {
  return get<OnvifDevice>(`/api/onvif/device/${id}`)
}

/** 新增设备 */
export function saveOnvifDeviceApi(data: Partial<OnvifDevice>) {
  return post<void>('/api/onvif/device/save', data)
}

/** 编辑设备 */
export function updateOnvifDeviceApi(data: Partial<OnvifDevice>) {
  return post<void>('/api/onvif/device/update', data)
}

/** 手动探测 */
export function probeOnvifDeviceApi(id: string | number) {
  return post<void>(`/api/onvif/device/${id}/probe`)
}

/** 删除设备 */
export function deleteOnvifDeviceApi(id: string | number) {
  return del<void>(`/api/onvif/device/${id}`)
}

/** 开始点播 */
export function startOnvifPlayApi(data: OnvifPlayStartRequest) {
  return post<OnvifPlayResultVO>('/api/onvif/play/start', data)
}

/** 停止点播 */
export function stopOnvifPlayApi(data: OnvifPlayStopRequest) {
  return post<void>('/api/onvif/play/stop', data)
}
