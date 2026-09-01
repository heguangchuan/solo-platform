import { get } from '@/utils/request'

/**
 * 地图配置（首页 Leaflet 地图初始化用）。
 *
 * <p>字段从后端 {@code application.yaml} 的 {@code map.*} 段读出。<b>唯一配置源</b>，
 * 任何字段为 null 都会让前端判定配置不完整、地图不渲染（fail-fast）。</p>
 */
export interface MapConfig {
  centerLng: number | null
  centerLat: number | null
  zoom: number | null
  minZoom: number | null
  maxZoom: number | null
  tileUrl: number | null
  tileSubdomains: number | null
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
  /**
   * 瓦片模式：offline / online
   *
   * <p>纯 UI 元数据，<b>不要用这个字段去拼 tileUrl</b>（{@link tileUrl} 已经够用）。
   * 这里只给前端一个角标 / 提示用——比如离线模式下显示「⚠️ 离线模式，部分区域可能没瓦片」之类的提示。</p>
   */
  tileMode: string | null
}

export function getDashboardMapConfigApi() {
  return get<MapConfig>('/api/dashboard/map-config')
}
