<script setup lang="ts">
/**
 * 首页地图面板
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>Leaflet + 瓦片底图（瓦片源由后端 application.yaml 控制）</li>
 *   <li>视口过滤 + debounce 拉后端拿 marker</li>
 *   <li>客户端 markercluster 聚合（zoom ≥ 阈值自动散开）</li>
 *   <li>三色 CCTV 枪机图（外白环 + 状态色屏面 + 白色镜头），适配暗色底图，识别度比平涂色块高一档</li>
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

// 通道 marker 图标：CCTV 枪机形 SVG（public/monitor/ 下三张静态资源）
// 设计要点：
//   1. 双层结构：外圈白环（halo）把 marker 从暗色底图上"抬"出来，避免跟暗路/深建筑撞色
//   2. 屏面=状态色：在线翠绿 / 离线冷灰 / 未注册亮红，三色互不混淆
//   3. 镜头是状态色挖空：在白色机身上"看穿"镜头，识别度比纯色块高一档
//   4. 底部底座+横杆是 CCTV 标志性细节，比 CRT 显示器形少一层"以为是工位屏"的认知负担
const ICON_ON = '/monitor/green.svg'
const ICON_OFF = '/monitor/gray.svg'
const ICON_UNREGISTERED = '/monitor/red.svg'
// viewBox 100×100，圆形外环 cx=50 cy=50 r=46（占 4~96 宽高），底部基座 y=76.5；
// 显示 40×40；anchor 落在底座正下方（与旧版同值，因为底座 y 位置接近）
const ICON_SIZE: [number, number] = [40, 40]
const ICON_ANCHOR: [number, number] = [20, 30]

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

// clusterclick 触发的 zoom 不应该让 onMoveEnd 重新拉 API（会被新视口过滤掉大量 marker）
// 设 true 后下一次 moveend 跳过 fetch，保留现有 marker
let skipNextFetchAfterClusterClick = false
let skipResetTimer: number | null = null

function iconFor(status: string): L.Icon {
  // 三种状态分别用实物 CCTV 枪机图，外圈白环 + 状态色屏面在暗色底图上一眼可辨
  let url = ICON_OFF
  if (status === 'ON') url = ICON_ON
  else if (status === 'UNREGISTERED') url = ICON_UNREGISTERED
  return L.icon({
    iconUrl: url,
    iconSize: ICON_SIZE,
    iconAnchor: ICON_ANCHOR,
  })
}

/**
 * 聚合气泡的 iconCreateFunction：单个 mini 枪机 + 右下角数量徽章
 * ───────────────────────────────────────────────
 * 跟单个 marker 完全同形态（都是枪机 SVG），只是多了个"未读消息"风格的数量角标
 * 颜色策略：集群内取"主导状态"——未注册 > 离线 > 在线（未注册最该被关注，优先级最高）
 * 99+ 上限：避免徽章被三位数撑变形
 */
function clusterIconCreate(cluster: L.MarkerCluster): L.DivIcon {
  const childMarkers = cluster.getAllChildMarkers()
  const count = childMarkers.length

  let online = 0
  let offline = 0
  let unreg = 0
  for (const m of childMarkers) {
    const status = (m as any).status || 'ON'
    if (status === 'ON') online++
    else if (status === 'UNREGISTERED') unreg++
    else offline++
  }

  // 状态优先级：未注册（异常，最该被看见）> 离线 > 在线
  let dominant: 'ON' | 'OFF' | 'UNREGISTERED' = 'ON'
  if (unreg > 0) dominant = 'UNREGISTERED'
  else if (offline > online) dominant = 'OFF'

  const camFile = dominant === 'ON' ? 'green' : dominant === 'OFF' ? 'gray' : 'red'
  const displayCount = count > 99 ? '99+' : String(count)

  return L.divIcon({
    className: 'cluster-icon',
    html:
      `<div class="cluster-bubble">` +
        `<div class="cluster-cam" style="background-image:url('/monitor/${camFile}.svg')"></div>` +
        `<div class="cluster-count">${displayCount}</div>` +
      `</div>`,
    iconSize: [40, 40],
    iconAnchor: [20, 30],  // 跟单个 marker 同 anchor（[20,30] 是枪机底座位置），集群散开时单点不会"跳"
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

/**
 * 状态文案 + 颜色（popup 标签条用 + 图例 dot 着色用 + cctv 图标屏面色）
 * 绿=在线 / 红=离线 / 更亮的红=未注册——绿/红两色分在线/非在线，红的两档分紧急程度
 * 离线比未注册暗一档（#F44336 vs #FF5252）：未注册是"该有但没注册"更"警"，用更亮红
 */
function statusMeta(status: string): { label: string; color: string; code: 1 | 0 | -1 } {
  if (status === 'ON') return { label: '在线', color: '#00E676', code: 1 }  // 翠绿
  if (status === 'UNREGISTERED') return { label: '未注册', color: '#FF5252', code: -1 }  // 亮红（更"警"）
  return { label: '离线', color: '#F44336', code: 0 }  // 中红（次一档）
}

/**
 * 把数字按 6 位小数输出（lng/lat 精度到厘米级够用），null/0 显示 ——
 */
function fmtCoord(v: number | null | undefined): string {
  if (v == null || v === 0) return '—'
  return v.toFixed(6)
}

function renderMarkers(
  markers: Array<{
    channelId: string
    deviceId: string
    name: string
    longitude: number
    latitude: number
    longitudeWgs84: number
    latitudeWgs84: number
    longitudeGcj02: number | null
    latitudeGcj02: number | null
    longitudeBd09: number | null
    latitudeBd09: number | null
    status: string
  }>,
  count: number,
  totalWithCoord: number,
) {
  const cluster = clusterRef.value
  if (!cluster) return
  cluster.clearLayers()
  const layers: L.Marker[] = []
  let online = 0, offline = 0, unreg = 0
  for (const m of markers) {
    const st = statusMeta(m.status)
    if (st.code === 1) online++
    else if (st.code === -1) unreg++
    else offline++
    const popupHtml =
      `<div class="map-popup">` +
        `<div class="map-popup-header">` +
          `<span class="map-popup-name">${escapeHtml(m.name || m.channelId)}</span>` +
          `<span class="map-popup-status" style="background:${st.color}">${st.label}</span>` +
        `</div>` +
        `<div class="map-popup-id">${escapeHtml(m.channelId)}</div>` +
        `<div class="map-popup-coords">` +
          `<div class="map-popup-coord-row"><span class="map-popup-coord-label">WGS-84</span><span>${fmtCoord(m.longitudeWgs84)}, ${fmtCoord(m.latitudeWgs84)}</span></div>` +
          `<div class="map-popup-coord-row"><span class="map-popup-coord-label">GCJ-02</span><span>${fmtCoord(m.longitudeGcj02)}, ${fmtCoord(m.latitudeGcj02)}</span></div>` +
          `<div class="map-popup-coord-row"><span class="map-popup-coord-label">BD-09</span><span>${fmtCoord(m.longitudeBd09)}, ${fmtCoord(m.latitudeBd09)}</span></div>` +
        `</div>` +
      `</div>`
    const marker = L.marker([m.latitude, m.longitude], { icon: iconFor(m.status) })
    // 自定义属性挂 status：markercluster 聚合时不带业务字段，靠这个读出来生成聚合 icon
    ;(marker as any).status = m.status
    marker.bindPopup(popupHtml)
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
    // clusterclick 触发的 zoom 不重拉：保留地图上现有 marker，让 cluster 自己散开
    if (skipNextFetchAfterClusterClick) {
      skipNextFetchAfterClusterClick = false
      return
    }
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
  // 首屏同步：Leaflet 的 zoom 事件只在用户缩放时触发，setView 不触发，
  // 不主动同步会导致缩放等级显示一直停在初值 0，直到用户滚轮缩放一次才刷新。
  currentZoom.value = map.getZoom()

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

  // 蓝系大屏色覆盖：tiles 之上、markers 之下塞一个蓝色渐变 div
  // 只靠 .leaflet-tile-pane 的 invert+hue-rotate 偏 15° 推不动"真蓝色"——加一层半透蓝渐变才像大屏
  // 用 map.createPane 创建独立 pane，z-index 450 卡在 tilePane (200) 和 markerPane (600) 之间
  // （shadowPane 500 在它上面，marker 的 drop-shadow 还能透下来，不影响相机识别度）
  const tintPane = map.createPane('tint-pane')
  tintPane.style.zIndex = '450'
  tintPane.style.pointerEvents = 'none'
  L.DomUtil.create('div', 'map-blue-tint', tintPane)

  // 聚合 marker：用 clusterIconCreate 自定义 iconCreateFunction，
  // 单个 mini 枪机 + 右下角数量徽章，跟单个 marker（枪机）形态对齐——不再用默认"圆+数字"
  // spiderfyOnMaxZoom: true —— max zoom（disableClusteringAtZoom=21）时点 cluster 改扇形展开所有 marker，
  //   避免"同点位挤一堆"（如 82 个摄像头都在同一栋楼）点开后只看到 1 个
  const cluster = (L as any).markerClusterGroup({
    chunkedLoading: true,
    chunkInterval: 50,
    chunkDelay: 20,
    disableClusteringAtZoom: cfg.disableClusteringAtZoom,
    maxClusterRadius: 60,
    showCoverageOnHover: false,
    spiderfyOnMaxZoom: true,
    iconCreateFunction: clusterIconCreate,
  }) as L.MarkerClusterGroup

  // 关键修复：点 cluster 触发的 zoom 不让 onMoveEnd 重新拉 API
  // 原因：fetchAndRender 拿新视口去查后端，后端按视口过滤 marker，82 个里有 81 个被当成"出框"过滤掉
  // 解决：跳过这一次 fetch，让现有 82 个 marker 留在地图上，cluster 自然散开
  cluster.on('clusterclick', () => {
    skipNextFetchAfterClusterClick = true
    if (skipResetTimer) window.clearTimeout(skipResetTimer)
    // 兜底：1 秒内没 moveend（例如 max zoom 时被 spiderfy 替代，map 没动），
    // 主动重置 flag，避免影响用户后续手动 pan/zoom
    skipResetTimer = window.setTimeout(() => {
      skipNextFetchAfterClusterClick = false
    }, 1000)
  })
  map.addLayer(cluster)

  mapRef.value = map
  clusterRef.value = cluster

  // 中心点标识（黄色锚点），跟随 cfg.center 变化
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
  // 拉后端配置；水滴 pin 图标在模块加载时已预渲染完（PIN_SVG），不需要再 await
  await loadConfig()
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
      <span class="legend-item"><i class="legend-dot" :style="{ background: statusMeta('ON').color }" />在线</span>
      <span class="legend-item"><i class="legend-dot" :style="{ background: statusMeta('OFF').color }" />离线</span>
      <span class="legend-item"><i class="legend-dot" :style="{ background: statusMeta('UNREGISTERED').color }" />未注册</span>
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
  background: #0A1929;  /* 深海蓝（主流大屏底色），瓦片加载前的兜底色 */
}

.map-container {
  position: absolute;
  inset: 0;
}

.map-loading {
  position: absolute;
  top: 12px;
  right: 12px;
  background: rgba(10, 25, 41, 0.88);
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  z-index: 1000;
  pointer-events: none;
}

.map-config-error {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(20, 26, 38, 0.95);
  border: 1px solid var(--el-color-danger);
  color: var(--el-text-color-regular);
  padding: 14px 20px;
  border-radius: 6px;
  font-size: 13px;
  z-index: 1000;
  max-width: 80%;
  text-align: left;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.5);
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
  background: rgba(10, 25, 41, 0.95);
  border: 1px solid rgba(0, 188, 212, 0.3);  /* 电光蓝描边 */
  color: var(--el-text-color-regular);
  padding: 14px 20px;
  border-radius: 6px;
  font-size: 13px;
  z-index: 1000;
  max-width: 80%;
  text-align: left;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.5);
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
  background: rgba(10, 25, 41, 0.88);
  border: 1px solid rgba(0, 188, 212, 0.2);  /* 电光蓝描边 */
  padding: 8px 12px;
  border-radius: 4px;
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  z-index: 1000;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4), 0 0 10px rgba(0, 188, 212, 0.06);
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
  border: 2px solid #00E5FF;  /* 电光蓝描边 */
  box-shadow: 0 0 6px rgba(0, 229, 255, 0.4);  /* 电光蓝微光晕 */
}

.map-zoom {
  position: absolute;
  bottom: 12px;
  left: 12px;
  background: rgba(10, 25, 41, 0.88);
  border: 1px solid rgba(0, 188, 212, 0.2);
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  z-index: 1000;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4), 0 0 10px rgba(0, 188, 212, 0.06);
}
</style>

<style>
/* 地图 marker 全局样式（不 scoped，因为 divIcon 渲染到 leaflet pane 内） */

/* AMap style=8 浅色瓦片 → 暗色大屏底图：
   - invert(1) 把白底翻成黑底
   - hue-rotate(180deg) 标准反相（190° 那种会偏色，让水域/公园/建筑统一往蓝偏）
   - brightness(0.9) contrast(0.95) 微调
   只套 .leaflet-tile-pane，marker / popup / 控件不被波及（颜色独立保留）
   蓝色调主要由下面 .map-blue-tint 半透渐变提供 */
.leaflet-tile-pane {
  filter: invert(1) hue-rotate(180deg) brightness(0.9) contrast(0.95);
}

/* 蓝系大屏色覆盖：tiles 之上 markers 之下塞一个蓝色渐变 div
   只靠 tile filter 的 hue-rotate 推不动"真蓝色"——加一层半透蓝渐变才像大屏
   渐变方向：上浅下深（"天 → 深蓝"），呼应主流监控大屏的纵向色阶
   强度可调：0.18 / 0.40 是中等强度，再大就开始遮瓦片了 */
.map-blue-tint {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 130, 230, 0.18) 0%, rgba(0, 40, 120, 0.40) 100%);
  pointer-events: none;
}

/* Leaflet 缩放控件（默认白底）→ 深海蓝面板 + 电光蓝描边，跟大屏统一 */
.leaflet-control-zoom {
  border: 1px solid rgba(0, 188, 212, 0.3) !important;
  background: rgba(10, 25, 41, 0.92) !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4), 0 0 10px rgba(0, 188, 212, 0.08) !important;
}
.leaflet-control-zoom a {
  background: transparent !important;
  color: #E0F7FA !important;
  border-bottom: 1px solid rgba(0, 188, 212, 0.2) !important;
}
.leaflet-control-zoom a:last-child {
  border-bottom: none !important;
}
.leaflet-control-zoom a:hover {
  background: rgba(0, 188, 212, 0.15) !important;  /* hover 时电光蓝高亮 */
}

/* CCTV 枪机 marker：外圈白环已在 SVG 内部实现，这里只加暗色 drop-shadow
   撑出立体感即可（删掉旧版两层白色 drop-shadow——白环在白底图上才有意义，
   暗色底图上反而会让 marker 边缘糊掉） */
.leaflet-marker-icon {
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.6));
}
/* 聚合气泡：单个 mini 枪机 + 右下角数量徽章
   ───────────────────────────────────────────────
   跟单个 marker 完全同形态（都是 40x40 枪机 SVG），只多了个"未读消息"风格的数量角标
   为什么不再用 leaflet.markercluster 默认的"圆+数字"：
     - 圆气泡跟单 marker 形态脱钩，一眼看不出是"摄像头集群"
     - 数字没有视觉锚点，不知道指的是什么
   设计：
     - 容器 40x40，iconAnchor [20,30]（同单 marker，集群散开时单点不"跳"）
     - mini 枪机：复用 public/monitor/*.svg 作 background-image，状态色由集群主导状态决定
     - 数量徽章：右下角"未读消息"风格（电光蓝底 + 深海蓝字 + 微光晕），99+ 上限 */
.cluster-icon {
  background: transparent !important;
  border: none !important;
}
.cluster-bubble {
  position: relative;
  width: 40px;
  height: 40px;
}
.cluster-cam {
  width: 40px;
  height: 40px;
  background-repeat: no-repeat;
  background-position: center;
  background-size: contain;
  filter: drop-shadow(0 2px 3px rgba(0, 0, 0, 0.6));
}
.cluster-count {
  position: absolute;
  right: -3px;
  bottom: -3px;
  background: #00E5FF;
  color: #0A1929;
  border: 1.5px solid #0A1929;
  border-radius: 10px;
  padding: 0 5px;
  font-size: 10px;
  font-weight: 700;
  line-height: 14px;
  min-width: 16px;
  text-align: center;
  box-shadow: 0 0 6px rgba(0, 229, 255, 0.5);
  white-space: nowrap;
}

/* Leaflet popup 弹窗：深海蓝面板 + 电光蓝描边 + 冷白字，跟大屏统一 */
.leaflet-popup-content-wrapper {
  background: #0A1929;
  color: #E0F7FA;
  border: 1px solid rgba(0, 188, 212, 0.3);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5), 0 0 12px rgba(0, 188, 212, 0.1);
  border-radius: 6px;
}
.leaflet-popup-tip {
  background: #0A1929;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
}
.leaflet-popup-close-button {
  color: #80DEEA;
}
.leaflet-popup-close-button:hover {
  color: #00E5FF;
}

.map-popup-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.map-popup-name {
  font-weight: 600;
  font-size: 13px;
  color: #E0F7FA;  /* 冷白 */
}
.map-popup-status {
  display: inline-block;
  font-size: 11px;
  color: #fff;
  padding: 1px 6px;
  border-radius: 8px;
  line-height: 1.4;
  text-shadow: 0 0 3px rgba(0, 0, 0, 0.5);  /* 状态色都是高饱和实色，加强阴影让白字在 #00E676 / #FF5252 / #F44336 上都清晰可读 */
}
.map-popup-id {
  font-size: 11px;
  color: #80DEEA;  /* 浅电光蓝，channelId 走数据点视觉 */
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  margin-bottom: 6px;
}
.map-popup-coords {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  color: #B2EBF2;
  border-top: 1px dashed rgba(0, 188, 212, 0.3);  /* 电光蓝虚线分隔 */
  padding-top: 4px;
}
.map-popup-coord-row {
  display: flex;
  gap: 8px;
  align-items: baseline;
}
.map-popup-coord-label {
  display: inline-block;
  min-width: 56px;
  color: #4DD0E1;  /* 强电光蓝，坐标系标签 */
  font-weight: 600;
}

/* 中心点标识：14px 电光蓝锚点 + 白边 + 微光晕
   用电光蓝而不用红/绿/蓝灰/黄：跟"在线/离线/未注册"状态色全错开，
   语义上"锚点"独立；同时跟大屏底色形成强对比（深蓝底上电光蓝最跳） */
.cm-icon {
  background: transparent;
  border: none;
}
.cm-dot {
  display: block;
  width: 14px;
  height: 14px;
  background: #00E5FF;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #00E5FF, 0 0 8px rgba(0, 229, 255, 0.6);  /* 外圈电光蓝 + 微光晕 */
}
</style>
