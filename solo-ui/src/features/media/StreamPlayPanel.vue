<template>
  <div class="space-y-3">
    <!-- 头部：标题 + 关闭（内嵌检视器时隐藏） -->
    <div v-if="showHeader" class="flex items-center gap-2">
      <span class="shrink-0 text-[13px] font-medium">播放</span>
      <span class="min-w-0 truncate font-mono text-xs text-muted-foreground" :title="title">
        {{ title }}
      </span>
      <div class="flex-1" />
      <button
        type="button"
        class="grid size-6 shrink-0 place-items-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
        title="关闭"
        @click="emit('close')"
      >
        <IconX class="size-3.5" />
      </button>
    </div>

    <!-- 播放器（jessibuca） -->
    <div class="aspect-video overflow-hidden rounded-md bg-black">
      <GbPlayer v-if="playUrl" :src="playUrl" :video-buffer="0.2" />
      <div v-else class="grid h-full place-items-center text-xs text-white/60">
        <div class="flex flex-col items-center gap-2">
          <IconLoader2 v-if="loading" class="size-5 animate-spin" />
          <span>{{ loading ? '正在获取播放地址…' : '未获取到可用的播放地址' }}</span>
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
import { computed, ref, watch } from 'vue'
import { toast } from '@/lib/toast'
import { IconCopy, IconLoader2, IconX } from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import GbPlayer from '@/features/gb/GbPlayer.vue'
import { getStreamProxyPlayUrlsApi, getStreamPushPlayUrlsApi } from '@/api/media'
import type { StreamPlayUrl } from '@/types/api'

export type StreamPlayKind = 'proxy' | 'push'

const props = withDefaults(
  defineProps<{
    kind: StreamPlayKind
    target: { id: string; app: string; stream: string }
    /** 独立面板模式显示标题栏；内嵌检视器时传 false */
    showHeader?: boolean
  }>(),
  { showHeader: true },
)

const emit = defineEmits<{ close: [] }>()

const loading = ref(false)
const info = ref<StreamPlayUrl | null>(null)

const title = computed(() => `${props.target.app}/${props.target.stream}`)

const toWss = (url?: string) =>
  url && window.location.protocol === 'https:' ? url.replace(/^ws:\/\//, 'wss://') : url

const urls = computed(() => {
  const u = info.value
  if (!u) return []
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
  if (!u) return ''
  return toWss(u.wsFlvUrl) || u.flvUrl || ''
})

const loadUrls = async () => {
  loading.value = true
  info.value = null
  try {
    const res =
      props.kind === 'proxy'
        ? await getStreamProxyPlayUrlsApi(props.target.id)
        : await getStreamPushPlayUrlsApi(props.target.id)
    info.value = res.data
  } finally {
    loading.value = false
  }
}

watch(() => props.target.id, loadUrls, { immediate: true })

const copy = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制到剪贴板')
  } catch {
    // 剪贴板权限被拒时忽略
  }
}
</script>
