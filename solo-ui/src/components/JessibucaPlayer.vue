<script setup lang="ts">
/**
 * Jessibuca 播放器封装组件
 * <p>父组件通过 ref 调用 {@code play(url)} / {@code stop()} / {@code destroy()}，
 * 父组件销毁时也会自动 destroy（避免离开页面后 WS 还在跑）。</p>
 * <p>jessibuca 本体走 {@code public/static/js/jessibuca/jessibuca.js} 静态脚本，
 * window.Jessibuca 必须在 mounted 时已就绪。</p>
 */
import { onBeforeUnmount, ref } from 'vue'
import type { JessibucaInstance } from '@/types/jessibuca'

const props = withDefaults(
  defineProps<{
    /** 是否需要音频解码（用于设备 hasAudio 提示） */
    hasAudio?: boolean
    /** 容器高度（默认 360px） */
    height?: string
  }>(),
  { hasAudio: true, height: '360px' },
)

const emit = defineEmits<{
  playing: []
  pause: []
  error: [msg: string]
  timeout: []
}>()

const containerRef = ref<HTMLDivElement | null>(null)
let player: JessibucaInstance | null = null
let pendingUrl: string | null = null

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
    operateBtns: {
      fullscreen: true,
      screenshot: true,
      play: true,
      audio: true,
      recorder: false,
    },
  })
  player.on('play', () => emit('playing'))
  player.on('pause', () => emit('pause'))
  player.on('error', (e: unknown) => {
    console.error('[jessibuca error]', e)
    emit('error', '播放器错误: ' + String(e))
  })
  player.on('timeout', () => emit('timeout'))
  player.on('loadingTimeout', () => emit('timeout'))
  return player
}

function play(url: string) {
  pendingUrl = url
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
}

onBeforeUnmount(() => {
  destroy()
})

defineExpose({ play, pause, stop, destroy })
</script>

<template>
  <div ref="containerRef" class="jessibuca-container" :style="{ height: props.height }"></div>
</template>

<style scoped>
.jessibuca-container {
  width: 100%;
  background: #000;
  border-radius: 4px;
  overflow: hidden;
}
</style>
