<script setup lang="ts">
/**
 * 地图取点对话框
 *
 * <p>点击 / 拖拽地图上的 marker → 实时回显经纬度 → 确定后通过 {@code select} 事件回传。
 * 复用首页的 map-config 接口（瓦片源 / 缩放 / 中心点都跟 dashboard 对齐）。</p>
 *
 * <h3>使用</h3>
 * <pre>
 *   &lt;MapPickerDialog
 *     v-model:visible="pickerVisible"
 *     :longitude="form.longitude"
 *     :latitude="form.latitude"
 *     @select="onPick"
 *   /&gt;
 * </pre>
 */
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import L from 'leaflet'
import { getDashboardMapConfigApi, type MapConfig } from '@/api/dashboard'
import { mapConfig as staticDefault } from '@/config/map'

const props = defineProps<{
  visible: boolean
  longitude?: number
  latitude?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'select', payload: { longitude: number; latitude: number }): void
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const mapRef = ref<L.Map | null>(null)
const markerRef = ref<L.Marker | null>(null)
const currentLng = ref<number | null>(null)
const currentLat = ref<number | null>(null)

/**
 * 初始化地图：拉后端 map-config 覆盖本地默认，没拉到的字段用 staticDefault 兜底。
 * cfg.center 是 [lng, lat]，Leaflet 要 [lat, lng]，swap。
 */
async function initMap() {
  if (!containerRef.value) return
  let cfg = {
    center: [...staticDefault.center] as [number, number],
    zoom: staticDefault.zoom,
    minZoom: staticDefault.minZoom,
    maxZoom: staticDefault.maxZoom,
    tileUrl: staticDefault.tileUrl,
    tileSubdomains: staticDefault.subdomains,
  }
  try {
    const { data } = await getDashboardMapConfigApi()
    applyServerCfg(data, cfg)
  } catch {
    // 后端不可达就用 staticDefault
  }

  const map = L.map(containerRef.value, {
    minZoom: cfg.minZoom,
    maxZoom: cfg.maxZoom,
    zoomControl: true,
    attributionControl: false,
  }).setView([cfg.center[1], cfg.center[0]], cfg.zoom)

  L.tileLayer(cfg.tileUrl, {
    subdomains: cfg.tileSubdomains,
    maxZoom: cfg.maxZoom,
    minZoom: cfg.minZoom,
  }).addTo(map)

  // 点击地图 = 在该位置落 marker
  map.on('click', (e: L.LeafletMouseEvent) => {
    setMarker(e.latlng.lng, e.latlng.lat)
  })

  mapRef.value = map

  // 已有经纬度 → 居中 + 回显 marker，缩放等级用 cfg.zoom（来自 map-config 接口，
  // 即 application.yaml 的 map.default-zoom；不要在这里强制放大到 16 ——
  // 配置说多少就是多少，改配置就能改取点时的默认视图）
  if (props.longitude != null && props.latitude != null) {
    setMarker(props.longitude, props.latitude)
    map.setView([props.latitude, props.longitude], cfg.zoom)
  }
}

function applyServerCfg(server: MapConfig, cfg: {
  center: [number, number]
  zoom: number
  minZoom: number
  maxZoom: number
  tileUrl: string
  tileSubdomains: string
}) {
  if (server.centerLng != null) cfg.center[0] = server.centerLng
  if (server.centerLat != null) cfg.center[1] = server.centerLat
  if (server.zoom != null) cfg.zoom = server.zoom
  if (server.minZoom != null) cfg.minZoom = server.minZoom
  if (server.maxZoom != null) cfg.maxZoom = server.maxZoom
  if (server.tileUrl) cfg.tileUrl = server.tileUrl
  if (server.tileSubdomains) cfg.tileSubdomains = server.tileSubdomains
}

function setMarker(lng: number, lat: number) {
  currentLng.value = lng
  currentLat.value = lat
  if (!mapRef.value) return
  if (markerRef.value) {
    markerRef.value.setLatLng([lat, lng])
  } else {
    const marker = L.marker([lat, lng], { draggable: true })
    marker.on('dragend', () => {
      const p = marker.getLatLng()
      currentLng.value = p.lng
      currentLat.value = p.lat
    })
    marker.addTo(mapRef.value)
    markerRef.value = marker
  }
}

function handleConfirm() {
  if (currentLng.value == null || currentLat.value == null) return
  emit('select', { longitude: currentLng.value, latitude: currentLat.value })
  emit('update:visible', false)
}

function handleClose() {
  emit('update:visible', false)
}

function destroyMap() {
  if (mapRef.value) {
    mapRef.value.remove()
    mapRef.value = null
    markerRef.value = null
  }
}

watch(
  () => props.visible,
  async (v) => {
    if (v) {
      await nextTick()
      if (!mapRef.value) {
        await initMap()
      } else {
        // dialog 关闭后重开：容器尺寸可能变了，强制重算
        mapRef.value.invalidateSize()
        if (props.longitude != null && props.latitude != null) {
          setMarker(props.longitude, props.latitude)
        }
      }
    }
  },
)

onBeforeUnmount(destroyMap)
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="地图取点"
    width="800px"
    top="8vh"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="picker-wrap">
      <div ref="containerRef" class="picker-map" />
      <div class="picker-coords">
        <span>经度：<b>{{ currentLng != null ? currentLng.toFixed(6) : '—' }}</b></span>
        <span>纬度：<b>{{ currentLat != null ? currentLat.toFixed(6) : '—' }}</b></span>
        <span class="picker-hint">点击地图或拖动 marker 取点</span>
      </div>
    </div>
    <template #footer>
      <el-button @click="handleClose">取 消</el-button>
      <el-button type="primary" :disabled="currentLng == null" @click="handleConfirm">确 定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.picker-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.picker-map {
  width: 100%;
  height: 480px;
  border-radius: 4px;
  border: 1px solid var(--el-border-color);
}
.picker-coords {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
}
.picker-coords b {
  color: var(--el-color-primary);
  font-weight: 600;
}
.picker-hint {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-family: inherit;
}
</style>
