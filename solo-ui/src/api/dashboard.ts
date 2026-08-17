import { get } from '@/utils/request'
import type { DashboardStats } from '@/types/api'

/**
 * 首页聚合统计
 * <p>一次 GET 拉完全部首页数据，避免瀑布请求。</p>
 */
export function getDashboardStatsApi() {
  return get<DashboardStats>('/api/dashboard/stats')
}
