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
  streamUri?: string
  /** 0 离线 1 在线 */
  status: number
  lastCheckTime?: string
  createTime: string
  updateTime: string
}

/** 点播开始参数 */
export interface OnvifPlayStartRequest {
  deviceId: string | number
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

/**
 * ONVIF 实时点播会话（Redis 主存，纯运行时状态）
 * <p>对应后端 {@code com.solo.onvif.entity.OnvifSession}，节点流统计弹窗"ONVIF 会话"
 * Tab 用这个类型。streamId 形如 {@code onvif_<deviceId>}。</p>
 */
export interface OnvifSession {
  streamId: string
  serverId: string
  deviceId: number
  proxyKey?: string
  startTime?: string
}
