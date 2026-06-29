import type {
  GbDevice,
  GbDevicePageRequest,
  GbDeviceChannel,
  GbDeviceChannelPageRequest,
  PageResult,
} from '@/types/api'
import { get, post, del } from '@/utils/request'

/**
 * 设备分页
 */
export function getGbDevicePageApi(data: GbDevicePageRequest) {
  return post<PageResult<GbDevice>>('/api/gb28181/device/page', data)
}

/**
 * 设备详情
 */
export function getGbDeviceApi(deviceId: string) {
  return get<GbDevice>(`/api/gb28181/device/${deviceId}`)
}

/**
 * 触发设备信息 + 通道目录同步
 */
export function syncGbDeviceApi(deviceId: string) {
  return post<void>(`/api/gb28181/device/${deviceId}/sync`)
}

/**
 * 删除设备记录
 */
export function deleteGbDeviceApi(deviceId: string) {
  return del<void>(`/api/gb28181/device/${deviceId}`)
}

/**
 * 通道分页
 */
export function getGbDeviceChannelPageApi(data: GbDeviceChannelPageRequest) {
  return post<PageResult<GbDeviceChannel>>('/api/gb28181/device-channel/page', data)
}

/**
 * 按设备查通道
 */
export function listChannelsByDeviceApi(deviceDbId: string) {
  return get<GbDeviceChannel[]>(`/api/gb28181/device-channel/by-device/${deviceDbId}`)
}
