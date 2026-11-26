<template>
  <div class="space-y-3">
    <!-- 播放器（jessibuca） -->
    <div class="aspect-video overflow-hidden rounded-md bg-black">
      <GbPlayer
        v-if="playUrl"
        :src="playUrl"
        :video-buffer="0.2"
        @error="onPlaybackFailure"
        @timeout="onPlaybackFailure"
        @stream-end="onPlaybackFailure"
      />
      <div v-else class="grid h-full place-items-center text-xs text-white/60">
        <div class="flex flex-col items-center gap-2">
          <IconLoader2 v-if="loading" class="size-5 animate-spin" />
          <span>{{ loading ? '正在建立点播…' : failed ? '播放失败，请重新获取地址' : '未获取到可用的播放地址' }}</span>
          <button
            v-if="failed && !loading"
            type="button"
            class="rounded border border-white/20 px-3 py-1 text-white/80 hover:bg-white/10"
            @click="start"
          >
            重新获取地址
          </button>
        </div>
      </div>
    </div>

    <!-- 播放地址 -->
    <div v-if="urls.length" class="space-y-1.5">
      <div
        v-for="item in urls"
        :key="item.label"
        class="flex items-center gap-2 rounded-md border bg-muted/40 px-2.5 py-1.5"
      >
        <span class="w-16 shrink-0 text-[11px] text-muted-foreground">{{ item.label }}</span>
        <code class="min-w-0 flex-1 truncate font-mono text-xs" :title="item.url">{{ item.url }}</code>
        <Button variant="ghost" size="xs" class="h-6 shrink-0 px-1.5" @click="copy(item.url)">
          <IconCopy class="size-3.5" />
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { toast } from '@/lib/toast'
import { IconCopy, IconLoader2 } from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import GbPlayer from '@/features/gb/GbPlayer.vue'
import { startOnvifPlayApi, stopOnvifPlayApi } from '@/api/onvif'
import type { OnvifDevice, OnvifPlayResult } from '@/types/api'

const props = defineProps<{
  device: OnvifDevice
}>()

const loading = ref(false)
const failed = ref(false)
const info = ref<OnvifPlayResult | null>(null)
let startSequence = 0

const toWss = (url?: string) =>
  url && window.location.protocol === 'https:' ? url.replace(/^ws:\/\//, 'wss://') : url

const urls = computed(() => {
  const u = info.value
  if (!u || failed.value) return []
  return [
    { label: 'WS-FLV', url: toWss(u.wsFlvUrl) },
    { label: 'HTTP-FLV', url: u.flvUrl },
    { label: 'HLS', url: u.hlsUrl },
    { label: 'WebRTC', url: u.rtcUrl },
  ].filter((x): x is { label: string; url: string } => !!x.url)
})

/** jessibuca 播放地址：优先 WS-FLV，退化为 HTTP-FLV */
const playUrl = computed(() => {
  const u = info.value
  if (!u || failed.value) return ''
  return toWss(u.wsFlvUrl) || u.flvUrl || ''
})

/** 停止当前点播（幂等，失败忽略——会话可能已被服务端清理） */
const stop = async () => {
  const streamId = info.value?.streamId
  info.value = null
  if (!streamId) return
  try {
    await stopOnvifPlayApi(streamId)
  } catch {
    // 忽略：停止是尽力而为
  }
}

const start = async () => {
  const sequence = ++startSequence
  await stop()
  if (sequence !== startSequence) return
  loading.value = true
  failed.value = false
  try {
    const result = (await startOnvifPlayApi(props.device.id)).data
    if (sequence === startSequence) {
      info.value = result
    } else if (result?.streamId && info.value?.streamId !== result.streamId) {
      // 设备已切换或组件已卸载：旧请求仍可能已拉起流，按返回的 streamId 尽力回收。
      try {
        await stopOnvifPlayApi(result.streamId)
      } catch {
        // 停止是尽力而为，不能覆盖当前设备的播放状态
      }
    }
  } catch {
    if (sequence === startSequence) failed.value = true
  } finally {
    if (sequence === startSequence) loading.value = false
  }
}

const onPlaybackFailure = () => {
  failed.value = true
}

watch(() => props.device.id, start, { immediate: true })
onBeforeUnmount(() => {
  startSequence++
  void stop()
})

const copy = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制到剪贴板')
  } catch {
    // 剪贴板权限被拒时忽略
  }
}
</script>
