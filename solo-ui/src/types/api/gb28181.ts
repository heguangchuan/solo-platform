/** GB/T 28181 国标模块相关类型 */
import type { PageRequest } from './common'

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
  /** 所属平台 ID：0000=自有平台；其他=上级级联平台 ID */
  platformId?: string
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

/** 下级级联平台白名单 */
export interface GbPlatform {
  id: string
  /** 平台国标 ID（20 位） */
  platformId: string
  /** 平台名称 */
  name: string
  /** 注册鉴权密钥（后端自动生成 32 位 UUID，不可编辑） */
  authKey?: string
  /** 启用状态：0 禁用 1 启用 */
  enabled: number
  /** 关联设备状态：0 离线 1 在线 2 未注册（由 service 关联回填） */
  status?: number
  /** 关联设备最后心跳时间 */
  keepaliveTime?: string
  /** 该平台同步来的通道总数 */
  channelCount?: number
  createTime: string
  updateTime: string
}

/** 下级级联平台分页查询参数 */
export interface GbPlatformPageRequest {
  page: number
  pageSize: number
  platformId?: string
  name?: string
  enabled?: number
}

/** 通道列表分页查询参数 */
export interface GbDeviceChannelPageRequest extends PageRequest {
  deviceId?: string
  channelId?: string
  name?: string
  status?: string
  /** 所属资源目录分组ID（按分组筛选通道） */
  groupId?: string
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
}

/** 国标设备通道 */
export interface GbDeviceChannel {
  id: string
  deviceDbId: string
  deviceId: string
  channelId: string
  /** 通道上报原始名称（Catalog 目录同步） */
  reportName?: string
  /** 用户自定义显示名称（展示优先级高于 reportName） */
  name?: string
  manufacturer?: string
  model?: string
  owner?: string
  civilCode?: string
  block?: string
  address?: string
  parental?: number
  status?: string
  longitude?: number
  latitude?: number
  ptzType?: number
  /** 所属平台 ID：0000=自有平台；其他=上级级联平台 ID（与所属设备一致） */
  platformId?: string
  /** 所属资源目录主键（引用 gb_group.id；默认分组 0000） */
  groupId?: string
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
  createTime: string
  updateTime: string
}

/** 通道列表分页响应：在 entity 之上补充设备名/目录名 */
export interface GbDeviceChannelVO extends GbDeviceChannel {
  /** 所属设备名称（后端按 deviceId 反查 gb_device.name 回填） */
  deviceName?: string
  /** 所属资源目录名称（后端按 groupId 反查 gb_group.name 回填） */
  groupName?: string
}

/** 资源目录分组分页查询参数 */
export interface GbGroupPageRequest extends PageRequest {
  /** 分组国标ID（模糊） */
  groupId?: string
  /** 分组名称（模糊） */
  name?: string
  /** 父节点主键 */
  parentId?: string
  /** 所属平台ID：0000=自有；其他=级联来源 */
  platformId?: string
}

/** 国标资源目录分组（业务分组 215 / 虚拟组织 216） */
export interface GbGroup {
  /** 数据主键：默认分组固定 0000，其余雪花ID；通道 groupId 引用此列 */
  id: string
  /** 分组国标ID（20位；第11-13位 215业务分组 / 216虚拟组织，拼 Catalog 上报上级用） */
  groupId: string
  /** 分组名称（作为 Catalog Item Name 上报上级） */
  name: string
  /** 父节点主键：0=根节点 */
  parentId?: string
  /** 数据来源：0000=自有平台；其他=级联来源平台ID */
  platformId?: string
  /** 排序值（同层从小到大） */
  sort?: number
  createTime?: string
  updateTime?: string
}

/**
 * 分组分页 / 树形响应 VO：在 entity 之上扩出 children / defaultGroup 视图字段
 * 分页接口无 children，树接口两者都有；用同一类型简化前端处理
 */
export interface GbGroupVO extends GbGroup {
  /** 子节点（树形接口返回） */
  children?: GbGroupVO[]
  /** 是否默认分组（后端按 gb28181.default-group.id 回填）：默认分组不可删除、只可改名称/排序 */
  defaultGroup?: boolean
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
  transport?: 'UDP' | 'TCP-passive'
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

/** 设备通道树节点 */
export interface GbDeviceChannelTreeNode {
  id: string
  label: string
  children?: GbDeviceChannelTreeNode[]
  deviceId?: string
  channelId?: string
  isDevice: boolean
  status?: number
  channelStatus?: string
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
