import type {
  GbDevice,
  GbDevicePageRequest,
  GbDeviceChannel,
  GbDeviceChannelPageRequest,
  GbDeviceChannelVO,
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
 * 删除设备记录
 */
export function deleteGbDeviceApi(deviceId: string) {
  return del<void>(`/api/gb28181/device/${deviceId}`)
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
 * 流媒体节点分页
 */
export function getGbMediaServerPageApi(data: GbMediaServerPageRequest) {
  return post<PageResult<GbMediaServer>>('/api/gb28181/media-server/page', data)
}

/**
 * 流媒体节点列表（下拉框用）
 */
export function listGbMediaServerApi() {
  return get<GbMediaServer[]>('/api/gb28181/media-server/list')
}

/**
 * 新增 / 更新流媒体节点（按 serverId 唯一）
 */
export function saveGbMediaServerApi(data: Partial<GbMediaServer>) {
  return post<void>('/api/gb28181/media-server/save', data)
}

/**
 * 删除流媒体节点
 */
export function deleteGbMediaServerApi(id: string) {
  return del<void>(`/api/gb28181/media-server/${id}`)
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
