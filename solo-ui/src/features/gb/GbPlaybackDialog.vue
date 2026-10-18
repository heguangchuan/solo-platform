<script setup lang="ts">
/**
 * 历史回放弹窗（可复用组件）
 *
 * 调用方持有 ref，调 open(deviceId, channelId, name) 打开。
 *
 * 交互两段式：
 * 1. 查询段：选时间范围 → 查 RecordInfo → 列出录像段
 * 2. 播放段：点录像段 → INVITE Playback → GbPlayer 播放 + 控制条（暂停/继续/倍速/跳转）
 *
 * 播放器复用 GbPlayer；流媒体协议跟点播一致（WS-FLV）。
 * 与点播对齐：可切换主/子码流 + 收流模式（UDP / TCP-passive / TCP-active）。
 */
import { nextTick, ref, computed, watch } from 'vue'
import {
  Play,
  Pause,
  Download,
  RefreshCw,
  Search,
  Layers,
  Network,
  Info,
  Volume2,
  VolumeX,
  Maximize2,
  Camera,
  Check,
} from 'lucide-vue-next'
import type { GbRecordItem, GbPlaybackResult, GbDownloadResult, GbVideoInfo, GbAudioInfo, GbPlayerStats, GbDeviceChannel } from '@/types/api/gb28181'
import {
  queryRecordInfoApi,
  playbackStartApi,
  playbackControlApi,
  startDownloadApi,
  getDownloadStatusApi,
  stopDownloadApi,
} from '@/api/gb28181'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import GbPlayer from './GbPlayer.vue'

const props = defineProps<{
  open: boolean
  channel?: GbDeviceChannel
}>()
const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const deviceIdRef = ref('')
const channelIdRef = ref('')
const channelNameRef = ref('')

// ========== 查询段 ==========
const startTime = ref('')
const endTime = ref('')
const querying = ref(false)
const records = ref<GbRecordItem[]>([])

// ========== 播放段 ==========
const playing = ref(false)
const loadingPlay = ref(false)
const statusText = ref('')
const errorText = ref('')
const streamInfo = ref<GbPlaybackResult | null>(null)
const currentRecord = ref<GbRecordItem | null>(null)
const playerRef = ref<InstanceType<typeof GbPlayer> | null>(null)

// 控制条状态
const paused = ref(false)
const speed = ref(1)
const speedOptions = [0.25, 0.5, 1, 2, 4]
const speedMenuOpen = ref(false)
/**
 * 进度条当前位置（只跟用户拖动有关）：
 * - 拖动中：随拖动实时变化（视觉反馈）
 * - 松手：发 seek 请求
 * - 不接入任何自动推进（timeUpdate 不接、setInterval 也不开）
 */
const sliderValue = ref(0)
const durationSec = ref(0)

// 码流档位（跟点播对齐的字典标签）
const STREAMS = [
  { label: '主码流', value: '' },
  { label: '子码流', value: 'stream:1' },
  { label: '主码流-2022', value: 'streamnumber:0' },
  { label: '子码流-2022', value: 'streamnumber:1' },
  { label: '主码流-大华', value: 'streamprofile:0' },
  { label: '子码流-大华', value: 'streamprofile:1' },
  { label: '主码流-水星+TP-LINK', value: 'streamMode:main' },
  { label: '子码流-水星+TP-LINK', value: 'streamMode:sub' },
]
const currentStream = ref('streamnumber:1')
const streamMenuOpen = ref(false)

// 收流模式
const TRANSPORTS = [
  { label: 'UDP', value: 'UDP' },
  { label: 'TCP 被动', value: 'TCP-passive' },
  { label: 'TCP 主动', value: 'TCP-active' },
] as const
const transport = ref<'UDP' | 'TCP-passive' | 'TCP-active'>('TCP-passive')
const transportMenuOpen = ref(false)

// ========== 播放器实时信息 ==========
const videoInfo = ref<GbVideoInfo | null>(null)
const audioInfo = ref<GbAudioInfo | null>(null)
const stats = ref<GbPlayerStats | null>(null)
const perfLevel = ref<number | null>(null)
const showInfoPanel = ref(false)
const muted = ref(true)

const resolutionText = computed(() => {
  if (!videoInfo.value) return '—'
  return `${videoInfo.value.width}×${videoInfo.value.height}`
})
const videoCodecText = computed(() => videoInfo.value?.encType || '—')
const fpsText = computed(() => (stats.value?.fps != null ? `${stats.value.fps} fps` : '—'))
const videoBitrateText = computed(() => {
  const bps = stats.value?.vbps
  if (bps == null) return '—'
  return `${Math.round((bps * 8) / 1000)} kbps`
})
const audioCodecText = computed(() => audioInfo.value?.encType || '—')
const audioBitrateText = computed(() => {
  const bps = stats.value?.abps
  if (bps == null) return '—'
  return `${Math.round((bps * 8) / 1000)} kbps`
})
const bufferText = computed(() => (stats.value?.buf != null ? `${stats.value.buf} ms` : '—'))
const performanceText = computed(() => {
  if (perfLevel.value === 0) return '卡顿'
  if (perfLevel.value === 1) return '流畅'
  if (perfLevel.value === 2) return '非常流畅'
  return '—'
})

const onVideoInfo = (v: GbVideoInfo) => (videoInfo.value = { ...v })
const onAudioInfo = (a: GbAudioInfo) => (audioInfo.value = { ...a })
// jessibuca stats 事件每次复用同一个对象，必须展开创建新引用
const onStats = (s: GbPlayerStats) => (stats.value = { ...s })
const onPerformance = (level: number) => (perfLevel.value = level)
const onMuteState = (flag: boolean) => (muted.value = flag)

// ========== 下载 ==========
const downloadInfo = ref<GbDownloadResult | null>(null)
const downloadLoading = ref(false)
const downloadStopping = ref(false)
const showDownload = ref(false)

const DOWNLOAD_STATUS: Record<string, { text: string; color: string }> = {
  INVITING: { text: '正在邀请设备...', color: 'text-blue-500' },
  RECORDING: { text: '设备录制中，等待落盘...', color: 'text-blue-500' },
  READY: { text: '下载已就绪', color: 'text-green-500' },
  FAILED: { text: '下载失败', color: 'text-red-500' },
  TIMEOUT: { text: '下载超时', color: 'text-red-500' },
  NOT_FOUND: { text: '任务不存在或已过期', color: 'text-yellow-500' },
}

function downloadStatusView(s?: string) {
  if (!s) return { text: '未知', color: 'text-yellow-500' }
  return DOWNLOAD_STATUS[s] || { text: s, color: 'text-yellow-500' }
}

// ========== 工具函数 ==========
function pad(n: number) {
  return String(n).padStart(2, '0')
}

function defaultRange(): [string, string] {
  const now = new Date()
  const day = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
  return [`${day} 00:00:00`, `${day} 23:59:59`]
}

/** ISO-8601 "2026-06-30T08:00:00" → "2026-06-30 08:00:00" */
function fmtIso(iso?: string): string {
  if (!iso) return ''
  return iso.replace('T', ' ')
}

/** 录像段时间（MM-DD HH:mm:ss） */
function recClock(iso?: string): string {
  return fmtIso(iso).slice(5, 19)
}

function fmtDuration(sec: number): string {
  const s = Math.max(0, Math.floor(sec))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = s % 60
  return `${pad(h)}:${pad(m)}:${pad(ss)}`
}

function pickUrl(info: GbPlaybackResult): string | undefined {
  // 优先 WS-FLV（TCP 穿透好），其次 HTTP-FLV
  return info.wsFlvUrl || info.flvUrl
}

// ========== 对外 API ==========

// 打开时自动初始化：当天时间范围 + 自动查询
watch(
  () => props.open,
  (open) => {
    if (open && props.channel) {
      deviceIdRef.value = props.channel.deviceId ?? ''
      channelIdRef.value = props.channel.channelId
      channelNameRef.value = props.channel.name ?? ''
      const [s, e] = defaultRange()
      startTime.value = s
      endTime.value = e
      records.value = []
      resetPlayState()
      resetDownloadState()
      // 打开即自动查一次当天录像
      nextTick().then(() => handleQuery())
    }
  },
)

function resetPlayState() {
  playing.value = false
  loadingPlay.value = false
  statusText.value = ''
  errorText.value = ''
  streamInfo.value = null
  currentRecord.value = null
  paused.value = false
  speed.value = 1
  speedMenuOpen.value = false
  sliderValue.value = 0
  durationSec.value = 0
  transport.value = 'TCP-passive'
  currentStream.value = 'streamnumber:1'
  videoInfo.value = null
  audioInfo.value = null
  stats.value = null
  perfLevel.value = null
  showInfoPanel.value = false
  muted.value = true
}

function resetDownloadState() {
  downloadInfo.value = null
  downloadLoading.value = false
  downloadStopping.value = false
  showDownload.value = false
}

function handleOpenChange(open: boolean) {
  if (!open) {
    onClose()
  } else {
    emit('update:open', true)
  }
}

// ========== 查询 ==========

async function handleQuery() {
  if (!startTime.value || !endTime.value) {
    toast.warning('请选择时间范围')
    return
  }
  querying.value = true
  records.value = []
  try {
    const { data } = await queryRecordInfoApi({
      deviceId: deviceIdRef.value,
      channelId: channelIdRef.value,
      startTime: startTime.value,
      endTime: endTime.value,
    })
    records.value = data || []
    if (records.value.length === 0) {
      toast.info('该时间段无录像')
    }
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    querying.value = false
  }
}

// ========== 播放 ==========

async function handlePlayRecord(row: GbRecordItem) {
  // 共享流：直接拿后端返回的流播，不主动 stop（同参数多端复用一条流，由后端空闲超时回收）
  playerRef.value?.pause?.()

  currentRecord.value = row
  loadingPlay.value = true
  statusText.value = '正在发起回放...'
  errorText.value = ''
  try {
    const { data } = await playbackStartApi({
      deviceId: deviceIdRef.value,
      channelId: channelIdRef.value,
      startTime: fmtIso(row.startTime),
      endTime: fmtIso(row.endTime),
      transport: transport.value,
      streamNumber: currentStream.value || undefined,
    })
    streamInfo.value = data
    durationSec.value = data.durationSec || 0
    sliderValue.value = 0
    paused.value = false
    speed.value = 1
    speedMenuOpen.value = false
    statusText.value = ''
    playing.value = true
    const url = pickUrl(data)
    if (!url) {
      errorText.value = '后端未返回可用的播放地址'
      return
    }
    await nextTick()
    playerRef.value?.play?.(url)
  } catch (e) {
    errorText.value = e instanceof Error ? e.message : String(e)
    statusText.value = ''
  } finally {
    loadingPlay.value = false
  }
}

async function handleSwitchStream(value: string) {
  streamMenuOpen.value = false
  if (value === currentStream.value) return
  if (!currentRecord.value) return
  currentStream.value = value
  await handlePlayRecord(currentRecord.value)
}

async function handleSwitchTransport(value: string) {
  transportMenuOpen.value = false
  if (value === transport.value) return
  if (!currentRecord.value) return
  transport.value = value as typeof transport.value
  await handlePlayRecord(currentRecord.value)
}

async function handleRefresh() {
  if (!currentRecord.value) return
  await handlePlayRecord(currentRecord.value)
}

// ========== 控制条 ==========

async function togglePause() {
  if (!streamInfo.value?.streamId) return
  const nextAction = paused.value ? 'resume' : 'pause'
  try {
    await playbackControlApi({
      streamId: streamInfo.value.streamId,
      action: nextAction,
    })
    paused.value = !paused.value
    // 本地播放器同步
    if (paused.value) {
      playerRef.value?.pause?.()
    } else {
      playerRef.value?.resume?.()
    }
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

async function handleSpeed(v: number) {
  if (!streamInfo.value?.streamId) return
  try {
    await playbackControlApi({
      streamId: streamInfo.value.streamId,
      action: 'speed',
      param: String(v),
    })
    speed.value = v
    speedMenuOpen.value = false
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

async function handleSeek(v: number) {
  if (!streamInfo.value?.streamId) return
  try {
    await playbackControlApi({
      streamId: streamInfo.value.streamId,
      action: 'seek',
      param: String(Math.floor(v)),
    })
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

function onSliderInput(e: Event) {
  const target = e.target as HTMLInputElement
  sliderValue.value = Number(target.value)
}

function onSliderChange() {
  handleSeek(sliderValue.value)
}

/**
 * 播放器抛上来的"流结束"事件：回放流被关闭/结束。
 * 只做友好提示，不自动重连；用户可重新点录像段。
 */
function onStreamEnd() {
  playing.value = false
  statusText.value = '回放已结束'
  errorText.value = ''
}

function onPlayerError(msg: string) {
  errorText.value = msg
  statusText.value = ''
}

// ========== 下载 ==========

async function handleDownload() {
  if (!currentRecord.value) {
    toast.warning('请先选择一段录像')
    return
  }
  try {
    const { data } = await startDownloadApi({
      deviceId: deviceIdRef.value,
      channelId: channelIdRef.value,
      startTime: fmtIso(currentRecord.value.startTime),
      endTime: fmtIso(currentRecord.value.endTime),
      transport: transport.value,
      speed: 1,
      streamNumber: currentStream.value || undefined,
    })
    const sid = data?.streamId
    if (!sid) {
      toast.error('启动下载失败：未返回 streamId')
      return
    }
    downloadInfo.value = data
    showDownload.value = true
    toast.success('下载任务已发起')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

async function refreshDownload() {
  if (!downloadInfo.value?.streamId) return
  downloadLoading.value = true
  try {
    const { data } = await getDownloadStatusApi(downloadInfo.value.streamId)
    downloadInfo.value = data
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    downloadLoading.value = false
  }
}

function downloadNow() {
  const url = downloadInfo.value?.downloadUrl
  if (!url) {
    toast.warning('下载链接尚未生成')
    return
  }
  window.location.href = url
}

async function copyDownloadLink() {
  const url = downloadInfo.value?.downloadUrl
  if (!url) {
    toast.warning('下载链接尚未生成')
    return
  }
  try {
    await navigator.clipboard.writeText(url)
    toast.success('链接已复制')
  } catch {
    toast.error('复制失败，请手动选中')
  }
}

async function stopDownload() {
  const ok = await confirm({ title: '确定取消下载？' })
  if (!ok) return
  if (!downloadInfo.value?.streamId) return
  downloadStopping.value = true
  try {
    await stopDownloadApi(downloadInfo.value.streamId)
    toast.success('已取消下载')
    await refreshDownload()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    downloadStopping.value = false
  }
}

// ========== 关闭 / 清理 ==========

function onClose() {
  // 共享流：只在本地断开播放器，不通知后端停止，避免影响其他观看者
  playerRef.value?.pause?.()
  resetPlayState()
  resetDownloadState()
  emit('update:open', false)
}

function onPlayerTimeout() {
  errorText.value = '拉流超时，请检查设备或刷新重试'
  statusText.value = ''
}
</script>

<template>
  <Dialog :open="open" @update:open="handleOpenChange">
    <DialogContent class="sm:max-w-5xl p-0 gap-0">
      <DialogHeader class="px-6 py-4 border-b">
        <DialogTitle class="text-base">
          历史回放
          <span v-if="channelNameRef" class="text-sm font-normal text-muted-foreground ml-2">
            - {{ channelNameRef }}
          </span>
        </DialogTitle>
        <DialogDescription />
      </DialogHeader>

      <div class="flex gap-3 p-4 h-[560px]">
        <!-- 左侧：录像段列表 -->
        <div class="w-60 flex flex-col gap-3 border-r pr-3 min-h-0">
          <div class="space-y-2">
            <div class="text-xs font-medium text-muted-foreground">开始时间</div>
            <Input v-model="startTime" type="datetime-local" size="sm" />
            <div class="text-xs font-medium text-muted-foreground">结束时间</div>
            <Input v-model="endTime" type="datetime-local" size="sm" />
            <Button :loading="querying" size="sm" class="w-full" @click="handleQuery">
              <Search class="w-4 h-4 mr-1.5" />
              查询录像
            </Button>
          </div>

          <div class="flex-1 overflow-y-auto min-h-0 space-y-1.5 pr-1">
            <div v-if="querying" class="text-center text-sm text-muted-foreground py-8">查询中...</div>
            <div v-else-if="records.length === 0" class="text-center text-sm text-muted-foreground py-8">无录像</div>
            <div
              v-for="rec in records"
              :key="(rec.startTime || '') + (rec.endTime || '')"
              class="px-3 py-2 rounded-md border cursor-pointer text-sm transition-colors"
              :class="
                currentRecord === rec
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-border hover:border-primary/50 hover:bg-accent'
              "
              @click="handlePlayRecord(rec)"
            >
              <div class="flex items-center gap-1 font-mono text-xs tabular-nums">
                <span>{{ recClock(rec.startTime) }}</span>
                <span class="text-muted-foreground">~</span>
                <span>{{ recClock(rec.endTime) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：播放器 + 控制条 -->
        <div class="flex-1 min-w-0 flex flex-col gap-3">
          <!-- 状态条 -->
          <div v-if="errorText" class="px-3 py-2 rounded-md bg-red-50 text-red-600 text-sm">
            {{ errorText }}
          </div>
          <div v-else-if="statusText" class="px-3 py-2 rounded-md bg-blue-50 text-blue-600 text-sm">
            {{ statusText }}
          </div>

          <!-- 播放器区域 -->
          <div class="relative bg-black rounded-md overflow-hidden flex-1 min-h-0">
            <GbPlayer
              v-if="playing"
              ref="playerRef"
              :src="currentRecord ? 'playback' : ''"
              :video-buffer="0.3"
              :show-toolbar="false"
              :show-zoom="false"
              @error="onPlayerError"
              @stream-end="onStreamEnd"
              @timeout="onPlayerTimeout"
              @video-info="onVideoInfo"
              @audio-info="onAudioInfo"
              @stats="onStats"
              @performance="onPerformance"
              @mute="onMuteState"
            />
            <div v-else class="absolute inset-0 flex items-center justify-center text-neutral-500 text-sm">
              请从左侧选择录像段开始回放
            </div>

            <!-- 信息面板 -->
            <div
              v-if="showInfoPanel && videoInfo"
              class="absolute top-3 right-3 bg-neutral-900/95 backdrop-blur-sm text-white text-xs leading-7 px-4 py-3 rounded-md shadow-lg z-10 min-w-[200px] space-y-0.5"
            >
              <div class="flex items-center justify-between gap-6">
                <span class="text-neutral-400">分辨率</span>
                <span class="font-mono">{{ resolutionText }}</span>
              </div>
              <div class="flex items-center justify-between gap-6">
                <span class="text-neutral-400">视频编码</span>
                <span class="font-mono">{{ videoCodecText }}</span>
              </div>
              <div class="flex items-center justify-between gap-6">
                <span class="text-neutral-400">帧率</span>
                <span class="font-mono">{{ fpsText }}</span>
              </div>
              <div class="flex items-center justify-between gap-6">
                <span class="text-neutral-400">视频码率</span>
                <span class="font-mono">{{ videoBitrateText }}</span>
              </div>
              <template v-if="audioInfo">
                <div class="flex items-center justify-between gap-6">
                  <span class="text-neutral-400">音频编码</span>
                  <span class="font-mono">{{ audioCodecText }}</span>
                </div>
                <div class="flex items-center justify-between gap-6">
                  <span class="text-neutral-400">音频码率</span>
                  <span class="font-mono">{{ audioBitrateText }}</span>
                </div>
                <div class="flex items-center justify-between gap-6">
                  <span class="text-neutral-400">声道</span>
                  <span class="font-mono">{{ audioInfo.numOfChannels ?? '—' }}</span>
                </div>
                <div class="flex items-center justify-between gap-6">
                  <span class="text-neutral-400">采样率</span>
                  <span class="font-mono">{{ audioInfo.sampleRate }} Hz</span>
                </div>
              </template>
              <div class="flex items-center justify-between gap-6">
                <span class="text-neutral-400">缓冲</span>
                <span class="font-mono">{{ bufferText }}</span>
              </div>
              <div class="flex items-center justify-between gap-6">
                <span class="text-neutral-400">流畅度</span>
                <span class="font-mono">{{ performanceText }}</span>
              </div>
            </div>
          </div>

          <!-- 工具栏 -->
          <div class="flex items-center justify-between gap-2 h-10 px-2 rounded-md border bg-card">
            <!-- 左：播放控制组 -->
            <div class="flex items-center gap-1">
              <button
                class="grid size-8 place-items-center rounded-md text-foreground transition-colors hover:bg-accent disabled:opacity-40"
                :disabled="!playing"
                :title="paused ? '继续' : '暂停'"
                @click="togglePause"
              >
                <component :is="paused ? Play : Pause" class="w-4 h-4" />
              </button>

              <button
                class="grid size-8 place-items-center rounded-md text-foreground transition-colors hover:bg-accent disabled:opacity-40"
                :disabled="!playing"
                title="静音"
                @click="muted ? playerRef?.cancelMute?.() : playerRef?.mute?.()"
              >
                <component :is="muted ? VolumeX : Volume2" class="w-4 h-4" />
              </button>

              <div class="w-px h-5 bg-border mx-1" />

              <button
                class="grid size-8 place-items-center rounded-md text-foreground transition-colors hover:bg-accent disabled:opacity-40"
                :disabled="!playing"
                title="刷新"
                @click="handleRefresh"
              >
                <RefreshCw class="w-4 h-4" />
              </button>
            </div>

            <!-- 中：进度条 -->
            <div class="flex-1 flex items-center gap-3 px-2 min-w-0">
              <span class="text-xs text-muted-foreground font-mono tabular-nums w-[70px] text-right">
                {{ fmtDuration(sliderValue) }}
              </span>
              <input
                type="range"
                v-model.number="sliderValue"
                :max="durationSec || 1"
                :disabled="!playing"
                class="flex-1 h-1 cursor-pointer accent-primary"
                @input="onSliderInput"
                @change="onSliderChange"
              />
              <span class="text-xs text-muted-foreground font-mono tabular-nums w-[70px]">
                {{ fmtDuration(durationSec) }}
              </span>
            </div>

            <!-- 右：码流/收流/倍速/下载/截图/全屏/信息 -->
            <div class="flex items-center gap-1">
              <!-- 倍速 -->
              <div class="relative">
                <div
                  v-if="playing && speedMenuOpen"
                  class="absolute bottom-full mb-2 right-0 bg-popover border rounded-md shadow-lg p-1 z-20 min-w-[80px]"
                >
                  <div
                    v-for="s in speedOptions"
                    :key="s"
                    class="flex cursor-pointer items-center justify-between gap-4 whitespace-nowrap rounded-sm px-2.5 py-1 text-xs hover:bg-accent"
                    :class="s === speed ? 'font-medium text-primary' : 'text-foreground/70'"
                    @click="handleSpeed(s)"
                  >
                    <span>{{ s }}x</span>
                    <Check v-if="s === speed" class="h-3.5 w-3.5 text-primary" />
                  </div>
                </div>
                <button
                  class="h-8 px-2 rounded-md text-sm transition-colors hover:bg-accent disabled:opacity-40"
                  :disabled="!playing"
                  title="倍速"
                  @click="speedMenuOpen = !speedMenuOpen"
                >
                  {{ speed }}x
                </button>
              </div>

              <div class="w-px h-5 bg-border mx-1" />

              <!-- 码流 -->
              <div class="relative">
                <button
                  class="grid size-8 place-items-center rounded-md text-foreground transition-colors hover:bg-accent disabled:opacity-40"
                  :disabled="!playing"
                  title="码流切换"
                  @click="streamMenuOpen = !streamMenuOpen"
                >
                  <Layers class="w-4 h-4" />
                </button>
                <div
                  v-if="streamMenuOpen && playing"
                  class="absolute bottom-full mb-2 right-0 bg-popover border rounded-md shadow-lg p-1 z-20 min-w-[180px] max-h-72 overflow-y-auto"
                >
                  <div
                    v-for="s in STREAMS"
                    :key="s.value"
                    class="flex cursor-pointer items-center justify-between gap-4 whitespace-nowrap rounded-sm px-2.5 py-1 text-xs hover:bg-accent"
                    :class="s.value === currentStream ? 'font-medium text-primary' : 'text-foreground/70'"
                    @click="handleSwitchStream(s.value)"
                  >
                    <span>{{ s.label }}</span>
                    <Check v-if="s.value === currentStream" class="h-3.5 w-3.5 text-primary" />
                  </div>
                </div>
              </div>

              <!-- 收流模式 -->
              <div class="relative">
                <button
                  class="grid size-8 place-items-center rounded-md text-foreground transition-colors hover:bg-accent disabled:opacity-40"
                  :disabled="!playing"
                  title="收流模式"
                  @click="transportMenuOpen = !transportMenuOpen"
                >
                  <Network class="w-4 h-4" />
                </button>
                <div
                  v-if="transportMenuOpen && playing"
                  class="absolute bottom-full mb-2 right-0 bg-popover border rounded-md shadow-lg p-1 z-20 min-w-[120px]"
                >
                  <div
                    v-for="t in TRANSPORTS"
                    :key="t.value"
                    class="flex cursor-pointer items-center justify-between gap-4 whitespace-nowrap rounded-sm px-2.5 py-1 text-xs hover:bg-accent"
                    :class="t.value === transport ? 'font-medium text-primary' : 'text-foreground/70'"
                    @click="handleSwitchTransport(t.value)"
                  >
                    <span>{{ t.label }}</span>
                    <Check v-if="t.value === transport" class="h-3.5 w-3.5 text-primary" />
                  </div>
                </div>
              </div>

              <div class="w-px h-5 bg-border mx-1" />

              <button
                class="grid size-8 place-items-center rounded-md text-foreground transition-colors hover:bg-accent disabled:opacity-40"
                :disabled="!currentRecord"
                title="下载本段"
                @click="handleDownload"
              >
                <Download class="w-4 h-4" />
              </button>

              <button
                class="grid size-8 place-items-center rounded-md text-foreground transition-colors hover:bg-accent disabled:opacity-40"
                :disabled="!playing"
                title="截图"
                @click="playerRef?.screenshot?.()"
              >
                <Camera class="w-4 h-4" />
              </button>

              <button
                class="grid size-8 place-items-center rounded-md text-foreground transition-colors hover:bg-accent disabled:opacity-40"
                :disabled="!playing"
                title="信息"
                @click="showInfoPanel = !showInfoPanel"
              >
                <Info class="w-4 h-4" />
              </button>

              <button
                class="grid size-8 place-items-center rounded-md text-foreground transition-colors hover:bg-accent disabled:opacity-40"
                :disabled="!playing"
                title="全屏"
                @click="playerRef?.fullscreen?.()"
              >
                <Maximize2 class="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 下载弹窗（内嵌，不单独弹） -->
      <Dialog v-if="showDownload" :open="showDownload" @update:open="(v) => (showDownload = v)">
        <DialogContent class="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>录像下载</DialogTitle>
            <DialogDescription />
          </DialogHeader>

          <div class="space-y-3">
            <div v-if="currentRecord" class="text-sm">
              录像段：
              <span class="font-mono text-muted-foreground">
                {{ recClock(currentRecord.startTime) }} ~ {{ recClock(currentRecord.endTime) }}
              </span>
            </div>
            <div v-if="downloadInfo?.streamId" class="text-xs text-muted-foreground font-mono break-all">
              streamId: {{ downloadInfo.streamId }}
            </div>

            <div v-if="downloadInfo" class="flex items-center gap-3">
              <span
                class="px-2.5 py-1 rounded text-xs font-medium"
                :class="downloadStatusView(downloadInfo.status).color + ' bg-neutral-100'"
              >
                {{ downloadStatusView(downloadInfo.status).text }}
              </span>
              <span v-if="downloadInfo.durationSec" class="text-xs text-muted-foreground">
                时长: {{ downloadInfo.durationSec }} 秒
              </span>
            </div>

            <div v-if="downloadInfo?.errorMsg" class="text-sm text-red-500 bg-red-50 p-3 rounded-md">
              {{ downloadInfo.errorMsg }}
            </div>

            <div v-if="downloadInfo?.status === 'READY' && downloadInfo.downloadUrl" class="space-y-2">
              <div class="text-sm text-muted-foreground">下载链接（MinIO 预签名，1 小时内有效）</div>
              <div class="flex gap-2">
                <Input :value="downloadInfo.downloadUrl" readonly size="sm" class="flex-1 font-mono text-xs" />
                <Button size="sm" variant="outline" @click="copyDownloadLink">复制</Button>
              </div>
            </div>
          </div>

          <div class="flex justify-end gap-2 mt-2">
            <Button
              v-if="downloadInfo && (downloadInfo.status === 'INVITING' || downloadInfo.status === 'RECORDING')"
              variant="destructive"
              :loading="downloadStopping"
              @click="stopDownload"
            >
              取消下载
            </Button>
            <Button :loading="downloadLoading" variant="outline" @click="refreshDownload">刷新状态</Button>
            <Button
              v-if="downloadInfo?.status === 'READY' && downloadInfo.downloadUrl"
              @click="downloadNow"
            >
              立即下载
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </DialogContent>
  </Dialog>
</template>
