/** ONVIF 模块相关类型 */
import type { PageRequest } from './common'

/** 设备列表分页查询参数 */
export interface OnvifDevicePageRequest extends PageRequest {
  name?: string
  host?: string
  status?: number
}

/** ONVIF 设备 */
export interface OnvifDevice {
  id: string
  name?: string
  host: string
  port?: number
  username?: string
  password?: string
  transport?: string
  /** 0 主码流 1 子码流 */
  streamType?: number
  manufacturer?: string
  model?: string
  firmware?: string
  serialNumber?: string
  profileToken?: string
  streamUri?: string
  /** 0 离线 1 在线 */
  status: number
  lastCheckTime?: string
  /** 1 系统新增 2 表格导入 */
  source?: number
  createTime: string
  updateTime: string
}

/** 点播开始参数 */
export interface OnvifPlayStartRequest {
  deviceId: string | number
  streamType?: number
}

/** 点播停止参数 */
export interface OnvifPlayStopRequest {
  streamId: string
}

/** 点播开始响应 */
export interface OnvifPlayResultVO {
  streamId: string
  app: string
  mediaServerId: string
  flvUrl?: string
  wsFlvUrl?: string
  hlsUrl?: string
  rtcUrl?: string
}
