/**
 * 地图默认配置 — 前端兜底版
 *
 * <p><b>这是兜底默认，不是生产配置源。</b>运行时真正生效的配置来自后端
 * {@code GET /api/dashboard/map-config}（对应 application.yaml 的 {@code map.*} 段）。
 * 只有后端不可达 / 字段缺失时才会用这里的值。</p>
 *
 * <p>修改部署的默认视图/瓦片源，<b>改后端 application.yaml</b>，不要改这个文件。</p>
 */
export const mapConfig = {
  // 兜底初始视图中心：[经度, 纬度]，坐标系与 coord-system 字段保持一致（GCJ-02 / 高德）
  center: [106.45, 29.57] as [number, number],
  // 兜底初始缩放级别（3-18）
  zoom: 18,
  // 兜底缩放范围
  minZoom: 3,
  maxZoom: 18,
  // 兜底瓦片源：走 Vite 代理（/tiles/* → 高德，绕开 CORS）
  // 见 vite.config.ts 的 server.proxy；生产用 nginx 配同样反代
  tileUrl: '/tiles/appmaptile?x={x}&y={y}&z={z}&lang=zh_cn&size=1&scale=1&style=8',
  subdomains: '',
  disableClusteringAtZoom: 16,
  fetchDebounceMs: 300,
  markerLimit: 5000,
  // 兜底坐标系：GCJ-02（高德瓦片）
  coordSystem: 'GCJ02',
}

export type MapConfig = typeof mapConfig
