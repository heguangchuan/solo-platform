<script setup lang="ts">
/**
 * 首页地图面板
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>Leaflet + 瓦片底图（瓦片源由后端 application.yaml 控制）</li>
 *   <li>视口过滤 + debounce 拉后端拿 marker</li>
 *   <li>客户端 markercluster 聚合（zoom ≥ 阈值自动散开）</li>
 *   <li>在线绿 / 离线灰 / 未注册橙三色 divIcon，无图片依赖</li>
 * </ul>
 *
 * <h3>配置加载</h3>
 * <ul>
 *   <li>挂载时拉 {@code GET /api/dashboard/map-config}，<b>唯一配置源</b></li>
 *   <li>API 失败 / 关键字段为 null → 显示错误提示，<b>不</b>渲染地图（避免静默用本地默认）</li>
 *   <li>用户提供「重试」按钮，配置恢复后自动初始化</li>
 * </ul>
 *
 * <h3>性能</h3>
 * <ul>
 *   <li>{@code preferCanvas: true} — 走 canvas 渲染，几万 marker 不爆 DOM</li>
 *   <li>{@code chunkedLoading: true} — markercluster 分批 add，不卡主线程</li>
 *   <li>单次请求上限（markerLimit），超过提示用户放大</li>
 *   <li>marker JSON 字段名压成单字符（c/d/n/x/y/s）</li>
 * </ul>
 */
import { onMounted, onBeforeUnmount, ref, shallowRef, reactive, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet.markercluster'
import { getChannelMapMarkersApi } from '@/api/gb28181'
import { getDashboardMapConfigApi, type MapConfig } from '@/api/dashboard'

// 瓦片缺失时的 fallback 图：UI 静态资源（不是地图业务配置），留前端硬编码
// 真正的地图配置（瓦片源 / 中心点 / 缩放 / 坐标系）完全由后端 application.yaml 的 map.* 段下发
const TILE_ERROR_URL = '/noTiles.png'

const emit = defineEmits<{
  (e: 'update', payload: { count: number; totalWithCoord: number; online: number; offline: number; unregistered: number }): void
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const hitLimit = ref(false)
const configLoaded = ref(false)
const configError = ref<string | null>(null)  // 后端配置拉取 / 校验失败时的错误信息
const tileError = ref<string | null>(null)  // 瓦片拉不到时给用户提示
const lastErrorUrl = ref('')  // 上一次报错的 URL，用于去重（不同 URL 替换显示）
const currentZoom = ref(0)  // 跟地图 zoom 事件绑定，initMap 后才有意义

// 当前生效的地图配置——全部由后端填充，前端不再提供兜底默认
// 字段类型保留非 nullable（让既有调用点不报错），loadConfig 校验通过后才视为有效
const cfg = reactive({
  center: [0, 0] as [number, number],
  zoom: 0,
  minZoom: 0,
  maxZoom: 0,
  tileUrl: '',
  tileSubdomains: '',
  disableClusteringAtZoom: 0,
  fetchDebounceMs: 0,
  markerLimit: 0,
  coordSystem: '',  // WGS84 / GCJ02 / BD09，与瓦片源对齐
  tileMode: undefined as string | undefined,  // offline / online
})

/**
 * 校验后端配置是否完整：所有关键字段都非 null 才视为可用。
 * 任何关键字段缺失就 reject，让前端显示错误而不是用部分配置渲染。
 */
function isValidConfig(c: MapConfig | null | undefined): c is MapConfig & {
  centerLng: number
  centerLat: number
  zoom: number
  minZoom: number
  maxZoom: number
  tileUrl: string
  coordSystem: string
  markerLimit: number
  fetchDebounceMs: number
  disableClusteringAtZoom: number
} {
  if (!c) return false
  return c.centerLng != null
    && c.centerLat != null
    && c.zoom != null
    && c.minZoom != null
    && c.maxZoom != null
    && c.tileUrl != null
    && c.coordSystem != null
    && c.markerLimit != null
    && c.fetchDebounceMs != null
    && c.disableClusteringAtZoom != null
}

/**
 * 用后端配置覆盖 cfg：必须 isValidConfig 通过才调用。
 * 删了"后端有值才覆盖 / null 保留"那段——既然走 strict 策略，缺一不可。
 */
function applyServerConfig(server: MapConfig & {
  centerLng: number
  centerLat: number
  zoom: number
  minZoom: number
  maxZoom: number
  tileUrl: string
  coordSystem: string
  markerLimit: number
  fetchDebounceMs: number
  disableClusteringAtZoom: number
}) {
  cfg.center = [server.centerLng, server.centerLat]
  cfg.zoom = server.zoom
  cfg.minZoom = server.minZoom
  cfg.maxZoom = server.maxZoom
  cfg.tileUrl = server.tileUrl
  cfg.tileSubdomains = server.tileSubdomains ?? ''
  cfg.disableClusteringAtZoom = server.disableClusteringAtZoom
  cfg.fetchDebounceMs = server.fetchDebounceMs
  cfg.markerLimit = server.markerLimit
  cfg.coordSystem = server.coordSystem
  cfg.tileMode = server.tileMode ?? undefined
  configLoaded.value = true
  // 原"后端响应到来后重新 setView + invalidateSize"的逻辑不再需要——
  // 新流程是 loadConfig 完成才 initMap，cfg 从一开始就是后端值。
}

// 用 shallowRef 防止 Leaflet 内部对象被 Vue 深度代理（性能+正确性）
const mapRef = shallowRef<L.Map | null>(null)
const tileLayerRef = shallowRef<L.TileLayer | null>(null)
const clusterRef = shallowRef<L.MarkerClusterGroup | null>(null)
const centerMarkerRef = shallowRef<L.Marker | null>(null)

let debounceTimer: number | null = null

// SVG 文件内容缓存：fetch + 去 DOCTYPE 后 inline 渲染（最稳）
// key = status (1/0/-1)，value = 清洗后的 SVG 字符串
const svgCache: Record<number, string> = {}

async function preloadSvgs() {
  const items: Array<[number, string]> = [
    [1, '/assets/online.svg'],
    [0, '/assets/offline.svg'],
    [-1, '/assets/not-detected.svg'],
  ]
  await Promise.all(items.map(async ([status, url]) => {
    try {
      const res = await fetch(url)
      let text = await res.text()
      // 去 xml 声明和 DOCTYPE：浏览器把 SVG 当 HTML 渲染时，xml 头会触发解析错误
      text = text.replace(/<\?xml[^?]*\?>/, '').replace(/<!DOCTYPE[^>]*>/, '')
      svgCache[status] = text
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error('[地图] 预加载 SVG 失败', url, e)
    }
  }))
}

function iconFor(status: 1 | 0 | -1): L.DivIcon {
  const svg = svgCache[status] || ''
  return L.divIcon({
    className: 'map-marker',
    html: `<div class="map-marker-svg">${svg}</div>`,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  })
}

/**
 * 中心点标识：一个 14px 红点 + 白边，最朴素但行业通用。
 * 不参与交互（点击穿透到下面的通道 marker）。
 */
function buildCenterIcon(): L.DivIcon {
  return L.divIcon({
    className: 'cm-icon',
    html: '<span class="cm-dot"></span>',
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  })
}

function addOrUpdateCenterMarker() {
  const map = mapRef.value
  if (!map) return
  // cfg.center 是 [lng, lat]，Leaflet 要 [lat, lng]
  const latLng: [number, number] = [cfg.center[1], cfg.center[0]]
  if (centerMarkerRef.value) {
    centerMarkerRef.value.setLatLng(latLng)
  } else {
    const marker = L.marker(latLng, { icon: buildCenterIcon(), interactive: false, zIndexOffset: 1000 })
    marker.addTo(map)
    centerMarkerRef.value = marker
  }
}

async function fetchAndRender() {
  const map = mapRef.value
  const cluster = clusterRef.value
  if (!map || !cluster) return
  const b = map.getBounds()
  const west = b.getWest()
  const east = b.getEast()
  if (west > east) {
    // 跨 180° 经线：拆两块请求
    const [a, c] = await Promise.all([
      getChannelMapMarkersApi({ minLng: west, minLat: b.getSouth(), maxLng: 180, maxLat: b.getNorth() }),
      getChannelMapMarkersApi({ minLng: -180, minLat: b.getSouth(), maxLng: east, maxLat: b.getNorth() }),
    ])
    renderMarkers([...a.data.markers, ...c.data.markers], a.data.count + c.data.count,
      a.data.totalWithCoord || c.data.totalWithCoord)
  } else {
    const { data } = await getChannelMapMarkersApi({ minLng: west, minLat: b.getSouth(), maxLng: east, maxLat: b.getNorth() })
    renderMarkers(data.markers, data.count, data.totalWithCoord)
  }
}

function renderMarkers(
  markers: Array<{ c: string; d: string; n: string; x: number; y: number; s: 1 | 0 | -1 }>,
  count: number,
  totalWithCoord: number,
) {
  const cluster = clusterRef.value
  if (!cluster) return
  cluster.clearLayers()
  const layers: L.Marker[] = []
  let online = 0, offline = 0, unreg = 0
  for (const m of markers) {
    if (m.s === 1) online++
    else if (m.s === -1) unreg++
    else offline++
    const marker = L.marker([m.y, m.x], { icon: iconFor(m.s) })
      .bindPopup(`<div class="map-popup"><div class="map-popup-name">${escapeHtml(m.n || m.c)}</div><div class="map-popup-id">${escapeHtml(m.c)}</div></div>`)
    layers.push(marker)
  }
  cluster.addLayers(layers)
  hitLimit.value = count >= cfg.markerLimit
  emit('update', { count, totalWithCoord, online, offline, unregistered: unreg })
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!))
}

function onMoveEnd() {
  if (debounceTimer) window.clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(async () => {
    loading.value = true
    try {
      await fetchAndRender()
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error('[地图] 拉取 marker 失败', e)
    } finally {
      loading.value = false
    }
  }, cfg.fetchDebounceMs)
}

function initMap() {
  if (!containerRef.value) return
  if (!configLoaded.value) return  // 严格走 API：后端配置没到位就不渲染地图
  // cfg.center 是 [lng, lat]（与后端 application.yaml、Java MapProperties 一致），
  // Leaflet 的 setView 要的是 [lat, lng]，所以这里要 swap 一下。
  const map = L.map(containerRef.value, {
    preferCanvas: true,
    minZoom: cfg.minZoom,
    maxZoom: cfg.maxZoom,
    zoomControl: true,
    attributionControl: false,
  }).setView([cfg.center[1], cfg.center[0]], cfg.zoom)

  const tileLayer = L.tileLayer(cfg.tileUrl, {
    subdomains: cfg.tileSubdomains,
    maxZoom: cfg.maxZoom,
    minZoom: cfg.minZoom,
    errorTileUrl: TILE_ERROR_URL,  // 瓦片缺失时 fallback，UI 静态资源
  })
    .on('tileerror', (e: any) => {
      // 瓦片 4xx/5xx/网络错误时触发；同一瓦片可能多次触发，按 URL 维度去重
      const url = e?.tile?.src || ''
      // 第一次错误显示完整 URL，后续同 URL 错误只在 console 刷；不同 URL 替换显示
      if (url && url === lastErrorUrl.value) return
      lastErrorUrl.value = url
      tileError.value = `瓦片加载失败：${url || '未知 URL'}（检查网络 / tileUrl 是否可达）`
      // eslint-disable-next-line no-console
      console.error('[地图] 瓦片加载失败', url, e)
    })
    .on('tileload', () => {
      // 任意一张瓦片加载成功 → 清错误（哪怕之前有过）
      if (tileError.value) {
        tileError.value = null
        lastErrorUrl.value = ''
      }
    })
    .addTo(map)

  tileLayerRef.value = tileLayer

  const cluster = (L as any).markerClusterGroup({
    chunkedLoading: true,
    chunkInterval: 50,
    chunkDelay: 20,
    disableClusteringAtZoom: cfg.disableClusteringAtZoom,
    maxClusterRadius: 60,
    showCoverageOnHover: false,
    spiderfyOnMaxZoom: false,
  }) as L.MarkerClusterGroup
  map.addLayer(cluster)

  mapRef.value = map
  clusterRef.value = cluster

  // 中心点标识（红色叉丝），跟随 cfg.center 变化
  addOrUpdateCenterMarker()

  map.on('moveend', onMoveEnd)
  map.on('zoomend', onMoveEnd)
  // 缩放过程实时更新等级显示（zoom 事件比 zoomend 触发更频繁，跟手感更好）
  map.on('zoom', () => {
    currentZoom.value = map.getZoom()
  })
  map.whenReady(() => {
    onMoveEnd()
  })
}

async function loadConfig() {
  configError.value = null
  try {
    const { data } = await getDashboardMapConfigApi()
    if (!isValidConfig(data)) {
      configError.value = '后端地图配置不完整（关键字段缺失）'
      // eslint-disable-next-line no-console
      console.error('[地图] 后端配置不完整', data)
      return
    }
    applyServerConfig(data)
  } catch (e) {
    configError.value = '后端地图配置不可达'
    // eslint-disable-next-line no-console
    console.error('[地图] 后端配置拉取失败', e)
  }
}

/**
 * 重试入口：用户点"重新加载配置"按钮触发。
 * 拉取成功后，若地图还没初始化就补一次 initMap。
 */
async function retryLoadConfig() {
  await loadConfig()
  if (configLoaded.value && !mapRef.value) {
    initMap()
    // 跟 onMounted 一样，等父容器布局稳定再 invalidateSize
    await nextTick()
    mapRef.value?.invalidateSize()
  }
}

/**
 * 强制重新加载瓦片：清掉当前 tile layer，销毁 + 重建。
 * 用于用户改完配置后想立刻看到效果，或网络抖动后想重试。
 */
function reloadTiles() {
  const map = mapRef.value
  const layer = tileLayerRef.value
  if (!map || !layer) return
  tileError.value = null
  lastErrorUrl.value = ''
  layer.remove()
  // 重新创建一个 tile layer，URL 来自当前 cfg
  const newLayer = L.tileLayer(cfg.tileUrl, {
    subdomains: cfg.tileSubdomains,
    maxZoom: cfg.maxZoom,
    minZoom: cfg.minZoom,
    errorTileUrl: TILE_ERROR_URL,
  })
    .on('tileerror', (e: any) => {
      const url = e?.tile?.src || ''
      if (url && url === lastErrorUrl.value) return
      lastErrorUrl.value = url
      tileError.value = `瓦片加载失败：${url || '未知 URL'}（检查网络 / tileUrl 是否可达）`
      // eslint-disable-next-line no-console
      console.error('[地图] 瓦片加载失败', url, e)
    })
    .on('tileload', () => {
      if (tileError.value) {
        tileError.value = null
        lastErrorUrl.value = ''
      }
    })
    .addTo(map)
  tileLayerRef.value = newLayer
  // eslint-disable-next-line no-console
  console.info('[地图] 已强制重建 tile layer，URL=', cfg.tileUrl)
}

// 关键修复：后端配置在 initMap 之前就完整（loadConfig 校验通过才 init），
// tile layer URL 一开始就是后端值，不需要再监听 cfg.tileUrl 异步切源。
// （旧版本有这段 watch 是因为"本地默认先 init → 后端响应慢慢覆盖"的二段流程，新流程下永远不会触发。）

onMounted(async () => {
  // 拉后端配置 + 预加载三个 SVG；都 await，确保配置就绪后第一个 marker 渲染时缓存就绪
  await Promise.all([loadConfig(), preloadSvgs()])
  // 后端配置校验通过才初始化地图，否则显示错误提示
  if (configLoaded.value) {
    // 容器必须有确定尺寸，否则 Leaflet 算不出 bounds
    initMap()
    // 关键修复：flex 父容器首屏布局完成后，强制 Leaflet 重算容器尺寸。
    // 解决"上面是灰色、拉倒下面才有地图"——initMap 时容器高度还没稳定，
    // Leaflet 用偏小的高度算 tile pane，导致上半部分露出 panel 的灰色背景。
    await nextTick()
    mapRef.value?.invalidateSize()
  }
})

onBeforeUnmount(() => {
  if (debounceTimer) window.clearTimeout(debounceTimer)
  const map = mapRef.value
  if (map) {
    map.off()
    map.remove()
    mapRef.value = null
  }
  clusterRef.value = null
  centerMarkerRef.value = null
})
</script>

<template>
  <div class="map-panel">
    <div ref="containerRef" class="map-container" />
    <div v-if="configError" class="map-config-error">
      ⚠️ {{ configError }}
      <br />
      <small>后端地图配置是唯一来源，请确认 application.yaml 的 <code>map.*</code> 段已配置且服务可达</small>
      <br />
      <button class="map-reload-btn" @click="retryLoadConfig">重新加载配置</button>
    </div>
    <div v-else-if="!configLoaded" class="map-loading">加载地图配置…</div>
    <div v-else-if="loading" class="map-loading">加载中…</div>
    <div v-if="hitLimit" class="map-tip">当前可视范围内通道超过 {{ cfg.markerLimit }}，请放大查看更多</div>
    <div v-if="tileError" class="map-tile-error">
      ⚠️ {{ tileError }}
      <br />
      <small v-if="cfg.tileMode">
        当前 <b>tile-mode: {{ cfg.tileMode }}</b>：
        <span v-if="cfg.tileMode === 'offline'">检查 <code>public/tiles/{{ '{z}/{x}/{y}' }}.png</code> 是否存在 + 是否覆盖当前视口（瓦片不存在的 zoom/区域会空白）</span>
        <span v-else>检查 <code>tile-url-online</code> 在 application.yaml 配置 + 客户端能否访问（高德/百度公开 URL 在国内常被墙）</span>
      </small>
      <br />
      <button class="map-reload-btn" @click="reloadTiles">重新加载瓦片</button>
    </div>
    <div class="map-legend">
      <span class="legend-item"><i class="legend-dot" style="background:#162ee9" />在线</span>
      <span class="legend-item"><i class="legend-dot" style="background:#d81e06" />离线</span>
      <span class="legend-item"><i class="legend-dot" style="background:#f6ef37" />未注册</span>
    </div>
    <div class="map-zoom">缩放等级：{{ currentZoom }}</div>
  </div>
</template>

<style scoped>
.map-panel {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 480px;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f7fa;
}

.map-container {
  position: absolute;
  inset: 0;
}

.map-loading {
  position: absolute;
  top: 12px;
  right: 12px;
  background: rgba(255, 255, 255, 0.9);
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  z-index: 1000;
  pointer-events: none;
}

.map-config-error {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid var(--el-color-danger);
  color: var(--el-text-color-primary);
  padding: 14px 20px;
  border-radius: 6px;
  font-size: 13px;
  z-index: 1000;
  max-width: 80%;
  text-align: left;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
}
.map-config-error small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  display: block;
  margin-top: 4px;
}

.map-tip {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(245, 108, 108, 0.9);
  color: #fff;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
  z-index: 1000;
  pointer-events: none;
}

.map-tile-error {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid var(--el-color-danger);
  color: var(--el-text-color-primary);
  padding: 14px 20px;
  border-radius: 6px;
  font-size: 13px;
  z-index: 1000;
  max-width: 80%;
  text-align: left;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
}
.map-tile-error small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  display: block;
  margin-top: 4px;
}
.map-reload-btn {
  margin-top: 8px;
  padding: 4px 12px;
  background: var(--el-color-danger);
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}
.map-reload-btn:hover {
  background: #f78989;
}

.map-legend {
  position: absolute;
  bottom: 12px;
  right: 12px;
  background: rgba(255, 255, 255, 0.95);
  padding: 8px 12px;
  border-radius: 4px;
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  z-index: 1000;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 0 2px rgba(0, 0, 0, 0.2);
}

.map-zoom {
  position: absolute;
  bottom: 12px;
  left: 12px;
  background: rgba(255, 255, 255, 0.95);
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  z-index: 1000;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}
</style>

<style>
/* 地图 marker 全局样式（不 scoped，因为 divIcon 渲染到 leaflet pane 内） */
.map-marker {
  background: transparent;
  border: none;
}
.map-marker-svg {
  display: block;
  width: 32px;
  height: 32px;
  pointer-events: auto;
}
.map-marker-svg svg {
  display: block;
  width: 100%;
  height: 100%;
}
.map-popup-name {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  margin-bottom: 2px;
}
.map-popup-id {
  font-size: 11px;
  color: #909399;
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
}

/* 中心点标识：14px 红点 + 白边，不参与交互（点击穿透到下面的通道 marker） */
.cm-icon {
  background: transparent;
  border: none;
}
.cm-dot {
  display: block;
  width: 14px;
  height: 14px;
  background: #f56c6c;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #f56c6c;
}
</style>
