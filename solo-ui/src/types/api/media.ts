/* ========== 流媒体节点（ZLMediaKit） ========== */

export interface MediaServer {
  id: string
  /** ZLM 节点唯一标识（与 config.ini 中 [general].mediaServerId 一致） */
  serverId: string
  /** 节点显示名 */
  name?: string
  /** ZLM 管理面 IP，用于 REST API */
  hostIp: string
  /** ZLM HTTP API 端口 */
  httpPort: number
  /** ZLM API secret */
  secret?: string
  /** 对外播放 IP，留空同 hostIp */
  streamIp?: string
  /** 对外播放 HTTP 端口，0 = 同 httpPort */
  streamHttpPort?: number
  /** RTSP 端口，0 = 不生成 RTSP URL */
  rtspPort?: number
  /** WebRTC 端口，0 = 不生成 RTC URL */
  rtcPort?: number
  rtpPortStart?: number
  rtpPortEnd?: number
  /** 0 离线，1 在线（由 ZLM hook 心跳驱动） */
  status: number
  hookAliveTime?: string
  createTime?: string
  updateTime?: string
}

export interface MediaServerPageRequest {
  page?: number
  pageSize?: number
  serverId?: string
  status?: number
}

/** 节点表单：新增与编辑共用，按 serverId 唯一 */
export interface MediaServerRequest {
  id?: string
  serverId: string
  name?: string
  hostIp: string
  httpPort: number
  secret?: string
  streamIp?: string
  rtpPortStart: number
  rtpPortEnd: number
}

/* ========== 拉流代理 ========== */

export interface StreamProxy {
  id: string
  app: string
  stream: string
  srcUrl: string
  mediaServerId?: string
  /** 0 TCP+UDP 自适应 1 TCP 2 UDP 3 组播 */
  rtpType?: number
  proxyKey?: string
  /** 0 未启动 1 已启动 */
  status?: number
  lastError?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface StreamProxyPageRequest {
  page?: number
  pageSize?: number
  stream?: string
  /** 0 已停止 1 运行中 */
  status?: number
}

export interface StreamProxyRequest {
  id?: string
  app: string
  stream: string
  srcUrl: string
  mediaServerId?: string
  rtpType?: number
  remark?: string
}

export interface StreamPlayUrl {
  app: string
  stream: string
  mediaServerId?: string
  flvUrl?: string
  wsFlvUrl?: string
  hlsUrl?: string
  rtcUrl?: string
}

/* ========== 推流（只读，由 ZLM on_stream_changed hook 驱动） ========== */

export interface StreamPush {
  id: string
  app: string
  stream: string
  mediaServerId: string
  vhost?: string
  streamSchema?: string
  /** 推流类型：rtmp / rtsp / rtc */
  originType?: string
  aliveSecond?: number
  pushTime?: string
  clientId?: string
  createTime?: string
}

export interface StreamPushPageRequest {
  page?: number
  pageSize?: number
  app?: string
  stream?: string
  mediaServerId?: string
}

/* ========== 推流方鉴权 ========== */

export interface StreamPushClient {
  id: string
  clientId: string
  token: string
  /** 0 禁用 1 启用 */
  enabled?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface StreamPushClientPageRequest {
  page?: number
  pageSize?: number
}

export interface StreamPushClientRequest {
  id?: string
  clientId: string
  enabled: number
  remark?: string
}

/* ========== 节点流统计 ========== */

export interface MediaServerStreamStatsSummary {
  serverId: string
  serverName?: string
  hostIp: string
  httpPort: number
  status: number
  gbStreamCount: number
  pushStreamCount: number
  proxyStreamCount: number
  onvifStreamCount: number
  total: number
}

export interface StreamStatsGbItem {
  streamId: string
  deviceId: string
  channelId: string
  sessionType?: string
  status?: string
  startTime?: string
}

export interface StreamStatsPushItem {
  app: string
  stream: string
  originType?: string
  aliveSecond?: number
  pushTime?: string
}

export interface StreamStatsProxyItem {
  app: string
  stream: string
  srcUrl: string
  status?: number
}

export interface StreamStatsOnvifItem {
  streamId: string
  deviceId?: number
  proxyKey?: string
  startTime?: string
}

export type StreamStatsTabKey = 'gb' | 'push' | 'proxy' | 'onvif'
