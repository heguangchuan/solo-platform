import { get } from '@/utils/request'
import type {
  HomeOverview,
  MediaServer,
  MediaServerStreamStatsSummary,
  StreamStatsGbItem,
  StreamStatsOnvifItem,
  StreamStatsProxyItem,
  StreamStatsPushItem,
  StreamStatsTabKey,
} from '@/types/api'

/** 首页概览统计：国标设备 / 国标通道 / ONVIF 设备 / 媒体节点的总数与在线数 */
export const getHomeOverviewApi = () => {
  return get<HomeOverview>('/api/home/overview')
}

/* ---- 首页媒体节点流统计（只读，不挂功能权限码，所有账号首页数据一致） ---- */

/** 首页-媒体节点列表 */
export const getHomeMediaServersApi = () => {
  return get<MediaServer[]>('/api/home/media-servers')
}

/** 首页-节点流统计 KPI 概要 */
export const getHomeStreamStatsSummaryApi = (serverId: string) => {
  return get<MediaServerStreamStatsSummary>(
    `/api/home/media-servers/${serverId}/stream-stats/summary`,
  )
}

/** 首页-节点流统计明细 */
export const getHomeStreamStatsDetailApi = <
  T extends
    | StreamStatsGbItem
    | StreamStatsPushItem
    | StreamStatsProxyItem
    | StreamStatsOnvifItem,
>(
  serverId: string,
  type: StreamStatsTabKey,
) => {
  return get<T[]>(`/api/home/media-servers/${serverId}/stream-stats/${type}`)
}
