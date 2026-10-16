import { get, post, del } from '@/utils/request'
import type {
  MediaServer,
  MediaServerPageRequest,
  MediaServerRequest,
  StreamProxy,
  StreamProxyPageRequest,
  StreamProxyRequest,
  StreamPlayUrl,
  StreamPush,
  StreamPushPageRequest,
  StreamPushClient,
  StreamPushClientPageRequest,
  StreamPushClientRequest,
  MediaServerStreamStatsSummary,
  StreamStatsGbItem,
  StreamStatsOnvifItem,
  StreamStatsProxyItem,
  StreamStatsPushItem,
  StreamStatsTabKey,
} from '@/types/api'

/* ==================== 流媒体节点 ==================== */

export const getMediaServerPageApi = (data: MediaServerPageRequest) => {
  return post<{ total: number; rows: MediaServer[] }>('/api/media-server/page', data)
}

export const listMediaServerApi = () => {
  return get<MediaServer[]>('/api/media-server/list')
}

export const saveMediaServerApi = (data: MediaServerRequest) => {
  return post<void>('/api/media-server/save', data)
}

export const deleteMediaServerApi = (id: string) => {
  return del<void>(`/api/media-server/${id}`)
}

/* ---- 节点流统计 ---- */

export const getStreamStatsSummaryApi = (serverId: string) => {
  return get<MediaServerStreamStatsSummary>(
    `/api/media-server/${serverId}/stream-stats/summary`,
  )
}

export const getStreamStatsDetailApi = <
  T extends
    | StreamStatsGbItem
    | StreamStatsPushItem
    | StreamStatsProxyItem
    | StreamStatsOnvifItem,
>(
  serverId: string,
  type: StreamStatsTabKey,
) => {
  return get<T[]>(`/api/media-server/${serverId}/stream-stats/${type}`)
}

/* ==================== 拉流代理 ==================== */

export const getStreamProxyPageApi = (data: StreamProxyPageRequest) => {
  return post<{ total: number; rows: StreamProxy[] }>('/api/stream-proxy/page', data)
}

export const saveStreamProxyApi = (data: StreamProxyRequest) => {
  return post<void>('/api/stream-proxy/save', data)
}

export const updateStreamProxyApi = (data: StreamProxyRequest) => {
  return post<void>('/api/stream-proxy/update', data)
}

export const enableStreamProxyApi = (id: string) => {
  return post<void>(`/api/stream-proxy/${id}/enable`)
}

export const disableStreamProxyApi = (id: string) => {
  return post<void>(`/api/stream-proxy/${id}/disable`)
}

export const getStreamProxyPlayUrlsApi = (id: string) => {
  return get<StreamPlayUrl>(`/api/stream-proxy/${id}/play-urls`)
}

export const deleteStreamProxyApi = (id: string) => {
  return del<void>(`/api/stream-proxy/${id}`)
}

export const batchDeleteStreamProxyApi = (ids: string[]) => {
  return post<void>('/api/stream-proxy/batch/delete', { ids })
}

/* ==================== 推流 ==================== */

export const getStreamPushPageApi = (data: StreamPushPageRequest) => {
  return post<{ total: number; rows: StreamPush[] }>('/api/stream-push/page', data)
}

export const getStreamPushPlayUrlsApi = (id: string) => {
  return get<StreamPlayUrl>(`/api/stream-push/${id}/play-urls`)
}

export const stopStreamPushApi = (id: string) => {
  return post<void>(`/api/stream-push/${id}/stop`)
}

export const deleteStreamPushApi = (id: string) => {
  return del<void>(`/api/stream-push/${id}`)
}

export const batchDeleteStreamPushApi = (ids: string[]) => {
  return post<void>('/api/stream-push/batch/delete', { ids })
}

/* ==================== 推流方鉴权 ==================== */

export const getStreamPushClientPageApi = (data: StreamPushClientPageRequest) => {
  return post<{ total: number; rows: StreamPushClient[] }>(
    '/api/stream-push-client/page',
    data,
  )
}

export const saveStreamPushClientApi = (data: StreamPushClientRequest) => {
  return post<StreamPushClient>('/api/stream-push-client/save', data)
}

export const updateStreamPushClientApi = (data: StreamPushClientRequest) => {
  return post<void>('/api/stream-push-client/update', data)
}

export const resetStreamPushClientTokenApi = (id: string) => {
  return post<StreamPushClient>(`/api/stream-push-client/${id}/reset-token`)
}

export const deleteStreamPushClientApi = (id: string) => {
  return del<void>(`/api/stream-push-client/${id}`)
}
