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

/** 流媒体节点（ZLMediaKit）分页查询参数 */
export interface GbMediaServerPageRequest extends PageRequest {
  serverId?: string
  status?: number
}

/** 流媒体节点（ZLMediaKit） */
export interface GbMediaServer {
  id: string
  /** ZLM 节点唯一标识（与 config.ini 中 [general].mediaServerId 一致） */
  serverId: string
  name?: string
  /** ZLM 管理面 IP，用于 REST API 调用 */
  hostIp: string
  /** ZLM HTTP API 端口 */
  httpPort: number
  /** ZLM API secret */
  secret?: string
  /** 对外播放 IP（HTTP-FLV / HLS / WebRTC），留空则同 hostIp */
  streamIp?: string
  /** 对外 HTTP/WS-FLV/HLS 端口（0=同 httpPort） */
  streamHttpPort?: number
  /** 对外 RTSP 端口（0=不生成 RTSP URL） */
  rtspPort?: number
  /** 对外 WebRTC 端口（0=不生成 RTC URL） */
  rtcPort?: number
  rtpPortStart?: number
  rtpPortEnd?: number
  /** 在线状态：0 离线，1 在线 */
  status: number
  hookAliveTime?: string
  createTime: string
  updateTime: string
}

/** 开始点播请求 */
export interface PlayStartRequest {
  deviceId: string
  channelId: string
  /** 收流模式，默认 UDP */
  transport?: 'UDP' | 'TCP-passive'
}

/** 停止点播请求 */
export interface PlayStopRequest {
  streamId: string
}

/** 点播结果（前端按 player 能力挑一个 URL 用；截图由 jessibuca 自带，不走后端） */
export interface PlayResultVO {
  streamId: string
  app: string
  mediaServerId: string
  ssrc: string
  rtpPort: number
  transport: string
  flvUrl?: string
  wsFlvUrl?: string
  hlsUrl?: string
  rtcUrl?: string
}
