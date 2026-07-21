<script setup lang="ts">
/**
 * Jessibuca 播放器封装组件
 *
 * <h3>UI 结构</h3>
 * <pre>
 *   ┌─────────────────────────────────────────────────┐
 *   │  画面（canvas）                                  │
 *   │                                                 │
 *   │                              [信息浮层 - 可选]   │
 *   └─────────────────────────────────────────────────┘
 *   ┌─ 工具栏 ─────────────────────────────────────────┐
 *   │ 左：分辨率/帧率/码率/网速                         │
 *   │ 右：码流 · 收流 · 缩放 · 播放暂停 · 静音 · 截图   │
 *   │     刷新 · 信息 · 全屏                          │
 *   └─────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>事件</h3>
 * <ul>
 *   <li>{@code playing/pause/error/timeout} — 内部状态</li>
 *   <li>{@code switch-stream} — 子码流切换（主/辅）— 业务层处理</li>
 *   <li>{@code switch-transport} — UDP/TCP 切换 — 业务层处理</li>
 *   <li>{@code refresh} — 用户点了"刷新"，业务层重新 start API</li>
 * </ul>
 *
 * <p>截图、录制、暂停/播放、全屏、缩放、静音都是播放器自带能力，
 * 通过 jessibuca API 直接驱动，不向外抛事件。</p>
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  VideoPause,
  VideoPlay,
  Camera,
  FullScreen,
  Refresh,
  InfoFilled,
  Mute,
  Microphone,
  ZoomIn,
  ZoomOut,
  Aim,
  Switch,
  Connection,
} from '@element-plus/icons-vue'
import type { JessibucaInstance } from '@/types/jessibuca'

const props = withDefaults(
  defineProps<{
    /** 是否需要音频解码 */
    hasAudio?: boolean
    /** 容器高度（默认 360px） */
    height?: string
    /** 当前子码流（业务层维护，传给 toolbar 显示） */
    streamType?: 'main' | 'sub'
    /** 当前收流模式（业务层维护，传给 toolbar 显示） */
    transport?: 'UDP' | 'TCP-passive'
  }>(),
  { hasAudio: true, height: '420px', streamType: 'main', transport: 'UDP' },
)

const emit = defineEmits<{
  playing: []
  pause: []
  error: [msg: string]
  timeout: []
  streamEnd: []
  'switch-stream': [type: 'main' | 'sub']
  'switch-transport': [t: 'UDP' | 'TCP-passive']
  refresh: []
}>()

const containerRef = ref<HTMLDivElement | null>(null)
let player: JessibucaInstance | null = null
let pendingUrl: string | null = null

// ============ 状态 ============
const isPlaying = ref(false)
const isMuted = ref(true) // jessibuca 默认静音以满足浏览器自动播放策略
const isFullscreen = ref(false)
const showInfoPanel = ref(false)
const videoInfo = ref<{ width: number; height: number } | null>(null)
const audioInfo = ref<{ numOfChannels: number; sampleRate: number } | null>(null)
const kBps = ref(0)
const playFps = ref(0)

// 电子放大：用 CSS transform 缩放 + 平移整个画面容器
const zoomScale = ref(1) // 1 = 原始；范围 [1, MAX_ZOOM]
const zoomX = ref(0) // px，平移偏移
const zoomY = ref(0)
const isDragging = ref(false)
const MIN_ZOOM = 1
const MAX_ZOOM = 8
const ZOOM_STEP = 0.25
const isZoomed = computed(() => zoomScale.value > 1)
const zoomTransform = computed(() => `translate(${zoomX.value}px, ${zoomY.value}px) scale(${zoomScale.value})`)
const zoomLabel = computed(() => `${Math.round(zoomScale.value * 100)}%`)

const resolutionText = computed(() =>
  videoInfo.value ? `${videoInfo.value.width}×${videoInfo.value.height}` : '—',
)
const bandwidthText = computed(() => (kBps.value > 0 ? `${kBps.value} KB/s` : '—'))
const fpsText = computed(() => (playFps.value > 0 ? `${playFps.value} fps` : '—'))

// ============ 播放器生命周期 ============

function ensurePlayer() {
  if (player) return player
  if (!containerRef.value) return null
  if (typeof window.Jessibuca !== 'function') {
    emit('error', 'Jessibuca 未加载，请检查 /static/js/jessibuca/jessibuca.js')
    return null
  }
  player = new window.Jessibuca({
    container: containerRef.value,
    videoBuffer: 0,
    isResize: true,
    text: '',
    loadingText: '正在拉流，请稍候...',
    useMSE: false,
    useWCS: false,
    decoder: '/static/js/jessibuca/decoder.js',
    forceNoOffscreen: true,
    hasAudio: props.hasAudio,
    isFlv: false,
    timeout: 10,
    heartTimeout: 5,
    heartTimeoutReplay: true,
    heartTimeoutReplayTimes: 3,
    loadingTimeout: 10,
    loadingTimeoutReplay: true,
    loadingTimeoutReplayTimes: 3,
    wasmDecodeErrorReplay: true,
    // 关掉自带 toolbar，全部由组件自己实现（统一风格、加业务按钮）
    operateBtns: {
      fullscreen: false,
      screenshot: false,
      play: false,
      audio: false,
      recorder: false,
    },
    showBandwidth: false,
  })
  player.on('play', () => {
    isPlaying.value = true
    emit('playing')
  })
  player.on('pause', () => {
    isPlaying.value = false
    emit('pause')
  })
  player.on('error', (e: unknown) => {
    console.error('[jessibuca error]', e)
    emit('error', '播放器错误: ' + String(e))
  })
  // streamEnd 不是硬错误：流被关闭/结束（设备停推、ZLM 关流等）时触发，交业务层做友好提示
  player.on('streamEnd', () => {
    isPlaying.value = false
    emit('streamEnd')
  })
  player.on('timeout', () => emit('timeout'))
  player.on('loadingTimeout', () => emit('timeout'))
  player.on('videoInfo', (data: unknown) => {
    videoInfo.value = data as { width: number; height: number }
  })
  player.on('audioInfo', (data: unknown) => {
    audioInfo.value = data as { numOfChannels: number; sampleRate: number }
  })
  player.on('kBps', (v: unknown) => {
    kBps.value = Math.round(Number(v) || 0)
  })
  player.on('stats', (s: unknown) => {
    const stat = s as { fps?: number }
    if (stat && typeof stat.fps === 'number') playFps.value = stat.fps
  })
  player.on('fullscreen', (flag: unknown) => {
    isFullscreen.value = Boolean(flag)
  })
  player.on('mute', (flag: unknown) => {
    isMuted.value = Boolean(flag)
  })
  return player
}

function play(url: string) {
  pendingUrl = url
  resetMetrics()
  const p = ensurePlayer()
  if (!p) return
  if (p.hasLoaded()) {
    p.play(url)
  } else {
    p.on('load', () => {
      if (pendingUrl) p.play(pendingUrl)
    })
  }
}

function pause() {
  player?.pause()
}

function stop() {
  if (!player) return
  try {
    player.pause()
    player.clearView()
  } catch {
    /* ignore */
  }
  resetMetrics()
}

function destroy() {
  if (!player) return
  try {
    player.destroy()
  } catch {
    /* ignore */
  }
  player = null
  pendingUrl = null
  resetMetrics()
}

function resetMetrics() {
  videoInfo.value = null
  audioInfo.value = null
  kBps.value = 0
  playFps.value = 0
}

onBeforeUnmount(() => {
  destroy()
})

defineExpose({ play, pause, stop, destroy })

// ============ 工具栏按钮交互 ============

function togglePlay() {
  if (!player) return
  if (isPlaying.value) {
    player.pause()
  } else if (pendingUrl) {
    player.play(pendingUrl)
  }
}

function toggleMute() {
  if (!player) return
  const p = player as JessibucaInstance & { mute?: () => void; cancelMute?: () => void }
  if (isMuted.value) {
    p.cancelMute?.()
    isMuted.value = false
  } else {
    p.mute?.()
    isMuted.value = true
  }
}

function takeScreenshot() {
  const p = player as JessibucaInstance & {
    screenshot?: (name?: string, format?: string, quality?: number) => void
  }
  p.screenshot?.(`snap_${Date.now()}`, 'png', 1)
}

function toggleFullscreen() {
  const p = player as JessibucaInstance & { setFullscreen?: (flag: boolean) => void }
  p.setFullscreen?.(!isFullscreen.value)
}

// ============ 电子放大 ============
//
// 用 CSS transform 把 jessibuca 容器整体缩放 + 平移，纯前端实现，跟摄像头物理是
// 球机/枪机/IPC 无关 —— 球机也能用电子放大（区别于球机自带的光学变倍）。
//
// 交互：
//   - 工具栏 [+]、[-]、[复位] 按钮
//   - 画面区滚轮缩放（以鼠标所在点为锚点，体验更自然）
//   - 放大后鼠标拖拽平移画面
//   - 双击画面快速复位

function zoomIn() {
  applyZoom(Math.min(MAX_ZOOM, zoomScale.value + ZOOM_STEP))
}

function zoomOut() {
  applyZoom(Math.max(MIN_ZOOM, zoomScale.value - ZOOM_STEP))
}

function resetZoom() {
  zoomScale.value = 1
  zoomX.value = 0
  zoomY.value = 0
}

function applyZoom(nextScale: number, anchorX = 0.5, anchorY = 0.5) {
  // anchorX/Y：缩放锚点（容器内归一化坐标 0~1）；默认中心
  if (nextScale <= 1) {
    resetZoom()
    return
  }
  const el = containerRef.value
  if (!el) {
    zoomScale.value = nextScale
    return
  }
  const w = el.clientWidth
  const h = el.clientHeight
  // 锚点像素位置（相对容器左上角）
  const ax = anchorX * w
  const ay = anchorY * h
  // 当前平移下，锚点对应的"图像坐标"
  // 公式：anchorOnImg = (anchor - translate) / scale
  // 缩放后要保持 anchorOnImg 仍在 anchor 像素位置：translateNew = anchor - anchorOnImg * scaleNew
  const oldScale = zoomScale.value
  const imgX = (ax - zoomX.value) / oldScale
  const imgY = (ay - zoomY.value) / oldScale
  zoomScale.value = nextScale
  zoomX.value = ax - imgX * nextScale
  zoomY.value = ay - imgY * nextScale
  clampPan()
}

/**
 * 限制平移范围，避免把画面拖出边界露出黑边
 */
function clampPan() {
  const el = containerRef.value
  if (!el || zoomScale.value <= 1) {
    zoomX.value = 0
    zoomY.value = 0
    return
  }
  const w = el.clientWidth
  const h = el.clientHeight
  // 放大 N 倍后，画面尺寸 = (w * N, h * N)，超出容器的部分 = w*(N-1), h*(N-1)
  // translate 范围：[-(w*(N-1)), 0]
  const maxX = 0
  const minX = -(w * (zoomScale.value - 1))
  const maxY = 0
  const minY = -(h * (zoomScale.value - 1))
  if (zoomX.value > maxX) zoomX.value = maxX
  if (zoomX.value < minX) zoomX.value = minX
  if (zoomY.value > maxY) zoomY.value = maxY
  if (zoomY.value < minY) zoomY.value = minY
}

function onWheel(e: WheelEvent) {
  if (!containerRef.value) return
  e.preventDefault()
  const rect = containerRef.value.getBoundingClientRect()
  const anchorX = (e.clientX - rect.left) / rect.width
  const anchorY = (e.clientY - rect.top) / rect.height
  const delta = e.deltaY < 0 ? ZOOM_STEP : -ZOOM_STEP
  const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoomScale.value + delta))
  applyZoom(next, anchorX, anchorY)
}

let dragStartX = 0
let dragStartY = 0
let panStartX = 0
let panStartY = 0

function onMouseDown(e: MouseEvent) {
  if (!isZoomed.value) return
  isDragging.value = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  panStartX = zoomX.value
  panStartY = zoomY.value
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent) {
  if (!isDragging.value) return
  zoomX.value = panStartX + (e.clientX - dragStartX)
  zoomY.value = panStartY + (e.clientY - dragStartY)
  clampPan()
}

function onMouseUp() {
  isDragging.value = false
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

function onDoubleClick() {
  if (isZoomed.value) {
    resetZoom()
  }
}

function toggleInfoPanel() {
  showInfoPanel.value = !showInfoPanel.value
}

function handleRefresh() {
  emit('refresh')
}

function handleSwitchStream() {
  emit('switch-stream', props.streamType === 'main' ? 'sub' : 'main')
}

function handleSwitchTransport() {
  emit('switch-transport', props.transport === 'UDP' ? 'TCP-passive' : 'UDP')
}
</script>

<template>
  <div class="player-wrap" :style="{ height: props.height }">
    <!-- 画面容器 — 外层固定，内层 jessibuca 容器做 transform 实现电子放大 -->
    <div
      class="stage"
      :class="{ zoomed: isZoomed, dragging: isDragging }"
      @wheel="onWheel"
      @mousedown="onMouseDown"
      @dblclick="onDoubleClick"
    >
      <div
        ref="containerRef"
        class="jessibuca-container"
        :style="{ transform: zoomTransform }"
      ></div>
    </div>

    <!-- 信息面板（点 "信息" 按钮显示） -->
    <div v-if="showInfoPanel" class="info-panel">
      <div><span class="info-label">分辨率</span>{{ resolutionText }}</div>
      <div><span class="info-label">帧 率</span>{{ fpsText }}</div>
      <div><span class="info-label">码 率</span>{{ bandwidthText }}</div>
      <div v-if="audioInfo"><span class="info-label">声 道</span>{{ audioInfo.numOfChannels }}</div>
      <div v-if="audioInfo"><span class="info-label">采样率</span>{{ audioInfo.sampleRate }} Hz</div>
    </div>

    <!-- 放大倍数浮层（缩放生效时显示） -->
    <div v-if="isZoomed" class="zoom-badge">{{ zoomLabel }}</div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <span class="metric"><i class="dot" :class="{ live: isPlaying }"></i>{{ isPlaying ? '播放中' : '已暂停' }}</span>
        <span class="metric">{{ resolutionText }}</span>
        <span class="metric">{{ fpsText }}</span>
        <span class="metric">{{ bandwidthText }}</span>
      </div>
      <div class="toolbar-right">
        <el-tooltip :content="props.streamType === 'main' ? '主码流 · 点击切到辅码流' : '辅码流 · 点击切到主码流'">
          <el-button text size="small" :icon="Switch" @click="handleSwitchStream" />
        </el-tooltip>
        <el-tooltip :content="props.transport === 'UDP' ? 'UDP 收流 · 点击切 TCP' : 'TCP 收流 · 点击切 UDP'">
          <el-button text size="small" :icon="Connection" @click="handleSwitchTransport" />
        </el-tooltip>
        <el-divider direction="vertical" />
        <el-tooltip content="放大（滚轮 / 双击复位）">
          <el-button text size="small" :icon="ZoomIn" :disabled="zoomScale >= MAX_ZOOM" @click="zoomIn" />
        </el-tooltip>
        <el-tooltip content="缩小">
          <el-button text size="small" :icon="ZoomOut" :disabled="zoomScale <= MIN_ZOOM" @click="zoomOut" />
        </el-tooltip>
        <el-tooltip content="复位">
          <el-button text size="small" :icon="Aim" :disabled="!isZoomed" @click="resetZoom" />
        </el-tooltip>
        <el-divider direction="vertical" />
        <el-tooltip :content="isPlaying ? '暂停' : '播放'">
          <el-button text size="small" :icon="isPlaying ? VideoPause : VideoPlay" @click="togglePlay" />
        </el-tooltip>
        <el-tooltip :content="isMuted ? '取消静音' : '静音'">
          <el-button text size="small" :icon="isMuted ? Mute : Microphone" @click="toggleMute" />
        </el-tooltip>
        <el-tooltip content="截图">
          <el-button text size="small" :icon="Camera" @click="takeScreenshot" />
        </el-tooltip>
        <el-tooltip content="刷新">
          <el-button text size="small" :icon="Refresh" @click="handleRefresh" />
        </el-tooltip>
        <el-tooltip content="码流信息">
          <el-button text size="small" :icon="InfoFilled" @click="toggleInfoPanel" />
        </el-tooltip>
        <el-tooltip content="全屏">
          <el-button text size="small" :icon="FullScreen" @click="toggleFullscreen" />
        </el-tooltip>
      </div>
    </div>
  </div>
</template>

<style scoped>
.player-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  background: #000;
  border-radius: 4px;
  overflow: hidden;
}

/* 电子放大舞台：固定容器，内部 jessibuca 用 transform 放大/平移 */
.stage {
  position: relative;
  flex: 1;
  width: 100%;
  min-height: 0;
  overflow: hidden;
  background: #000;
}
.stage.zoomed {
  cursor: grab;
}
.stage.dragging {
  cursor: grabbing;
}

.jessibuca-container {
  width: 100%;
  height: 100%;
  background: #000;
  transform-origin: 0 0;
  transition: transform 80ms ease-out;
}
/* jessibuca 内部 canvas 不拦截鼠标事件，让 .stage 的 mousedown/wheel/dblclick 能命中 */
.jessibuca-container :deep(canvas) {
  pointer-events: none;
}
.stage.dragging .jessibuca-container {
  transition: none;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 8px;
  background: rgba(20, 20, 20, 0.85);
  color: #d6d8db;
  font-size: 12px;
  user-select: none;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-right :deep(.el-button) {
  color: #d6d8db;
  padding: 4px 8px;
}
.toolbar-right :deep(.el-button:hover),
.toolbar-right :deep(.el-button:focus) {
  color: #409eff;
  background: rgba(255, 255, 255, 0.06);
}
.toolbar-right :deep(.el-divider--vertical) {
  background: rgba(255, 255, 255, 0.15);
  margin: 0 4px;
}

.metric {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #909399;
  display: inline-block;
}
.dot.live {
  background: #67c23a;
  box-shadow: 0 0 6px #67c23a;
}

/* 信息浮层 */
.info-panel {
  position: absolute;
  top: 12px;
  right: 12px;
  background: rgba(0, 0, 0, 0.6);
  color: #f0f0f0;
  font-size: 12px;
  padding: 8px 12px;
  border-radius: 4px;
  line-height: 1.8;
  z-index: 10;
  pointer-events: none;
}
.info-label {
  display: inline-block;
  width: 50px;
  color: #909399;
}

/* 放大倍数浮层 */
.zoom-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  z-index: 10;
  pointer-events: none;
}
</style>
