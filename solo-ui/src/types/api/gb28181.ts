/** GB/T 28181 国标模块相关类型 */
import type { PageRequest } from './common'

/** 设备列表分页查询参数 */
export interface GbDevicePageRequest extends PageRequest {
  deviceId?: string
  name?: string
  status?: number
}

/** 国标设备 */
export interface GbDevice {
  id: string
  deviceId: string
  name?: string
  manufacturer?: string
  model?: string
  firmware?: string
  sipDomain?: string
  ip?: string
  port?: number
  localIp?: string
  hostAddress?: string
  transport?: string
  expires?: number
  charset?: string
  status: number
  channelCount?: number
  registerTime?: string
  keepaliveTime?: string
  lastCatalogSyncTime?: string
  createTime: string
  updateTime: string
}

/** 通道列表分页查询参数 */
export interface GbDeviceChannelPageRequest extends PageRequest {
  deviceId?: string
  channelId?: string
  name?: string
  status?: string
  parentId?: string
}

/** 国标设备通道 */
export interface GbDeviceChannel {
  id: string
  deviceDbId: string
  deviceId: string
  channelId: string
  name?: string
  manufacturer?: string
  model?: string
  owner?: string
  civilCode?: string
  block?: string
  address?: string
  parental?: number
  parentId?: string
  status?: string
  longitude?: number
  latitude?: number
  ptzType?: number
  streamId?: string
  createTime: string
  updateTime: string
}
