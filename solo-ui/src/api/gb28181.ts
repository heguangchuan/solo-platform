import type {
  GbDevice,
  GbDevicePageRequest,
  GbDeviceDetectResult,
  GbPlatform,
  GbPlatformPageRequest,
  GbDeviceChannel,
  GbDeviceChannelPageRequest,
  GbDeviceChannelVO,
  GbDeviceChannelTreeNode,
  GbGroup,
  GbGroupVO,
  GbGroupPageRequest,
  GbMediaServer,
  GbMediaServerPageRequest,
  PageResult,
  PlayResultVO,
  PlayStartRequest,
  PlayStopRequest,
  RecordInfoQueryRequest,
  RecordItemResponse,
  PlaybackStartRequest,
  PlaybackStopRequest,
  PlaybackControlRequest,
  PlaybackResultVO,
  StreamProxy,
  StreamProxyPageRequest,
  StreamProxyPlayUrl,
  StreamPush,
  StreamPushPageRequest,
  StreamPushPlayUrl,
  StreamPushClient,
  StreamPushClientPageRequest,
} from '@/types/api'
import { get, post, del } from '@/utils/request'

/**
 * 设备分页
 */
export function getGbDevicePageApi(data: GbDevicePageRequest) {
  return post<PageResult<GbDevice>>('/api/gb28181/device/page', data)
}

/**
 * 设备详情
 */
export function getGbDeviceApi(deviceId: string) {
  return get<GbDevice>(`/api/gb28181/device/${deviceId}`)
}

/**
 * 触发设备信息 + 通道目录同步
 */
export function syncGbDeviceApi(deviceId: string) {
  return post<void>(`/api/gb28181/device/${deviceId}/sync`)
}

/**
 * 批量探测设备存活（主动发 SIP DeviceInfo 查询，写回 status）
 */
export function detectGbDeviceApi(ids: (number | string)[]) {
  return post<GbDeviceDetectResult>('/api/gb28181/device/detect', { ids })
}

/**
 * 下级级联平台分页
 */
export function getGbPlatformPageApi(data: GbPlatformPageRequest) {
  return post<PageResult<GbPlatform>>('/api/gb28181/platform/page', data)
}

/**
 * 新增下级级联平台白名单
 */
export function saveGbPlatformApi(data: Partial<GbPlatform>) {
  return post<void>('/api/gb28181/platform/save', data)
}

/**
 * 编辑下级级联平台白名单
 */
export function updateGbPlatformApi(data: Partial<GbPlatform>) {
  return post<void>('/api/gb28181/platform/update', data)
}

/**
 * 删除下级级联平台白名单
 */
export function deleteGbPlatformApi(id: string) {
  return del<void>(`/api/gb28181/platform/${id}`)
}

/**
 * 批量删除下级级联平台白名单
 */
export function batchDeleteGbPlatformApi(ids: (number | string)[]) {
  return post<void>('/api/gb28181/platform/batch/delete', { ids })
}

/**
 * 删除设备记录
 */
export function deleteGbDeviceApi(deviceId: string) {
  return del<void>(`/api/gb28181/device/${deviceId}`)
}

/**
 * 批量删除设备记录
 */
export function batchDeleteGbDeviceApi(ids: number[]) {
  return post<void>('/api/gb28181/device/batch/delete', { ids })
}

/**
 * 手动新增设备（状态/来源由后端设定），可同时新增多条通道
 */
export function saveGbDeviceApi(data: { device: Partial<GbDevice>, channels: Partial<GbDeviceChannel>[] }) {
  return post<void>('/api/gb28181/device/save', data)
}

/**
 * 手动编辑设备资料
 */
export function updateGbDeviceApi(data: Partial<GbDevice>) {
  return post<void>('/api/gb28181/device/update', data)
}

/**
 * 通道分页
 */
export function getGbDeviceChannelPageApi(data: GbDeviceChannelPageRequest) {
  return post<PageResult<GbDeviceChannelVO>>('/api/gb28181/device-channel/page', data)
}

/**
 * 按设备查通道
 */
export function listChannelsByDeviceApi(deviceDbId: string) {
  return get<GbDeviceChannel[]>(`/api/gb28181/device-channel/by-device/${deviceDbId}`)
}

/**
 * 手动新增通道（状态/来源由后端设定）
 */
export function saveGbDeviceChannelApi(data: Partial<GbDeviceChannel>) {
  return post<void>('/api/gb28181/device-channel/save', data)
}

/**
 * 手动编辑通道资料
 */
export function updateGbDeviceChannelApi(data: Partial<GbDeviceChannel>) {
  return post<void>('/api/gb28181/device-channel/update', data)
}

/**
 * 删除通道
 */
export function deleteGbDeviceChannelApi(id: string) {
  return del<void>(`/api/gb28181/device-channel/${id}`)
}

/**
 * 批量删除通道
 */
export function batchDeleteGbDeviceChannelApi(ids: string[]) {
  return post<void>('/api/gb28181/device-channel/batch/delete', { ids })
}

/**
 * 资源目录分组分页
 */
export function getGbGroupPageApi(data: GbGroupPageRequest) {
  return post<PageResult<GbGroupVO>>('/api/gb28181/group/page', data)
}

/**
 * 资源目录分组树（列表页展示用）
 */
export function getGbGroupTreeApi() {
  return get<GbGroupVO[]>('/api/gb28181/group/tree')
}

/**
 * 资源目录分组全量列表（下拉/选父级用）
 */
export function listGbGroupApi() {
  return get<GbGroup[]>('/api/gb28181/group/list')
}

/**
 * 新增资源目录分组
 */
export function saveGbGroupApi(data: Partial<GbGroup>) {
  return post<void>('/api/gb28181/group/save', data)
}

/**
 * 编辑资源目录分组
 */
export function updateGbGroupApi(data: Partial<GbGroup>) {
  return post<void>('/api/gb28181/group/update', data)
}

/**
 * 删除资源目录分组
 */
export function deleteGbGroupApi(id: string) {
  return del<void>(`/api/gb28181/group/${id}`)
}

/**
 * 流媒体节点分页
 */
export function getGbMediaServerPageApi(data: GbMediaServerPageRequest) {
  return post<PageResult<GbMediaServer>>('/api/media-server/page', data)
}

/**
 * 流媒体节点列表（下拉框用）
 */
export function listGbMediaServerApi() {
  return get<GbMediaServer[]>('/api/media-server/list')
}

/**
 * 新增 / 更新流媒体节点（按 serverId 唯一）
 */
export function saveGbMediaServerApi(data: Partial<GbMediaServer>) {
  return post<void>('/api/media-server/save', data)
}

/**
 * 删除流媒体节点
 */
export function deleteGbMediaServerApi(id: string) {
  return del<void>(`/api/media-server/${id}`)
}

/**
 * 开始点播（同步返回流就绪的 URL 集合，后端 INVITE→ZLM hook→Future 完成）
 */
export function startPlayApi(data: PlayStartRequest) {
  return post<PlayResultVO>('/api/gb28181/play/start', data)
}

/**
 * 停止点播（发 BYE + 关 RTP 端口 + 释放 SSRC）
 */
export function stopPlayApi(data: PlayStopRequest) {
  return post<void>('/api/gb28181/play/stop', data)
}

/**
 * 录像查询（RecordInfo，设备分片返回，后端合并后一次性返回）
 */
export function queryRecordInfoApi(data: RecordInfoQueryRequest) {
  return post<RecordItemResponse[]>('/api/gb28181/playback/query', data)
}

/**
 * 开始回放（同步返回流就绪的 URL 集合 + 时段元数据）
 */
export function playbackStartApi(data: PlaybackStartRequest) {
  return post<PlaybackResultVO>('/api/gb28181/playback/start', data)
}

/**
 * 停止回放
 */
export function playbackStopApi(data: PlaybackStopRequest) {
  return post<void>('/api/gb28181/playback/stop', data)
}

/**
 * 回放控制：pause / resume / seek / speed
 */
export function playbackControlApi(data: PlaybackControlRequest) {
  return post<void>('/api/gb28181/playback/control', data)
}

/**
 * 获取设备通道树（用于监控墙左侧树形选择）
 */
export function getDeviceChannelTreeApi() {
  return get<GbDeviceChannelTreeNode[]>('/api/gb28181/device-channel/tree')
}

// ============================================================
//  拉流代理
// ============================================================

/** 分页 */
export function getStreamProxyPageApi(data: StreamProxyPageRequest) {
  return post<PageResult<StreamProxy>>('/api/stream-proxy/page', data)
}

/** 详情 */
export function getStreamProxyApi(id: string) {
  return get<StreamProxy>(`/api/stream-proxy/${id}`)
}

/** 新增 */
export function saveStreamProxyApi(data: Partial<StreamProxy>) {
  return post<void>('/api/stream-proxy/save', data)
}

/** 编辑 */
export function updateStreamProxyApi(data: Partial<StreamProxy>) {
  return post<void>('/api/stream-proxy/update', data)
}

/** 启用（推流） */
export function enableStreamProxyApi(id: string) {
  return post<void>(`/api/stream-proxy/${id}/enable`)
}

/** 停用（摘流） */
export function disableStreamProxyApi(id: string) {
  return post<void>(`/api/stream-proxy/${id}/disable`)
}

/** 播放地址 */
export function getStreamProxyPlayUrlsApi(id: string) {
  return get<StreamProxyPlayUrl>(`/api/stream-proxy/${id}/play-urls`)
}

/** 删除 */
export function deleteStreamProxyApi(id: string) {
  return del<void>(`/api/stream-proxy/${id}`)
}

/** 批量删除 */
export function batchDeleteStreamProxyApi(ids: string[]) {
  return post<void>('/api/stream-proxy/batch/delete', { ids })
}

// ============================================================
//  推流管理
// ============================================================

/** 分页 */
export function getStreamPushPageApi(data: StreamPushPageRequest) {
  return post<PageResult<StreamPush>>('/api/stream-push/page', data)
}

/** 播放地址 */
export function getStreamPushPlayUrlsApi(id: string) {
  return get<StreamPushPlayUrl>(`/api/stream-push/${id}/play-urls`)
}

/** 强制中止 */
export function stopStreamPushApi(id: string) {
  return post<void>(`/api/stream-push/${id}/stop`)
}

/** 删除（仅对已中止） */
export function deleteStreamPushApi(id: string) {
  return del<void>(`/api/stream-push/${id}`)
}

/** 批量删除 */
export function batchDeleteStreamPushApi(ids: string[]) {
  return post<void>('/api/stream-push/batch/delete', { ids })
}

// ============================================================
//  推流方鉴权
// ============================================================

export function getStreamPushClientPageApi(data: StreamPushClientPageRequest) {
  return post<PageResult<StreamPushClient>>('/api/stream-push-client/page', data)
}

export function saveStreamPushClientApi(data: Partial<StreamPushClient>) {
  return post<StreamPushClient>('/api/stream-push-client/save', data)
}

export function updateStreamPushClientApi(data: Partial<StreamPushClient>) {
  return post<void>('/api/stream-push-client/update', data)
}

export function resetStreamPushClientTokenApi(id: string) {
  return post<StreamPushClient>(`/api/stream-push-client/${id}/reset-token`)
}

export function deleteStreamPushClientApi(id: string) {
  return del<void>(`/api/stream-push-client/${id}`)
}

// -------- GB28181 国标编码自动生成 --------

/** 本平台身份信息，供前端在设备未入库时本地拼编码使用 */
export interface GbPlatformInfo {
  /** 前 10 位行政区划中心编码，对应 gb28181.domain */
  domain: string
  /** 本平台 20 位 SIP ID */
  platformId: string
}

/** 读取本平台域 / SIP ID */
export function getGbPlatformInfoApi() {
  return get<GbPlatformInfo>('/api/gb28181/code/platform-info')
}

/** 生成下一个可用设备国标编码（20位）。type 为空后端默认 132（IPC） */
export function getNextGbDeviceCodeApi(type?: string) {
  return get<string>('/api/gb28181/code/next-device', { params: { type } })
}

/** 生成指定设备下一个可用通道国标编码（20位）。通道编码在所属设备内唯一。type 为空后端默认 131（摄像机） */
export function getNextGbChannelCodeApi(deviceDbId: string, type?: string) {
  return get<string>('/api/gb28181/code/next-channel', { params: { deviceDbId, type } })
}

/** 生成下一个可用资源目录分组国标编码（20位）。type 为空后端默认 216（虚拟组织） */
export function getNextGbGroupCodeApi(type?: string) {
  return get<string>('/api/gb28181/code/next-group', { params: { type } })
}
