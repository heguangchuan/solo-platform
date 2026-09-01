/** GB/T 28181 国标模块相关类型 */
import type { PageRequest } from './common'
import type { OnvifSession } from './onvif'

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
  /** SIP 认证用户名（默认同 gb28181.id 全局配置） */
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
  /** Catalog 响应单包 Item 数（ADR-027）：1/2/4/8。undefined=走全局默认 */
  catalogGroup?: number
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

/** 共享目录树节点（老版本，保留兼容：设备作枝干，分组/通道作叶子） */
export interface GbShareTreeNode {
  id: string
  label: string
  type: 'DEVICE' | 'GROUP' | 'CHANNEL'
  children?: GbShareTreeNode[]
}

/** 上级平台推送目录分组（per-platform 推送树节点）
 *  对应后端 GbPlatformGroup 实体。children 字段由后端 Service 构树时挂上。 */
export interface GbPlatformGroup {
  id: number
  name: string
  /** 20 位国标业务码（推送时 XML DeviceID 用） */
  code: string
  platformId: number
  /** 父分组 id（雪花），根节点 = 0 */
  parentId: number
  children?: GbPlatformGroup[]
  createTime?: string
  updateTime?: string
}

/** 推送分组新建 / 编辑请求（对应后端 GbPlatformGroupSaveRequest） */
export interface GbPlatformGroupSaveRequest {
  name: string
  code: string
  parentId: number
}

/** 推送目录：已挂载/未挂载通道分页查询参数 */
export interface UnmountedChannelPageRequest extends PageRequest {
  /** 名称 / 通道国标编码 模糊匹配 */
  keyword?: string
}

/**
 * 批量挂载请求体（对应后端 POST /api/gb28181/parent-platform/{id}/share）
 * <p>resourceIds 来自 {@link GbDeviceChannel.id}；parental=0 通道 / parental=1 分组均接受</p>
 */
export interface AddShareRequest {
  resourceIds: number[]
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
  /**
   * GCJ-02 副本（高德瓦片用）：由 WGS-84 canonical 算出来冗余存。
   * 回显用：picker 按当前瓦片系挑对应那对，避免前端再做 WGS-84 → 瓦片系二次转换。
   * 历史数据可能为 null。
   */
  longitudeGcj02?: number | null
  latitudeGcj02?: number | null
  /**
   * BD-09 副本（百度瓦片用），同 longitudeGcj02
   */
  longitudeBd09?: number | null
  latitudeBd09?: number | null
  /**
   * 入参坐标系（仅写时用，DB 不存）：WGS84 / GCJ02 / BD09。
   * picker 选中时由 picker 写入，告诉后端怎么把 longitude/latitude 转成 WGS-84 canonical。
   * 不传则按 WGS84 处理。
   */
  coordSystem?: string
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

/** 录像下载请求（与回放同构，加 speed 字段） */
export interface DownloadStartRequest {
  deviceId: string
  channelId: string
  /** yyyy-MM-dd HH:mm:ss */
  startTime: string
  endTime: string
  /** 收流模式，默认 TCP-passive */
  transport?: 'UDP' | 'TCP-passive' | 'TCP-active'
  /** 下载倍速：1 / 2 / 4 / 8，默认 1 */
  speed?: 1 | 2 | 4 | 8
  /** 码流 token：空/不传主码流 */
  streamNumber?: string
}

/**
 * 录像下载响应 / 任务快照
 * 状态机：INVITING → RECORDING → READY / FAILED / TIMEOUT / NOT_FOUND
 */
export interface DownloadResultVO {
  streamId?: string
  /** INVITING / RECORDING / READY / FAILED / TIMEOUT / NOT_FOUND */
  status?: string
  startEpochSec?: number
  endEpochSec?: number
  durationSec?: number
  /** READY 状态有值，MinIO 1h 预签名 URL */
  downloadUrl?: string
  errorMsg?: string
}

/** 拉流代理分页查询参数 */
export interface StreamProxyPageRequest extends PageRequest {
  app?: string
  stream?: string
  /** 0 未启动 1 已启动 */
  status?: number
}

/** 拉流代理 */
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
}

/** 推流（行存在即在推，由 ZLM hook 驱动入 / 出表） */
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

/**
 * 国标点播 / 回放 / 下载会话（运行时状态，Redis 主存）
 * <p>与后端 {@code com.solo.gb28181.entity.GbInviteSession} 字段一一对应。
 * 节点流统计弹窗的"国标会话"Tab 用这个类型。</p>
 */
export interface GbInviteSession {
  /** 业务主键：<deviceId>_<channelId>（回放再附时间段后缀） */
  streamId: string
  serverId: string
  deviceId: string
  channelId: string
  ssrc?: string
  rtpPort?: number
  transport?: string
  streamNumber?: string
  callId?: string
  fromTag?: string
  toTag?: string
  /** PLAY / PLAYBACK / DOWNLOAD */
  sessionType?: string
  startTimeStr?: string
  endTimeStr?: string
  /** INVITING / READY / STOPPED / TIMEOUT / FAILED */
  status?: string
  flvUrl?: string
  wsFlvUrl?: string
  hlsUrl?: string
  rtcUrl?: string
  startTime?: string
  readyTime?: string
  stopTime?: string
  failReason?: string
}

/**
 * 节点流统计详情（弹窗下钻用）
 * <p>对应 {@code GET /api/media-server/{serverId}/stream-stats}。
 * 四类会话总数 + 各自明细列表，前端弹窗顶部放 KPI，下方分 Tab 列明细。</p>
 */
export interface MediaServerStreamStats {
  serverId: string
  serverName?: string
  hostIp: string
  httpPort: number
  /** 0 离线 1 在线 */
  status: number
  gbStreamCount: number
  pushStreamCount: number
  proxyStreamCount: number
  onvifStreamCount: number
  /**
   * 该节点上的活跃流总数（ZLM on_stream_changed hook → Redis ZSet 维护）
   * <p>与四类累加之和不一定相等——前者是 ZLM 视角的权威值，理论上覆盖所有来源；
   * 后者只覆盖四类已建模会话，未来新增来源时差值会先于 total 反映出来。</p>
   */
  total: number
  gbSessions: GbInviteSession[]
  pushes: StreamPush[]
  proxies: StreamProxy[]
  onvifSessions: OnvifSession[]
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

/** 本平台国标身份（对应 GET /api/gb28181/parent-platform/local-info） */
export interface GbLocalPlatformInfo {
  /** 本平台 SIP ID（20 位国标编码） */
  id?: string
  /** 本平台 SIP 域 */
  domain?: string
  /** 本平台 SIP 监听 IP */
  ip?: string
  /** 本平台 SIP 监听端口 */
  port?: number
  /** 鉴权密码（上级平台白名单配置用） */
  password?: string
  /** 设备注册有效期（秒） */
  registerExpires?: number
}
