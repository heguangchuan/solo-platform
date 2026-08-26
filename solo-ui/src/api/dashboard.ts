import { get } from '@/utils/request'
import type { DashboardStats } from '@/types/api'

/**
 * 首页聚合统计
 * <p>一次 GET 拉完全部首页数据，避免瀑布请求。</p>
 */
export function getDashboardStatsApi() {
  return get<DashboardStats>('/api/dashboard/stats')
}

/**
 * 地图配置（首页 Leaflet 地图初始化用）。
 *
 * <p>字段从后端 {@code application.yaml} 的 {@code map.*} 段读出。前端只在挂载时拉一次，
 * 任意字段为 null 时由前端用本地 {@code src/config/map.ts} 兜底（不阻塞渲染）。</p>
 */
export interface MapConfig {
  centerLng: number | null
  centerLat: number | null
  zoom: number | null
  minZoom: number | null
  maxZoom: number | null
  tileUrl: string | null
  tileSubdomains: string | null
  disableClusteringAtZoom: number | null
  fetchDebounceMs: number | null
  markerLimit: number | null
  /**
   * 坐标系：WGS84 / GCJ02 / BD09，与瓦片源对齐
   *
   * <p>前端用这个值做两件事：
   * <ol>
   *   <li>选 Leaflet 的 CRS（BD-09 → 用 {@code L.CRS.Baidu}；其他 → 默认 {@code L.CRS.EPSG3857}）</li>
   *   <li>后端 {@code queryMapMarkers} 出口时按这个值做坐标转换，前端无脑用</li>
   * </ol>
   */
  coordSystem: string | null
}

export function getDashboardMapConfigApi() {
  return get<MapConfig>('/api/dashboard/map-config')
}
