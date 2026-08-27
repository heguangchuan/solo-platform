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
import { wgs84ToTarget } from '@/utils/coordTransform'

const props = defineProps<{
  visible: boolean
  longitude?: number
  latitude?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'select', payload: { longitude: number; latitude: number; coordSystem: string }): void
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const mapRef = ref<L.Map | null>(null)
const markerRef = ref<L.Marker | null>(null)
const currentLng = ref<number | null>(null)
const currentLat = ref<number | null>(null)
// 瓦片坐标系（与 map-config 接口的 coordSystem 同步）：决定选点提交 / 编辑反显的转换方向
// 后端配置就绪后才赋值，initMap 之前保持空串
const coordSystemRef = ref<string>('')
// 后端配置拉取 / 校验失败时的错误信息
const configError = ref<string | null>(null)

/**
 * 校验后端配置是否完整：所有关键字段都非 null 才视为可用。
 * 跟 MapPanel.vue 同款判断——严格走 API，缺一不可。
 */
function isValidConfig(c: MapConfig | null | undefined): c is MapConfig & {
  centerLng: number
  centerLat: number
  zoom: number
  minZoom: number
  maxZoom: number
  tileUrl: string
  coordSystem: string
} {
  if (!c) return false
  return c.centerLng != null
    && c.centerLat != null
    && c.zoom != null
    && c.minZoom != null
    && c.maxZoom != null
    && c.tileUrl != null
    && c.coordSystem != null
}

/**
 * 初始化地图：纯后端驱动。后端失败 / 字段缺失时设 configError，地图区显示错误提示。
 * cfg.center 是 [lng, lat]，Leaflet 要 [lat, lng]，swap。
 */
async function initMap() {
  if (!containerRef.value) return
  configError.value = null
  let cfg: {
    center: [number, number]
    zoom: number
    minZoom: number
    maxZoom: number
    tileUrl: string
    tileSubdomains: string
  }
  try {
    const { data } = await getDashboardMapConfigApi()
    if (!isValidConfig(data)) {
      configError.value = '后端地图配置不完整（关键字段缺失），无法取点'
      // eslint-disable-next-line no-console
      console.error('[取点] 后端配置不完整', data)
      return
    }
    cfg = {
      center: [data.centerLng, data.centerLat],
      zoom: data.zoom,
      minZoom: data.minZoom,
      maxZoom: data.maxZoom,
      tileUrl: data.tileUrl,
      tileSubdomains: data.tileSubdomains ?? '',
    }
    coordSystemRef.value = data.coordSystem
  } catch (e) {
    configError.value = '后端地图配置不可达，无法取点'
    // eslint-disable-next-line no-console
    console.error('[取点] 后端配置拉取失败', e)
    return
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

  // 已有经纬度 → 居中 + 回显 marker
  // props.longitude/latitude 是 WGS-84（后端 DB canonical），瓦片系是 coordSystemRef
  // 反显前要 WGS-84 → 瓦片系，否则 marker 会落在瓦片上 ~50-100m 偏移的位置
  if (props.longitude != null && props.latitude != null) {
    const [tileLng, tileLat] = wgs84ToTarget(props.longitude, props.latitude, coordSystemRef.value)
    setMarker(tileLng, tileLat)
    map.setView([tileLat, tileLng], cfg.zoom)
  }
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
  // 把瓦片系原始坐标 + coordSystem 发出去，后端负责转 WGS-84 存三套
  // （前端不再做转换，picker 怎么看到就怎么发）
  emit('select', {
    longitude: currentLng.value,
    latitude: currentLat.value,
    coordSystem: coordSystemRef.value,
  })
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
          // props 是 WGS-84，瓦片系是 coordSystemRef，反显要按当前瓦片系转换
          const [tileLng, tileLat] = wgs84ToTarget(props.longitude, props.latitude, coordSystemRef.value)
          setMarker(tileLng, tileLat)
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
      <div v-if="configError" class="picker-error">
        ⚠️ {{ configError }}
        <br />
        <small>后端地图配置是唯一来源，请确认 application.yaml 的 <code>map.*</code> 段已配置且服务可达</small>
      </div>
      <div v-show="!configError" ref="containerRef" class="picker-map" />
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

.picker-error {
  width: 100%;
  height: 480px;
  border-radius: 4px;
  border: 1px solid var(--el-color-danger);
  background: rgba(254, 240, 240, 0.6);
  color: var(--el-color-danger);
  padding: 24px;
  font-size: 13px;
  line-height: 1.6;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  box-sizing: border-box;
}
.picker-error small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 8px;
  display: block;
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
