/** 国标 GB28181 模块类型（当前覆盖：设备管理、设备通道 CRUD） */
import type { PageRequest } from './common'

/** 设备通道目录同步状态 */
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
  /** 设备状态：1 在线，0 离线，2 未注册 */
  status?: number
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
}

/** 国标设备 */
export interface GbDevice {
  id: string
  /** 国标编码（20 位） */
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
  /** 传输协议：UDP / TCP */
  transport?: string
  expires?: number
  /** 字符编码：GB2312 / UTF-8 */
  charset?: string
  /** 1 在线，0 离线，2 未注册 */
  status: number
  registerTime?: string
  keepaliveTime?: string
  lastCatalogSyncTime?: string
  /** 目录订阅周期（秒），0=不订阅 */
  subscribeCycleForCatalog?: number
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
  createTime: string
  updateTime: string
}

/** 设备下挂通道（新增设备向导第二步编辑用，仅需两个字段） */
export interface GbDeviceChannelDraft {
  /** 通道国标编码（20 位） */
  channelId: string
  /** 通道名称 */
  name: string
}

/** 新增设备请求体：设备信息 + 一次性带多条通道 */
export interface GbDeviceSaveRequest {
  device: Partial<GbDevice>
  channels: GbDeviceChannelDraft[]
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

/* ----------------------- 设备通道 ----------------------- */

/** 通道列表分页查询参数 */
export interface GbDeviceChannelPageRequest extends PageRequest {
  /** 所属设备国标编码 */
  deviceId?: string
  /** 通道国标编码 */
  channelId?: string
  /** 通道名称 */
  name?: string
  /** ON 在线 / OFF 离线 / UNREGISTERED 未注册 */
  status?: string
  /** 父分组国标编码（后端 LIKE 'value%'） */
  parentId?: string
  /** 资源类型：0 通道 / 1 分组；不传=全部 */
  parental?: number
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
  /** 云台类型：0 未知 / 1 球机 / 2 半球 / 3 固定枪机 / 4 遥控枪机（仅对通道生效） */
  ptzType?: number
}

/** 国标资源目录项（通道 + 分组合一） */
export interface GbDeviceChannel {
  id: string
  /** 所属设备国标编码（关联 gb_device.device_id）；通道与分组均必填 */
  deviceId?: string
  /** catalog item 20 位国标码：分组 215业务分组/216虚拟组织；通道 131摄像机/132IPC */
  channelId: string
  /** 设备上报原始名称（仅通道） */
  reportName?: string
  /** 用户自定义显示名称（展示优先级高于 reportName） */
  name?: string
  manufacturer?: string
  model?: string
  /** 设备归属 */
  owner?: string
  /** 行政区域编码 */
  civilCode?: string
  /** 所属警区 */
  block?: string
  /** 安装地址 */
  address?: string
  /** 0 通道（叶子）/ 1 分组（容器） */
  parental?: number
  /** ON 在线 / OFF 离线 / UNREGISTERED 未注册 */
  status?: string
  longitude?: number
  latitude?: number
  /** GCJ-02 / BD-09 冗余副本（历史数据可能为 null） */
  longitudeGcj02?: number | null
  latitudeGcj02?: number | null
  longitudeBd09?: number | null
  latitudeBd09?: number | null
  /** 入参坐标系（仅写时用，DB 不存）：WGS84 / GCJ02 / BD09 */
  coordSystem?: string
  /** 云台类型：0 未知 / 1 球机 / 2 半球 / 3 固定枪机 / 4 遥控枪机 */
  ptzType?: number
  /** 父 catalog item 国标编码（指向同表 channel_id）；"0"=根 */
  parentId?: string
  /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
  source?: number
  createTime?: string
  updateTime?: string
}

/** 国标设备状态统计行（GET /api/gb28181/device/status-count） */
export interface GbDeviceStatusCount {
  /** 1 在线 / 0 离线 / 2 未注册 */
  status: number
  count: number
}

/** 国标通道状态统计行（GET /api/gb28181/device-channel/status-count） */
export interface GbChannelStatusCount {
  /** 0 通道 / 1 分组 */
  parental: number
  /** 通道：ON / OFF / UNREGISTERED；分组固定 ON */
  status: string
  count: number
}

/** 设备报警（报警中心，POST /api/gb28181/alarm/page 行项） */
export interface GbDeviceAlarm {
  id: string
  /** 报警所属设备国标 ID */
  deviceId: string
  /** 报警通道国标 ID */
  channelId: string
  /** 通道名称（落库时冗余） */
  channelName: string
  /** 报警级别 1~4（1 最严重） */
  alarmPriority: number
  /** 报警方式原始码（如 "2"，可为组合码 "12"） */
  alarmMethod: string
  /** 报警方式描述（如"设备报警"） */
  alarmMethodDesc: string
  /** 报警类型子码（设备报警时的具体类型，如移动侦测） */
  alarmType: string
  /** 设备上报的报警发生时间 */
  alarmTime: string
  /** 报警内容描述 */
  alarmDescription: string
  longitude?: number | null
  latitude?: number | null
  /** 处置状态：0 未处理、1 已确认、2 已处置、3 误报 */
  handleStatus: number
  handleRemark: string
  handleUser: string
  handleTime: string
  /** 平台收到报警时间 */
  receiveTime: string
  createTime: string
}

/** 报警分页查询参数（POST /api/gb28181/alarm/page） */
export interface GbDeviceAlarmPageRequest extends PageRequest {
  deviceId?: string
  channelId?: string
  alarmPriority?: number
  handleStatus?: number
  startTime?: string
  endTime?: string
}

/** 报警处置请求（POST /api/gb28181/alarm/handle） */
export interface GbDeviceAlarmHandleRequest {
  id: string
  /** 1 已确认、2 已处置、3 误报 */
  handleStatus: number
  handleRemark?: string
}

/** 设备移动位置点（GPS 轨迹，POST /api/gb28181/position/page 行项） */
export interface GbMobilePosition {
  id: string
  /** 设备国标 ID */
  deviceId: string
  /** 设备名称（落库时冗余） */
  deviceName: string
  /** 设备上报的定位时间 */
  positionTime: string
  /** 经度（WGS84） */
  longitude?: number | null
  /** 纬度（WGS84） */
  latitude?: number | null
  /** 海拔（米） */
  altitude?: number | null
  /** 速度（设备原值，km/h 或 m/s 视厂商而定） */
  speed?: number | null
  /** 方向角 0~359，正北为 0 顺时针 */
  direction?: number | null
  /** 平台收到报文时间 */
  receiveTime: string
  createTime: string
}

/** 移动位置分页 / 轨迹查询参数（/position/page、/position/track） */
export interface GbMobilePositionPageRequest extends PageRequest {
  deviceId?: string
  startTime?: string
  endTime?: string
}

/** 云台预置位（GET /api/gb28181/ptz/preset/list/{channelDbId}） */
export interface GbPtzPreset {
  id: string
  /** 通道数据库主键（gb_device_channel.id） */
  channelDbId: string
  /** 预置位编号（设备侧编号，从 1 开始） */
  presetId: number
  /** 预置位名称（如"大门入口"） */
  name: string
  createTime: string
  updateTime: string
}

/** 云台巡航路线（GET /api/gb28181/ptz/cruise/list/{channelDbId}） */
export interface GbPtzCruise {
  id: string
  /** 通道数据库主键（gb_device_channel.id） */
  channelDbId: string
  /** 路线编号（设备侧编号，通道内从 1 开始） */
  cruiseId: number
  /** 路线名称 */
  name: string
  /** 巡航速度 1~255 */
  speed: number
  /** 默认停留秒数 */
  staySeconds: number
  createTime: string
  updateTime: string
}

/** 巡航点（路线内的一个预置位停靠） */
export interface GbPtzCruisePoint {
  id: string
  /** 关联 gb_ptz_cruise.id */
  cruiseId: string
  /** 预置位编号（对应 gb_ptz_preset.preset_id） */
  presetId: number
  /** 预置位名称冗余 */
  presetName: string
  /** 单点停留秒数，null 用路线默认 */
  staySeconds?: number | null
  /** 巡航顺序，从 0 开始 */
  sortOrder: number
  createTime: string
}

/** 巡航路线详情（GET /api/gb28181/ptz/cruise/{id}） */
export interface GbPtzCruiseDetail {
  cruise: GbPtzCruise
  points: GbPtzCruisePoint[]
}

/** 新建巡航路线请求（POST /api/gb28181/ptz/cruise/save） */
export interface GbPtzCruiseSaveRequest {
  channelDbId: string
  name: string
  speed: number
  staySeconds: number
  points: Array<{ presetId: number; staySeconds?: number | null }>
}

/* ----------------------- 点播 / 云台 ----------------------- */

/** 开始点播请求 */
export interface GbPlayStartRequest {
  deviceId: string
  channelId: string
  /** 收流模式，默认 UDP */
  transport?: 'UDP' | 'TCP-passive' | 'TCP-active'
  /** 码流 token：空/不传主码流，非空子码流 */
  streamNumber?: string
}

/** 停止点播请求 */
export interface GbPlayStopRequest {
  streamId: string
}

/** 点播结果（前端按播放器能力挑一个 URL） */
export interface GbPlayResult {
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

/* ----------------------- 语音广播 ----------------------- */

/** 开始语音广播请求 */
export interface GbBroadcastStartRequest {
  deviceId: string
  channelId: string
  /** broadcast=单向喊话（默认），talk=双向对讲 */
  mode?: 'broadcast' | 'talk'
}

/** 停止语音广播请求 */
export interface GbBroadcastStopRequest {
  streamId: string
}

/** WebRTC SDP 交换请求（浏览器 offer → ZLM answer，平台代理转发） */
export interface GbBroadcastWebRtcRequest {
  streamId: string
  sdp: string
}

/** 开始广播结果：rtcPushUrl 仅用于展示/排查，推流 SDP 走后端代理接口交换 */
export interface GbBroadcastResult {
  streamId: string
  app: string
  mediaServerId: string
  rtcPushUrl: string
}

/**
 * 云台方向/变倍指令。
 * 方向：up/down/left/right/upleft/upright/downleft/downright；
 * 变倍：zoomin/zoomout；停止：stop。
 */
export type GbPtzCommand =
  | 'up'
  | 'down'
  | 'left'
  | 'right'
  | 'upleft'
  | 'upright'
  | 'downleft'
  | 'downright'
  | 'zoomin'
  | 'zoomout'
  | 'stop'

/** 镜头聚焦方向 */
export type GbPtzFocusDirection = 'near' | 'far' | 'stop'

/** 镜头光圈方向 */
export type GbPtzIrisDirection = 'open' | 'close' | 'stop'

/** 拉框缩放：坐标均为视频画面像素值（不是窗口像素） */
export interface GbDragZoomRequest {
  action: 'in' | 'out'
  /** 播放窗口长度像素 */
  length: number
  /** 播放窗口宽度像素 */
  width: number
  midPointX: number
  midPointY: number
  /** 拉框长度像素 */
  lengthX: number
  /** 拉框宽度像素 */
  lengthY: number
}

/** 看守位设置：关闭时只传 enabled=false */
export interface GbHomePositionRequest {
  enabled: boolean
  /** 自动归位时间（秒），开启时用 */
  resetTime?: number
  /** 预置位编号，开启时用 */
  presetIndex?: number
}

/* ----------------------- 播放器运行信息（Jessibuca 事件上抛） ----------------------- */

/** 视频流信息（Jessibuca videoInfo 事件） */
export interface GbVideoInfo {
  width: number
  height: number
  /** 编码名，如 H.264 / H.265 */
  encType?: string
  /** 编码代码：10=H.264，12=H.265 */
  encTypeCode?: number
}

/** 音频流信息（Jessibuca audioInfo 事件） */
export interface GbAudioInfo {
  numOfChannels: number
  sampleRate: number
  encType?: string
  /** 10=AAC，7=G.711A，8=G.711U */
  encTypeCode?: number
}

/** 播放统计（Jessibuca stats 事件） */
export interface GbPlayerStats {
  /** 帧率 */
  fps: number
  /** 视频字节/秒 */
  vbps: number
  /** 音频字节/秒 */
  abps: number
  /** 缓冲毫秒 */
  buf: number
}

/* ----------------------- 录像回放 ----------------------- */

/** 录像段（RecordInfo 查询结果） */
export interface GbRecordItem {
  channelId: string
  name?: string
  filePath?: string
  address?: string
  /** ISO-8601 起始，如 2026-06-30T08:00:00 */
  startTime?: string
  endTime?: string
  fileSize?: number
  type?: string
  recorderId?: string
  secrecy?: number
}

/** 录像查询请求 */
export interface GbRecordQueryRequest {
  deviceId: string
  channelId: string
  /** yyyy-MM-dd HH:mm:ss */
  startTime: string
  endTime: string
}

/** 回放控制动作 */
export type GbPlaybackAction = 'pause' | 'resume' | 'seek' | 'speed'

/** 回放控制请求 */
export interface GbPlaybackControlRequest {
  streamId: string
  action: GbPlaybackAction
  /** seek 秒数 / speed 倍数；pause/resume 省略 */
  param?: string
}

/** 回放结果（在点播结果基础上加时段元数据） */
export interface GbPlaybackResult extends GbPlayResult {
  startEpochSec?: number
  endEpochSec?: number
  durationSec?: number
}

/** 录像下载请求（与回放同构，加 speed 字段） */
export interface GbDownloadStartRequest {
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
 * 录像下载任务快照
 * 状态机：INVITING → RECORDING → READY / FAILED / TIMEOUT / NOT_FOUND
 */
export interface GbDownloadResult {
  streamId?: string
  status?: string
  startEpochSec?: number
  endEpochSec?: number
  durationSec?: number
  /** READY 状态有值，MinIO 1h 预签名 URL */
  downloadUrl?: string
  errorMsg?: string
}

/* ==================== 级联平台 ==================== */

/** 本平台国标身份（GET /api/gb28181/parent-platform/local-info） */
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
  /** 启用状态：0 禁用 1 启用 */
  enabled: number
  /** 在线状态：0 离线 1 在线 */
  status?: number
  /** Catalog 响应单包 Item 数：1/2/4/8；undefined 走全局默认 */
  catalogGroup?: number
  /** 本地设备国标编号（后端额外返回） */
  deviceGbId?: string
  createTime?: string
  updateTime?: string
}

/** 上级平台分页查询参数 */
export interface GbParentPlatformPageRequest extends PageRequest {
  name?: string
  serverGbId?: string
  enabled?: number
}

/** 上级平台推送目录分组 */
export interface GbPlatformGroup {
  id: string
  name: string
  /** 20 位国标业务码（推送 XML DeviceID 用） */
  code: string
  platformId: string
  /** 父分组 id，根节点 = 0 */
  parentId: string
  children?: GbPlatformGroup[]
  createTime?: string
  updateTime?: string
}

/** 推送分组新建 / 编辑请求 */
export interface GbPlatformGroupSaveRequest {
  name: string
  code: string
  parentId: string
}

/** 推送目录：已挂载/未挂载通道分页查询参数 */
export interface GbShareChannelPageRequest extends PageRequest {
  /** 名称 / 通道国标编码 模糊匹配 */
  keyword?: string
}

/** 批量挂载请求体 */
export interface GbAddShareRequest {
  resourceIds: string[]
}

/** 已挂载通道行（后端在通道基础上额外返回推送国标码） */
export interface GbSharedChannel extends GbDeviceChannel {
  /** 推送国标码，撞码重编时与 channelId 不同 */
  exposedCode?: string
}

/** 国标接入禁用名单（黑名单） */
export interface GbPlatform {
  id: string
  /** 被禁用的国标 ID（20 位） */
  platformId: string
  /** 备注名（可空） */
  name?: string
  /** 0 拦截中（默认）1 临时放行 */
  enabled: number
  /** 关联设备状态：0 离线 1 在线 2 未注册 */
  status?: number
  /** 关联设备最近心跳时间 */
  keepaliveTime?: string
  createTime: string
  updateTime: string
}

/** 接入禁用名单分页查询参数 */
export interface GbPlatformPageRequest extends PageRequest {
  platformId?: string
  name?: string
  enabled?: number
}

/* ----------------------- 云端录像中心 ----------------------- */

/** 云端录像文件视图（status 0上传中 1成功 2失败） */
export interface GbRecordFile {
  id: string
  deviceId?: string
  deviceName?: string
  channelId?: string
  channelName?: string
  startTime?: string
  endTime?: string
  /** 时长（秒），上传中的文件可能为空 */
  duration?: number | null
  /** 文件大小（字节） */
  fileSize?: number | null
  status: number
  createTime?: string
}

/** 录像文件分页查询参数（注意后端分页字段是 size，不是 pageSize） */
export interface GbRecordFilePageRequest extends PageRequest {
  deviceId?: string
  channelId?: string
  /** yyyy-MM-dd HH:mm:ss */
  start?: string
  /** yyyy-MM-dd HH:mm:ss */
  end?: string
}

/* ----------------------- 观看记录 ----------------------- */

/** 观看记录（actorType 1平台用户 2开放API应用；status 0观看中 1正常结束 2兜底关闭） */
export interface GbViewRecord {
  id: string
  sessionId?: string
  actorType: number
  actorId?: string
  actorName?: string
  deviceId?: string
  deviceName?: string
  channelId?: string
  channelName?: string
  streamId?: string
  app?: string
  streamSchema?: string
  clientIp?: string
  mediaServerId?: string
  startTime?: string
  endTime?: string
  /** 时长（秒），观看中为空 */
  durationSec?: number | null
  totalBytes?: number | null
  status: number
  closeReason?: string
  createTime?: string
}

/* ----------------------- 云端录像计划 ----------------------- */

/** 录像计划周时段（dayOfWeek 1周一 ~ 7周日，时间 HH:mm，支持跨午夜） */
export interface GbRecordPlanPeriod {
  id?: string
  planId?: string
  dayOfWeek: number
  startTime: string
  endTime: string
}

/** 录像计划分页查询请求 */
export interface GbRecordPlanPageRequest {
  /** 当前页码（从 1 开始） */
  page?: number
  /** 每页条数 */
  pageSize?: number
  /** 所属设备国标 ID */
  deviceId?: string
  /** 通道国标 ID */
  channelId?: string
  /** 启用状态：1 启用、0 停用、不传全部 */
  enabled?: number
}

/** 云端录像计划（周计划） */
export interface GbRecordPlan {
  id?: string
  name?: string
  deviceId: string
  channelId: string
  /** 设备名称（列表冗余返回，便于展示） */
  deviceName?: string
  /** 通道名称（列表冗余返回，便于展示） */
  channelName?: string
  /** 0停 1启 */
  enabled?: number
  /** UDP / TCP-active / TCP-passive，空随设备 */
  transport?: string
  /** 码流 token，空=主码流 */
  streamNumber?: string
  /** 保留天数，0=跟随全局 */
  retentionDays?: number
  createTime?: string
  updateTime?: string
  /** 周时段，仅详情/保存时携带 */
  periods?: GbRecordPlanPeriod[]
}
