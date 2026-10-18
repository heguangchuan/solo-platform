/* ========== ONVIF 设备 ========== */

export interface OnvifDevice {
  id: string
  /** 显示名（用户填） */
  name?: string
  /** 设备 IP */
  host: string
  /** ONVIF Web 服务端口，默认 80 */
  port: number
  /** 鉴权用户名 */
  username?: string
  /** 鉴权密码 */
  password?: string
  /** 0 离线 1 在线（由探测任务维护） */
  status?: number
  /** GetStreamUri 探测填充的原始 RTSP（不含账密） */
  streamUri?: string
  /** 最近一次探测时间 */
  lastCheckTime?: string
  createTime?: string
  updateTime?: string
}

export interface OnvifDevicePageRequest {
  page?: number
  pageSize?: number
  /** 显示名模糊匹配 */
  name?: string
  /** IP 模糊匹配 */
  host?: string
  /** 0 离线 1 在线 */
  status?: number
}

/** 设备表单：新增与编辑共用；保存后后端会同步探测一次 */
export interface OnvifDeviceRequest {
  id?: string
  name?: string
  host: string
  port: number
  username?: string
  password?: string
}

/* ========== ONVIF 实时点播 ========== */

export interface OnvifPlayResult {
  /** ZLM 流名（约定 onvif_<deviceId>），stop 时原样回传 */
  streamId: string
  /** ZLM 应用名，固定 rtsp */
  app: string
  mediaServerId?: string
  flvUrl?: string
  wsFlvUrl?: string
  hlsUrl?: string
  rtcUrl?: string
}
