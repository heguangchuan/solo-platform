import { get } from '@/utils/request'
import type { HomeOverview } from '@/types/api'

/** 首页概览统计：国标设备 / 国标通道 / ONVIF 设备 / 媒体节点的总数与在线数 */
export const getHomeOverviewApi = () => {
  return get<HomeOverview>('/api/home/overview')
}
