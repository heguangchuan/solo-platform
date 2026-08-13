/** GB/T 28181 国标模块相关类型 */
import type { PageRequest } from './common'

/** Catalog 同步进度（来自 GET /sync-status） */
export interface GbSyncStatus {
  deviceId: string
  /** IDLE / RUNNING / SUCCESS / FAILED */
  status: 'IDLE' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  /** 已收到通道 / 目录项累计 */
  received: number
  /** 设备通告的 SumNum，0=设备空目录 */
  total: number
  /** 0-100 */
  percent: number
  /** 毫秒时间戳 */
  startedAtMs: number
  lastUpdateAtMs: number
  /** yyyy-MM-dd HH:mm:ss，status != RUNNING 时存在 */
  finishedAt?: string
  /** partial / 失败原因 */
  errorMsg?: string
  duplicateCount: number
}

/** /sync 接口返回（同步触发响应） */
export interface GbSyncTriggerResponse {
  /** TRIGGERED：本次抢到锁；ALREADY_RUNNING：锁失败，已有同步在进行 */
  result: 'TRIGGERED' | 'ALREADY_RUNNING'
  deviceId: string
  /** 当前 status JSON 字符串（结构同 GET /sync-status），前端可解析 */
  status: string
}

/** 设备列表分页查询参数 */
export interface GbDevicePageRequest extends PageRequest {
  deviceId?: string
  name?: string
  status?: number
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
}

/** 国标设备 */
export interface GbDevice {
  id: string
  deviceId: string
  /** 设备上报原始名称（DeviceInfo 响应） */
  reportName?: string
  /** 用户自定义显示名称（展示优先级高于 reportName） */
  name?: string
  manufacturer?: string
  model?: string
  firmware?: string
  sipDomain?: string
  ip?: string
  /** 收流IP覆盖；留空使用注册源IP，双栈内网互通时填设备内网IP */
  recvIp?: string
  port?: number
  localIp?: string
  hostAddress?: string
  transport?: string
  expires?: number
  charset?: string
  status: number
  registerTime?: string
  keepaliveTime?: string
  lastCatalogSyncTime?: string
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
  createTime: string
  updateTime: string
}

/** 设备存活探测结果（异步：仅汇报已发起/跳过，结果需刷新列表查看） */
export interface GbDeviceDetectResult {
  /** 已发起探测台数（状态异步更新） */
  detecting: number
  /** 跳过台数（无网络地址，无法探测） */
  skipped: number
  /** 每台设备处理明细 */
  items: Array<{ id: string; deviceId: string; result: 'detecting' | 'skipped' }>
}

/** 国标接入禁用名单（黑名单）：设备与下级平台默认自由注册，仅本表登记的 ID 被拒 */
export interface GbPlatform {
  id: string
  /** 被禁用的国标 ID（20 位，设备或下级平台通用） */
  platformId: string
  /** 备注名（可空；仅便于运维辨识，不参与业务判定） */
  name?: string
  /** 禁用开关：0 禁用（拒绝接入，默认）1 临时放行（保留记录但不拦截） */
  enabled: number
  /** 关联设备状态：0 离线 1 在线 2 未注册（由 service 关联回填） */
  status?: number
  /** 关联设备最后心跳时间 */
  keepaliveTime?: string
  createTime: string
  updateTime: string
}

/** 接入禁用名单分页查询参数 */
export interface GbPlatformPageRequest {
  page: number
  pageSize: number
  platformId?: string
  name?: string
  enabled?: number
}

/** 上级平台配置 */
export interface GbParentPlatform {
  id: string
  /** 上级平台名称 */
  name: string
  /** 上级平台国标编码 */
  serverGbId: string
  /** 上级平台国标域 */
  serverGbDomain: string
  /** 上级平台 SIP IP */
  serverIp: string
  /** 上级平台 SIP 端口 */
  serverPort: number
  /** 收流IP覆盖；留空使用SDP解析地址，双栈内网互通时填上级内网IP */
  recvIp?: string
  /** 本平台作为下级的国标编号 */
  deviceGbId: string
  /** 本平台 SIP IP（留空取配置默认值） */
  deviceIp?: string
  /** 本平台 SIP 端口 */
  devicePort?: number
  /** SIP 认证用户名 */
  username?: string
  /** SIP 认证密码 */
  password?: string
  /** 注册有效期（秒） */
  expires: number
  /** 心跳周期（秒） */
  keepTimeout: number
  /** 传输协议 UDP/TCP */
  transport: string
  /** 字符集 */
  characterSet: string
  /** 启用状态：0禁用 1启用 */
  enabled: number
  /** 在线状态：0离线 1在线（后续注册成功回填） */
  status?: number
  createTime?: string
  updateTime?: string
}

/** 上级平台分页查询参数 */
export interface GbParentPlatformPageRequest {
  page: number
  pageSize: number
  name?: string
  serverGbId?: string
  enabled?: number
}

/** 共享目录树节点（通道推送弹窗用）：设备作枝干，分组/通道作叶子 */
export interface GbShareTreeNode {
  /** 节点唯一键：设备 d_xxx（不可勾选）/ 分组 g_xxx / 通道 c_xxx */
  id: string
  /** 节点显示标签 */
  label: string
  /** 节点类型：DEVICE 设备（枝干）/ GROUP 分组（parental=1）/ CHANNEL 通道（parental=0） */
  type: 'DEVICE' | 'GROUP' | 'CHANNEL'
  children?: GbShareTreeNode[]
}

/** 上级平台已共享的节点 id 集合 */
export interface GbPlatformSharedIds {
  /**
   * 已共享的 catalog item 节点 id（g_ 前缀=分组 parental=1 / c_ 前缀=通道 parental=0），
   * 前端按前缀渲染勾选态
   */
  channelIds: string[]
}

/** 通道列表分页查询参数 */
export interface GbDeviceChannelPageRequest extends PageRequest {
  deviceId?: string
  channelId?: string
  name?: string
  status?: string
  /** 父分组 GB 码前缀（后端 SQL LIKE 'value%' 匹配） */
  parentId?: string
  /** 资源类型过滤：0=通道 / 1=分组；不传=全部（通道 + 分组） */
  parental?: number
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
}

/** 国标资源目录项（通道 + 分组合一，ADR-023） */
export interface GbDeviceChannel {
  id: string
  /** 所属设备国标ID（逻辑关联 gb_device.device_id）；通道与分组均必填——分组标识推送源设备 */
  deviceId?: string
  /** catalog item 20 位国标码：分组=215业务分组/216虚拟组织；通道=131摄像机/132IPC */
  channelId: string
  /** catalog item 上报原始名称（仅通道） */
  reportName?: string
  /** 用户自定义显示名称（展示优先级高于 reportName） */
  name?: string
  manufacturer?: string
  model?: string
  owner?: string
  civilCode?: string
  block?: string
  address?: string
  /** 0=通道（叶子）/ 1=分组（容器） */
  parental?: number
  status?: string
  longitude?: number
  latitude?: number
  ptzType?: number
  /** 父 catalog item 20 位国标码（指向同表 channel_id）；"0"=根 */
  parentId?: string
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
  createTime?: string
  updateTime?: string
}

/** 通道列表分页响应：透传 entity 全部字段（不回填冗余字段） */
export interface GbDeviceChannelVO extends GbDeviceChannel {
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

/** 云台预置位 */
export interface GbPtzPreset {
  id: string
  /** 通道数据库主键 */
  channelDbId: string
  /** 预置位编号（设备侧编号，从 1 开始） */
  presetId: number
  /** 预置位名称（如"大门入口"） */
  name: string
  createTime: string
  updateTime: string
}

/** 开始点播请求 */
export interface PlayStartRequest {
  deviceId: string
  channelId: string
  /** 收流模式，默认 UDP。TCP-active 前端已可选，后端暂未实现 */
  transport?: 'UDP' | 'TCP-passive' | 'TCP-active'
  /** 码流 token：空 / 不传主码流，非空子码流（如 '1' / 'stream:sub'） */
  streamNumber?: string
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
  /** 码流 token：空主码流，非空子码流 */
  streamNumber?: string
  flvUrl?: string
  wsFlvUrl?: string
  hlsUrl?: string
  rtcUrl?: string
}

/** 录像查询请求 */
export interface RecordInfoQueryRequest {
  deviceId: string
  channelId: string
  /** yyyy-MM-dd HH:mm:ss */
  startTime: string
  endTime: string
}

/** 单条录像段 */
export interface RecordItemResponse {
  channelId: string
  name?: string
  filePath?: string
  address?: string
  /** ISO-8601 起始 */
  startTime?: string
  endTime?: string
  fileSize?: number
  type?: string
  recorderId?: string
  secrecy?: number
}

/** 开始回放请求 */
export interface PlaybackStartRequest {
  deviceId: string
  channelId: string
  /** yyyy-MM-dd HH:mm:ss */
  startTime: string
  endTime: string
  /** 收流模式，默认 UDP */
  transport?: 'UDP' | 'TCP-passive' | 'TCP-active'
  /** 码流 token：空/不传主码流，非空子码流（如 '1' / 'stream:sub'） */
  streamNumber?: string
}

/** 停止回放请求 */
export interface PlaybackStopRequest {
  streamId: string
}

/** 回放控制请求 */
export interface PlaybackControlRequest {
  streamId: string
  /** pause / resume / seek / speed */
  action: 'pause' | 'resume' | 'seek' | 'speed'
  /** seek 秒数 / speed 倍数；pause/resume 省略 */
  param?: string
}

/** 回放结果（在点播结果基础上加时段元数据） */
export interface PlaybackResultVO extends PlayResultVO {
  startEpochSec?: number
  endEpochSec?: number
  durationSec?: number
}

/** 拉流代理分页查询参数 */
export interface StreamProxyPageRequest extends PageRequest {
  name?: string
  app?: string
  stream?: string
  /** 0 未启动 1 已推给 ZLM */
  status?: number
  /** 0 禁用 1 启用 */
  enabled?: number
}

/** 拉流代理 */
export interface StreamProxy {
  id: string
  name?: string
  app: string
  stream: string
  srcUrl: string
  mediaServerId?: string
  /** 0 TCP+UDP 自适应 1 TCP 2 UDP 3 组播 */
  rtpType?: number
  enableAudio?: number
  enableMp4?: number
  enableDisableNoneReader?: number
  enabled?: number
  proxyKey?: string
  /** 0 未启动 1 已推给 ZLM */
  status?: number
  lastError?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 拉流代理播放地址 */
export interface StreamProxyPlayUrl {
  app: string
  stream: string
  mediaServerId?: string
  flvUrl?: string
  wsFlvUrl?: string
  hlsUrl?: string
  rtcUrl?: string
}

/** 推流分页查询参数 */
export interface StreamPushPageRequest extends PageRequest {
  app?: string
  stream?: string
  mediaServerId?: string
  /** 0 已断 1 推流中 */
  pushing?: number
}

/** 推流 */
export interface StreamPush {
  id: string
  app: string
  stream: string
  mediaServerId: string
  vhost?: string
  streamSchema?: string
  /** 推流类型：rtmp / rtsp / rtc */
  originType?: string
  pushing?: number
  aliveSecond?: number
  pushTime?: string
  clientId?: string
  createTime?: string
}

/** 推流播放地址（复用与拉流代理一样的字段） */
export interface StreamPushPlayUrl {
  app: string
  stream: string
  mediaServerId?: string
  flvUrl?: string
  wsFlvUrl?: string
  hlsUrl?: string
  rtcUrl?: string
}

/** 推流方鉴权 */
export interface StreamPushClient {
  id: string
  clientId: string
  token: string
  enabled?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 推流方分页查询参数 */
export interface StreamPushClientPageRequest extends PageRequest {
  clientId?: string
  enabled?: number
}
