<template>
  <div class="relative overflow-hidden rounded-md bg-black">
    <video
      ref="videoRef"
      class="block w-full bg-black"
      :style="{ height: typeof height === 'number' ? `${height}px` : height }"
      muted
      playsinline
    />

    <!-- 加载 / 错误 / 流结束 状态层 -->
    <div
      v-if="status !== 'playing'"
      class="absolute inset-0 grid place-items-center text-xs"
      :class="status === 'error' ? 'text-red-400' : 'text-white/60'"
    >
      <div class="flex flex-col items-center gap-2">
        <IconLoader2 v-if="status === 'loading'" class="size-5 animate-spin" />
        <IconPlugConnectedX v-else-if="status === 'error'" class="size-6" />
        <IconPlayerStop v-else class="size-6" />
        <span>{{ statusText }}</span>
        <Button
          v-if="status !== 'loading'"
          variant="secondary"
          size="xs"
          class="h-7 text-xs"
          @click="reload"
        >
          <IconRefresh class="size-3" />
          重新加载
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconLoader2,
  IconRefresh,
  IconPlugConnectedX,
  IconPlayerStop,
} from '@tabler/icons-vue'
import type Mpegts from 'mpegts.js'
import Button from '@/components/ui/button/Button.vue'

const props = withDefaults(
  defineProps<{
    url: string
    height?: number | string
  }>(),
  { height: 420 },
)

type PlayStatus = 'loading' | 'playing' | 'ended' | 'error'
const status = ref<PlayStatus>('loading')
const videoRef = ref<HTMLVideoElement>()

let mpegtsModule: typeof import('mpegts.js') | null = null
let player: Mpegts.Player | null = null

const statusText = computed(() => {
  switch (status.value) {
    case 'loading':
      return '正在连接视频流…'
    case 'ended':
      return '视频流已结束'
    case 'error':
      return '视频流连接失败'
    default:
      return ''
  }
})

const destroyPlayer = () => {
  if (player) {
    try {
      player.destroy()
    } catch {
      // ignore
    }
    player = null
  }
}

const startPlay = async () => {
  if (!props.url) {
    status.value = 'error'
    return
  }
  destroyPlayer()
  status.value = 'loading'

  // 动态引入，避免 mpegts.js 在 SSR / 非浏览器环境被求值
  if (!mpegtsModule) mpegtsModule = await import('mpegts.js')
  if (!mpegtsModule.default.isSupported()) {
    status.value = 'error'
    return
  }

  const p = mpegtsModule.default.createPlayer(
    { type: 'flv', isLive: true, url: props.url, hasAudio: true, hasVideo: true },
    {
      enableWorker: true,
      enableStashBuffer: false,
      liveBufferLatencyChasing: true,
      liveBufferLatencyMaxLatency: 1.5,
      liveBufferLatencyMinRemain: 0.2,
    },
  )
  player = p
  p.attachMediaElement(videoRef.value!)
  p.on('playing', () => {
    status.value = 'playing'
  })
  p.on(
    'error',
    () => {
      if (status.value === 'playing') status.value = 'ended'
      else status.value = 'error'
    },
  )
  p.load()
  try {
    await videoRef.value?.play()
  } catch {
    // 自动播放被拦截时静音播放策略通常已足够；忽略
  }
}

const reload = () => startPlay()

watch(() => props.url, startPlay)

onMounted(startPlay)
onBeforeUnmount(destroyPlayer)
</script>
