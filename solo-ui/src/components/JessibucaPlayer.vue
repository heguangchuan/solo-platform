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
const scaleMode = ref(1) // 0=拉伸、1=等比有黑边、2=等比满铺
const videoInfo = ref<{ width: number; height: number } | null>(null)
const audioInfo = ref<{ numOfChannels: number; sampleRate: number } | null>(null)
const kBps = ref(0)
const playFps = ref(0)

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

function cycleScaleMode() {
  if (!player) return
  scaleMode.value = (scaleMode.value + 1) % 3
  const p = player as JessibucaInstance & { setScaleMode?: (m: number) => void }
  p.setScaleMode?.(scaleMode.value)
}

const scaleModeLabel = computed(() => ['拉伸', '等比', '满铺'][scaleMode.value])

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
    <!-- 画面容器 -->
    <div ref="containerRef" class="jessibuca-container"></div>

    <!-- 信息面板（点 "信息" 按钮显示） -->
    <div v-if="showInfoPanel" class="info-panel">
      <div><span class="info-label">分辨率</span>{{ resolutionText }}</div>
      <div><span class="info-label">帧 率</span>{{ fpsText }}</div>
      <div><span class="info-label">码 率</span>{{ bandwidthText }}</div>
      <div v-if="audioInfo"><span class="info-label">声 道</span>{{ audioInfo.numOfChannels }}</div>
      <div v-if="audioInfo"><span class="info-label">采样率</span>{{ audioInfo.sampleRate }} Hz</div>
    </div>

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
        <el-tooltip :content="'缩放模式：' + scaleModeLabel + ' · 点击切换'">
          <el-button text size="small" :icon="ZoomIn" @click="cycleScaleMode" />
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

.jessibuca-container {
  flex: 1;
  width: 100%;
  background: #000;
  min-height: 0;
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
</style>
