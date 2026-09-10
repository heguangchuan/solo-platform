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
 *   <li>{@code switch-stream} — 码流切换（主/子码流，抛档位 value）— 业务层处理</li>
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
  Position,
} from '@element-plus/icons-vue'
import type { JessibucaInstance } from '@/types/jessibuca'

const props = withDefaults(
  defineProps<{
    /** 是否需要音频解码 */
    hasAudio?: boolean
    /** 容器高度（默认 360px） */
    height?: string
    /** 可选码流档位（业务层传入）；不传或不足 2 档则不显示码流切换按钮。value 为发给后端的码流 token（空串=主码流） */
    streams?: Array<{ label: string; value: string }>
    /** 当前码流档位 value（业务层维护，传给 toolbar 显示） */
    currentStream?: string
    /** 当前收流模式（业务层维护，传给 toolbar 显示） */
    transport?: 'UDP' | 'TCP-passive' | 'TCP-active'
    /**
     * 缓冲时长（秒）。实时点播用 0 追求最低延迟；
     * 回放场景设备推流不均匀（定位后突发、中间断续），给 0.2~0.5s 缓冲可显著减少卡顿。
     */
    videoBuffer?: number
  }>(),
  {
    hasAudio: true, height: '420px', streams: () => [], currentStream: '',
    transport: 'UDP', videoBuffer: 0,
  },
)

const emit = defineEmits<{
  playing: []
  pause: []
  error: [msg: string]
  timeout: []
  streamEnd: []
  'switch-stream': [value: string]
  'switch-transport': [t: 'UDP' | 'TCP-passive' | 'TCP-active']
  refresh: []
  /** 云台控制：direction 为方向/动作，pressing=true 开始、false 停止 */
  ptz: [direction: string, pressing: boolean]
}>()

const containerRef = ref<HTMLDivElement | null>(null)
let player: JessibucaInstance | null = null
let pendingUrl: string | null = null

// ============ 状态 ============
const isPlaying = ref(false)
const isMuted = ref(true) // jessibuca 默认静音以满足浏览器自动播放策略
const isFullscreen = ref(false)
const showInfoPanel = ref(false)
const videoInfo = ref<{
  width: number
  height: number
  /** 编码类型字符串（官方 videoInfo 扩展字段，如 "H264"/"H265"） */
  encType?: string
  /** 编码类型码（官方约定 10=h264, 12=h265），encType 缺失时兜底 */
  encTypeCode?: number
} | null>(null)
const audioInfo = ref<{
  numOfChannels: number
  sampleRate: number
  /** 音频编码字符串（官方 audioInfo 扩展字段，如 "AAC"/"PCMA"/"PCMU"） */
  encType?: string
  /** 音频编码码（官方约定 10=aac, 7=ALAW/g711a, 8=MULAW/g711u），encType 缺失时兜底 */
  encTypeCode?: number
} | null>(null)
const playFps = ref(0)
// stats 每秒钟吐出：更精确的视/音频码率（字节/秒）+ 缓冲时长（ms）
const videoBps = ref(0)
const audioBps = ref(0)
const bufferMs = ref(0)
// performance：0=卡顿 1=流畅 2=非常流畅
const perfLevel = ref<number | null>(null)

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
// 视频编码：优先用 encType 字符串；缺失时按官方 encTypeCode（10=h264, 12=h265）兜底
const videoCodecText = computed(() => {
  const v = videoInfo.value
  if (!v) return '—'
  if (v.encType) return v.encType.toUpperCase()
  if (v.encTypeCode === 10) return 'H264'
  if (v.encTypeCode === 12) return 'H265'
  return '—'
})
// 音频编码：优先用 encType 字符串；缺失时按官方 encTypeCode（10=aac, 7=g711a, 8=g711u）兜底
const audioCodecText = computed(() => {
  const a = audioInfo.value
  if (!a) return '—'
  if (a.encType) return a.encType.toUpperCase()
  if (a.encTypeCode === 10) return 'AAC'
  if (a.encTypeCode === 7) return 'G711A'
  if (a.encTypeCode === 8) return 'G711U'
  return '—'
})
const fpsText = computed(() => (playFps.value > 0 ? `${playFps.value} fps` : '—'))
// 码率：字节/秒 → kbps（×8 ÷1000）
const videoBitrateText = computed(() =>
  videoBps.value > 0 ? `${Math.round((videoBps.value * 8) / 1000)} kbps` : '—',
)
const audioBitrateText = computed(() =>
  audioBps.value > 0 ? `${Math.round((audioBps.value * 8) / 1000)} kbps` : '—',
)
const bufferText = computed(() => (bufferMs.value > 0 ? `${bufferMs.value} ms` : '—'))
const performanceText = computed(() => {
  switch (perfLevel.value) {
    case 0:
      return '卡顿'
    case 1:
      return '流畅'
    case 2:
      return '非常流畅'
    default:
      return '—'
  }
})

// ============ 码流切换 ============
// 业务层传入 streams（>=2 档）才显示切换按钮；点击按钮弹出码流选项浮层，点选即切换。
const showStreamSwitch = computed(() => props.streams.length >= 2)
const streamMenuVisible = ref(false)

// ============ 收流模式切换 ============
// 与码流切换同款交互：点击按钮弹出浮层，点选即切换。写死三档。
const transportMenuVisible = ref(false)
const TRANSPORTS: Array<{ label: string; value: 'UDP' | 'TCP-passive' | 'TCP-active' }> = [
  { label: 'UDP', value: 'UDP' },
  { label: 'TCP 被动', value: 'TCP-passive' },
  { label: 'TCP 主动', value: 'TCP-active' },
]

// ============ 云台控制 ============
const ptzVisible = ref(false)
let ptzActive = '' // 当前按住的方向

function onPtzStart(direction: string) {
  ptzActive = direction
  emit('ptz', direction, true)
  window.addEventListener('mouseup', onPtzGlobalUp, { once: true })
}

function onPtzGlobalUp() {
  if (!ptzActive) return
  emit('ptz', ptzActive, false)
  ptzActive = ''
}

function onPtzStop(_direction: string) {
  // 中间停止键专用（单击即停）
  ptzActive = ''
  emit('ptz', 'stop', false)
}

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
    videoBuffer: props.videoBuffer,
    isResize: true,
    text: '',
    loadingText: '正在拉流，请稍候...',
    useMSE: false,
    useWCS: false,
    decoder: '/static/js/jessibuca/decoder.js',
    forceNoOffscreen: true,
    hasAudio: props.hasAudio,
    // 本项目播放地址统一是 WS-FLV / HTTP-FLV（MediaUrlBuilder 产出），必须走 FLV demuxer。
    // 置 false 会让 jessibuca 按裸流解析，拿到 FLV 数据解不出 track，表现为一直转圈不出画面。
    isFlv: true,
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
    videoInfo.value = data as {
      width: number
      height: number
      encType?: string
      encTypeCode?: number
    }
  })
  player.on('audioInfo', (data: unknown) => {
    audioInfo.value = data as {
      numOfChannels: number
      sampleRate: number
      encType?: string
      encTypeCode?: number
    }
  })
  player.on('stats', (s: unknown) => {
    const stat = s as { fps?: number; vbps?: number; abps?: number; buf?: number }
    if (stat && typeof stat.fps === 'number') playFps.value = stat.fps
    if (stat && typeof stat.vbps === 'number') videoBps.value = stat.vbps
    if (stat && typeof stat.abps === 'number') audioBps.value = stat.abps
    if (stat && typeof stat.buf === 'number') bufferMs.value = stat.buf
  })
  player.on('performance', (p: unknown) => {
    const level = Number(p)
    perfLevel.value = Number.isNaN(level) ? null : level
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

/**
 * 从本地暂停态恢复（jessibuca play() 不传 url 即可恢复之前缓存的流，不需要重新拉）。
 * 与 {@link #play} 的区别：本方法不传 url，不会触发重新拉流 / 重新解码。
 */
function resume() {
  player?.play()
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
  playFps.value = 0
  videoBps.value = 0
  audioBps.value = 0
  bufferMs.value = 0
  perfLevel.value = null
}

onBeforeUnmount(() => {
  destroy()
})

defineExpose({ play, pause, resume, stop, destroy })

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

function handleSwitchStream(value: string) {
  streamMenuVisible.value = false
  if (value === props.currentStream) return
  emit('switch-stream', value)
}

function handleSwitchTransport(value: 'UDP' | 'TCP-passive' | 'TCP-active') {
  transportMenuVisible.value = false
  if (value === props.transport) return
  emit('switch-transport', value)
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
      <div><span class="info-label">视频编码</span>{{ videoCodecText }}</div>
      <div><span class="info-label">帧 率</span>{{ fpsText }}</div>
      <div><span class="info-label">视频码率</span>{{ videoBitrateText }}</div>
      <div v-if="audioInfo"><span class="info-label">音频编码</span>{{ audioCodecText }}</div>
      <div v-if="audioInfo"><span class="info-label">音频码率</span>{{ audioBitrateText }}</div>
      <div v-if="audioInfo"><span class="info-label">声 道</span>{{ audioInfo.numOfChannels }}</div>
      <div v-if="audioInfo"><span class="info-label">采样率</span>{{ audioInfo.sampleRate }} Hz</div>
      <div><span class="info-label">缓 冲</span>{{ bufferText }}</div>
      <div><span class="info-label">流畅度</span>{{ performanceText }}</div>
    </div>

    <!-- 放大倍数浮层（缩放生效时显示） -->
    <div v-if="isZoomed" class="zoom-badge">{{ zoomLabel }}</div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-tooltip content="全屏">
          <el-button text size="small" :icon="FullScreen" @click="toggleFullscreen" />
        </el-tooltip>
        <el-tooltip content="放大（滚轮 / 双击复位）">
          <el-button text size="small" :icon="ZoomIn" :disabled="zoomScale >= MAX_ZOOM" @click="zoomIn" />
        </el-tooltip>
        <el-tooltip content="缩小">
          <el-button text size="small" :icon="ZoomOut" :disabled="zoomScale <= MIN_ZOOM" @click="zoomOut" />
        </el-tooltip>
        <el-tooltip content="复位">
          <el-button text size="small" :icon="Aim" :disabled="!isZoomed" @click="resetZoom" />
        </el-tooltip>
        <el-tooltip :content="isPlaying ? '暂停' : '播放'">
          <el-button text size="small" :icon="isPlaying ? VideoPause : VideoPlay" @click="togglePlay" />
        </el-tooltip>
        <el-tooltip :content="isMuted ? '取消静音' : '静音'">
          <el-button text size="small" :icon="isMuted ? Mute : Microphone" @click="toggleMute" />
        </el-tooltip>
        <el-tooltip
          v-if="showStreamSwitch"
          v-model:visible="streamMenuVisible"
          trigger="click"
          placement="top"
          effect="dark"
          popper-class="stream-menu"
        >
          <template #content>
            <div class="stream-menu-list">
              <div
                v-for="s in streams"
                :key="s.value"
                class="stream-menu-item"
                :class="{ 'is-active': s.value === currentStream }"
                @click="handleSwitchStream(s.value)"
              >
                {{ s.label }}
              </div>
            </div>
          </template>
          <el-button text size="small" :icon="Switch" title="码流切换" />
        </el-tooltip>
        <el-tooltip
          v-model:visible="transportMenuVisible"
          trigger="click"
          placement="top"
          effect="dark"
          popper-class="stream-menu"
        >
          <template #content>
            <div class="stream-menu-list">
              <div
                v-for="t in TRANSPORTS"
                :key="t.value"
                class="stream-menu-item"
                :class="{ 'is-active': t.value === transport }"
                @click="handleSwitchTransport(t.value)"
              >
                {{ t.label }}
              </div>
            </div>
          </template>
          <el-button text size="small" :icon="Connection" title="收流模式" />
        </el-tooltip>
        <el-tooltip
          v-model:visible="ptzVisible"
          trigger="click"
          placement="top"
          effect="dark"
          popper-class="ptz-panel-popper"
          :show-arrow="false"
        >
          <template #content>
            <div class="ptz-panel">
              <div class="ptz-grid">
                <div class="ptz-btn" @mousedown="onPtzStart('up-left')">↖</div>
                <div class="ptz-btn" @mousedown="onPtzStart('up')">↑</div>
                <div class="ptz-btn" @mousedown="onPtzStart('up-right')">↗</div>
                <div class="ptz-btn" @mousedown="onPtzStart('left')">←</div>
                <div class="ptz-btn ptz-center" @click="onPtzStop('stop')">●</div>
                <div class="ptz-btn" @mousedown="onPtzStart('right')">→</div>
                <div class="ptz-btn" @mousedown="onPtzStart('down-left')">↙</div>
                <div class="ptz-btn" @mousedown="onPtzStart('down')">↓</div>
                <div class="ptz-btn" @mousedown="onPtzStart('down-right')">↘</div>
              </div>
              <div class="ptz-zoom">
                <div class="ptz-btn" @mousedown="onPtzStart('zoom-in')">🔍+</div>
                <div class="ptz-btn" @mousedown="onPtzStart('zoom-out')">🔍−</div>
              </div>
            </div>
          </template>
          <el-button text size="small" :icon="Position" title="云台控制" />
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
      </div>
      <div class="toolbar-right"></div>
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
  gap: 2px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
}

.toolbar-left :deep(.el-button) {
  color: #d6d8db;
  padding: 4px 6px;
}
.toolbar-left :deep(.el-button:hover),
.toolbar-left :deep(.el-button:focus) {
  color: #409eff;
  background: rgba(255, 255, 255, 0.06);
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

<!-- 码流选项浮层 teleport 到 body，scoped 够不到，用 popper-class 定制（非 scoped） -->
<style>
/* 用 el-tooltip 的 dark effect 打底，这里贴合工具栏色调并去掉默认内边距 */
.stream-menu.el-popper.is-dark {
  background: rgba(20, 20, 20, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 4px;
}
.stream-menu .el-popper__arrow::before {
  background: rgba(20, 20, 20, 0.95) !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
}
.stream-menu-list {
  display: flex;
  flex-direction: column;
  min-width: 120px;
}
.stream-menu-item {
  padding: 6px 12px;
  font-size: 12px;
  color: #d6d8db;
  border-radius: 3px;
  cursor: pointer;
  white-space: nowrap;
}
.stream-menu-item:hover {
  background: rgba(64, 158, 255, 0.15);
  color: #409eff;
}
.stream-menu-item.is-active {
  color: #409eff;
  font-weight: 600;
}

/* 云台控制面板 */
.ptz-panel-popper.el-popper.is-dark {
  background: rgba(20, 20, 20, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 10px;
}
.ptz-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.ptz-grid {
  display: grid;
  grid-template-columns: repeat(3, 36px);
  grid-template-rows: repeat(3, 36px);
  gap: 4px;
}
.ptz-zoom {
  display: flex;
  gap: 4px;
}
.ptz-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.08);
  color: #d6d8db;
  font-size: 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s, color 0.15s;
}
.ptz-btn:hover {
  background: rgba(64, 158, 255, 0.25);
  color: #409eff;
}
.ptz-btn:active {
  background: rgba(64, 158, 255, 0.45);
  color: #fff;
}
.ptz-center {
  background: rgba(255, 255, 255, 0.15);
  color: #f56c6c;
  font-size: 12px;
}
.ptz-center:hover {
  background: rgba(245, 108, 108, 0.25);
  color: #f56c6c;
}
</style>
