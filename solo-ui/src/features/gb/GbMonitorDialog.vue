<script setup lang="ts">
/**
 * 国标监控大窗口（基础能力）。
 * - 播放器能力全部交给 GbPlayer（工具栏、PTZ、电子放大、信息面板、截图、全屏等）
 * - 本组件只管：点播生命周期、弹窗骨架、通道信息
 * - 仅提供单路基础监控；多路分屏等组合由业务方基于本组件自行实现。
 */
import { computed, ref, watch } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from '@/components/ui/dialog'
import GbPlayer from '@/features/gb/GbPlayer.vue'
import {
  startGbPlayApi,
  stopGbPlayApi,
  ptzControlApi,
  ptzFocusApi,
  ptzIrisApi,
  ptzWiperApi,
  listPtzPresetApi,
  savePtzPresetApi,
  deletePtzPresetApi,
  callPtzPresetApi,
  listPtzCruiseApi,
  getPtzCruiseApi,
  savePtzCruiseApi,
  updatePtzCruiseApi,
  deletePtzCruiseApi,
  addPtzCruisePointApi,
  removePtzCruisePointApi,
  startPtzCruiseApi,
  stopPtzCruiseApi,
} from '@/api/gb28181'
import type {
  GbDeviceChannel,
  GbPtzCommand,
  GbPtzCruise,
  GbPtzCruisePoint,
  GbPtzPreset,
} from '@/types/api'

const props = defineProps<{
  channel?: GbDeviceChannel
  /**
   * 复用已有流模式：同一路流已被其他观看者（如检视器小预览）点播。
   * - start 时直接取共享流地址（不触发 stop→start 竞态）
   * - 关闭弹窗不调用 stop，避免切断其他观看者；流的释放由后端引用计数决定
   */
  reuseStream?: boolean
}>()
const open = defineModel<boolean>('open', { default: false })

/* ---------------- 码流档位 ---------------- */

const STREAMS = [
  { label: '主码流', value: 'stream:0' },
  { label: '子码流', value: 'stream:1' },
  { label: '主码流-2022', value: 'streamnumber:0' },
  { label: '子码流-2022', value: 'streamnumber:1' },
  { label: '主码流-大华', value: 'streamprofile:0' },
  { label: '子码流-大华', value: 'streamprofile:1' }, 
  { label: '主码流-水星/TP-LINK', value: 'streamMode:main' },
  { label: '子码流-水星/TP-LINK', value: 'streamMode:sub' },
]

/* ---------------- 点播状态 ---------------- */

const playerRef = ref<InstanceType<typeof GbPlayer> | null>(null)
const playUrl = ref('')
const loading = ref(false)
const isPlaying = ref(false)
const currentStream = ref('')
const transport = ref<'UDP' | 'TCP-passive' | 'TCP-active'>('TCP-passive')
let session: { streamId: string } | null = null
/** 当前会话是否由本弹窗新建；复用模式接入的共享流不归本弹窗释放 */
let ownsSession = false
/** 进行中的点播请求，用于「停止」时中断 */
let abortController: AbortController | null = null

/* ---------------- 预置位 ---------------- */

const presets = ref<GbPtzPreset[]>([])

const fetchPresets = async () => {
  if (!props.channel?.id) {
    presets.value = []
    return
  }
  try {
    const { data } = await listPtzPresetApi(props.channel.id)
    presets.value = data ?? []
  } catch {
    presets.value = []
  }
}

const channelName = computed(
  () => props.channel?.name || props.channel?.reportName || props.channel?.channelId || '',
)
const showPtz = computed(() => props.channel?.ptzType !== 3)

/**
 * 恢复到空闲界面（纯本地）：中断未完成请求、清空播放地址与状态，
 * 不调用流媒体停止接口，工具栏始终可用。出错/超时/流结束/手动停止都走这里。
 */
const resetToIdle = () => {
  abortController?.abort()
  abortController = null
  session = null
  ownsSession = false
  playUrl.value = ''
  loading.value = false
  isPlaying.value = false
}

const start = async () => {
  if (!props.channel || loading.value) return
  loading.value = true
  abortController?.abort()
  const controller = new AbortController()
  abortController = controller
  try {
    const { data } = await startGbPlayApi(
      {
        deviceId: props.channel.deviceId ?? '',
        channelId: props.channel.channelId,
        transport: transport.value,
        streamNumber: currentStream.value || undefined,
      },
      controller.signal,
    )
    // 请求返回期间已被中断（手动停止/关闭弹窗）：丢弃结果
    if (abortController !== controller) return
    const url = data.wsFlvUrl || data.flvUrl
    if (!url) {
      resetToIdle()
      return
    }
    session = data
    ownsSession = !props.reuseStream
    // 地址变化交给 :src 监听内部切换；地址未变（如刷新）则命令式强制重拉
    if (url === playUrl.value) playerRef.value?.play?.(url)
    else playUrl.value = url
  } catch (e) {
    // 主动中断：不处理；其余异常直接回到空闲界面（不弹遮罩）
    if (e instanceof Error && e.name === 'CanceledError') return
    resetToIdle()
  } finally {
    // 仅当本次请求仍是当前请求时清理，避免覆盖中断后的状态
    if (abortController === controller) {
      loading.value = false
      abortController = null
    }
  }
}

/** 关闭弹窗：本地复位；自有会话才通知后端停止，共享流不停止 */
const teardown = () => {
  const s = session
  const owned = ownsSession
  resetToIdle()
  if (s && owned) stopGbPlayApi({ streamId: s.streamId }).catch(() => {})
}

/** 工具栏「停止」：仅恢复到空闲界面，不调用流媒体停止接口 */
const onStop = () => resetToIdle()

const restart = async () => {
  if (loading.value) return
  const s = session
  const owned = ownsSession
  session = null
  ownsSession = false
  isPlaying.value = false
  // 共享流只在本地断开，不发 stop；重新 start 后会话仍不归本弹窗所有
  if (s && owned) await stopGbPlayApi({ streamId: s.streamId }).catch(() => {})
  // 不置空 playUrl：保持 GbPlayer 挂载，切码流/收流时不退出全屏；新地址返回后由 src 监听内部切换
  await start()
}

watch(open, (v) => {
  if (v) {
    void start()
    void fetchPresets()
    void fetchCruises()
  } else {
    teardown()
    presets.value = []
    cruises.value = []
    cruisePoints.value = []
  }
})

/* ---------------- 播放器事件 ---------------- */

const onPlayerError = () => resetToIdle()
const onPlayerTimeout = () => resetToIdle()
const onStreamEnd = () => resetToIdle()

/* ---------------- 码流 / 收流切换 ---------------- */

const onSwitchStream = (value: string) => {
  currentStream.value = value
  void restart()
}
const onSwitchTransport = (value: 'UDP' | 'TCP-passive' | 'TCP-active') => {
  transport.value = value
  void restart()
}

/* ---------------- PTZ ---------------- */

const ptzSpeeds = ref({ move: 100, zoom: 5, focus: 5, iris: 5 })
const deviceId = computed(() => props.channel?.deviceId ?? '')
const channelId = computed(() => props.channel?.channelId ?? '')

// 方向命令映射（GbPlayer 内部用连字符格式，转成后端命令）
const CMD_MAP: Record<string, GbPtzCommand> = {
  up: 'up',
  down: 'down',
  left: 'left',
  right: 'right',
  'up-left': 'upleft',
  'up-right': 'upright',
  'down-left': 'downleft',
  'down-right': 'downright',
  'zoom-in': 'zoomin',
  'zoom-out': 'zoomout',
  stop: 'stop',
}

const onPtz = async (direction: string, pressing: boolean) => {
  if (!deviceId.value || !channelId.value) return
  const cmd: GbPtzCommand = pressing ? (CMD_MAP[direction] ?? 'stop') : 'stop'
  try {
    await ptzControlApi(
      deviceId.value,
      channelId.value,
      cmd,
      ptzSpeeds.value.move,
      ptzSpeeds.value.zoom,
    )
  } catch {
    /* 忽略 */
  }
}

const onFocus = async (direction: 'near' | 'far' | 'stop', _speed: number) => {
  if (!deviceId.value || !channelId.value) return
  try {
    await ptzFocusApi(deviceId.value, channelId.value, direction, ptzSpeeds.value.focus)
  } catch {
    /* 忽略 */
  }
}

const onIris = async (direction: 'open' | 'close' | 'stop', _speed: number) => {
  if (!deviceId.value || !channelId.value) return
  try {
    await ptzIrisApi(deviceId.value, channelId.value, direction, ptzSpeeds.value.iris)
  } catch {
    /* 忽略 */
  }
}

const onWiper = async (action: 'on' | 'off') => {
  if (!deviceId.value || !channelId.value) return
  try {
    await ptzWiperApi(deviceId.value, channelId.value, action)
  } catch {
    /* 忽略 */
  }
}

const onSpeedChange = (speeds: { move: number; zoom: number; focus: number; iris: number }) => {
  ptzSpeeds.value = speeds
}

/* ---------------- 预置位 ---------------- */

const onPresetSave = async (presetId: number) => {
  if (!props.channel?.id) return
  try {
    await savePtzPresetApi({ channelDbId: props.channel.id, presetId })
    await fetchPresets()
  } catch {
    /* 忽略 */
  }
}

const onPresetCall = async (id: string) => {
  try {
    await callPtzPresetApi(id)
  } catch {
    /* 忽略 */
  }
}

const onPresetDelete = async (id: string) => {
  try {
    await deletePtzPresetApi(id)
    await fetchPresets()
  } catch {
    /* presetId 仅用于前端按钮定位，删除按 id 即可 */
  }
}

/* ---------------- 巡航 ---------------- */

const cruises = ref<GbPtzCruise[]>([])
const cruisePoints = ref<GbPtzCruisePoint[]>([])

const fetchCruises = async () => {
  if (!props.channel?.id) {
    cruises.value = []
    return
  }
  try {
    const { data } = await listPtzCruiseApi(props.channel.id)
    cruises.value = data ?? []
  } catch {
    cruises.value = []
  }
}

const fetchCruisePoints = async (id: string) => {
  try {
    const { data } = await getPtzCruiseApi(id)
    cruisePoints.value = data?.points ?? []
  } catch {
    cruisePoints.value = []
  }
}

const onCruiseSelect = (id: string) => {
  void fetchCruisePoints(id)
}

const onCruiseSave = async (data: {
  name: string
  speed: number
  staySeconds: number
  points: Array<{ presetId: number; staySeconds?: number | null }>
}) => {
  if (!props.channel?.id) return
  try {
    await savePtzCruiseApi({ ...data, channelDbId: props.channel.id })
    await fetchCruises()
  } catch {
    /* 忽略 */
  }
}

const onCruiseUpdate = async (id: string, data: Partial<GbPtzCruise>) => {
  try {
    await updatePtzCruiseApi({ id, ...data })
    await fetchCruises()
  } catch {
    /* 忽略 */
  }
}

const onCruiseDelete = async (id: string) => {
  cruisePoints.value = []
  try {
    await deletePtzCruiseApi(id)
    await fetchCruises()
  } catch {
    /* 忽略 */
  }
}

const onCruiseAddPoint = async (cruiseId: string, presetId: number, staySeconds?: number | null) => {
  try {
    await addPtzCruisePointApi(cruiseId, { presetId, staySeconds })
    await fetchCruisePoints(cruiseId)
  } catch {
    /* 忽略 */
  }
}

const onCruiseRemovePoint = async (cruiseId: string, presetId: number) => {
  try {
    await removePtzCruisePointApi(cruiseId, presetId)
    await fetchCruisePoints(cruiseId)
  } catch {
    /* 忽略 */
  }
}

const onCruiseStart = async (id: string) => {
  try {
    await startPtzCruiseApi(id)
  } catch {
    /* 忽略 */
  }
}

const onCruiseStop = async (id: string) => {
  try {
    await stopPtzCruiseApi(id)
  } catch {
    /* 忽略 */
  }
}

/* ---------------- 录制 ---------------- */
/* 文件由 Jessibuca 在停止录制时自动下载到浏览器默认下载目录 */
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      class="flex max-w-[calc(100%-2rem)] flex-col gap-0 overflow-hidden p-0 sm:max-w-5xl"
      :show-close-button="false"
    >
      <DialogTitle class="sr-only">监控：{{ channelName }}</DialogTitle>

      <!-- 标题栏 -->
      <header class="flex h-12 shrink-0 items-center gap-3 border-b px-4">
        <span class="truncate text-sm font-medium">{{ channelName }}</span>
        <span
          v-if="isPlaying"
          class="flex items-center gap-1 text-[10px] font-medium text-emerald-500"
        >
          <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-500" />
          LIVE
        </span>
        <button
          class="ml-auto grid size-7 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          title="关闭"
          @click="open = false"
        >
          <svg class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M18 6 6 18M6 6l12 12" />
          </svg>
        </button>
      </header>

      <!-- 主体 -->
      <div class="p-4">
        <div class="relative aspect-video w-full overflow-hidden rounded-md border bg-black">
          <GbPlayer
            ref="playerRef"
            :src="playUrl"
            :video-buffer="0.2"
            :streams="STREAMS"
            :current-stream="currentStream"
            :show-transport="true"
            :transport="transport"
            :show-ptz="showPtz"
            :presets="presets"
            :show-presets="showPtz"
            :cruises="cruises"
            :cruise-points="cruisePoints"
            :show-cruises="showPtz"
            :show-refresh-btn="true"
            :show-stop-btn="true"
            :show-record-btn="true"
            @playing="isPlaying = true"
            @pause="isPlaying = false"
            @error="onPlayerError"
            @stream-end="onStreamEnd"
            @timeout="onPlayerTimeout"
            @switch-stream="onSwitchStream"
            @switch-transport="onSwitchTransport"
            @refresh="restart"
            @stop="onStop"
            @ptz="onPtz"
            @focus="onFocus"
            @iris="onIris"
            @wiper="onWiper"
            @speed-change="onSpeedChange"
            @preset-save="onPresetSave"
            @preset-delete="onPresetDelete"
            @preset-call="onPresetCall"
            @cruise-select="onCruiseSelect"
            @cruise-save="onCruiseSave"
            @cruise-update="onCruiseUpdate"
            @cruise-delete="onCruiseDelete"
            @cruise-add-point="onCruiseAddPoint"
            @cruise-remove-point="onCruiseRemovePoint"
            @cruise-start="onCruiseStart"
            @cruise-stop="onCruiseStop"
          />

          <!-- 加载态（底部留出工具栏区域，且不拦截工具栏交互） -->
          <div
            v-if="loading"
            class="pointer-events-none absolute inset-0 bottom-9 grid place-items-center bg-black/60 text-xs text-muted-foreground"
          >
            <RefreshCw class="mr-2 size-4 animate-spin" />
            正在拉流…
          </div>

          <!-- 空闲态：不遮挡工具栏，可用刷新按钮重新点播 -->
          <div
            v-if="!loading && !playUrl"
            class="pointer-events-none absolute inset-0 bottom-9 grid place-items-center text-xs text-muted-foreground"
          >
            暂无播放
          </div>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
