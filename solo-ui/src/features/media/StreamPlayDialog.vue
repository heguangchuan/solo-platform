<template>
  <DialogRoot :open="visible" @update:open="(v: boolean) => !v && onClose()">
    <DialogContent class="max-w-2xl gap-3">
      <DialogHeader>
        <DialogTitle>播放 · {{ title }}</DialogTitle>
      </DialogHeader>

      <div class="space-y-3">
        <div v-if="playUrl" class="h-[420px] overflow-hidden rounded-md">
          <GbPlayer :src="playUrl" :video-buffer="0.2" />
        </div>

        <!-- 播放地址 -->
        <div v-if="urls.length" class="space-y-1.5">
          <div
            v-for="item in urls"
            :key="item.label"
            class="flex items-center gap-2 rounded-md border bg-muted/40 px-2.5 py-1.5"
          >
            <span class="w-16 shrink-0 text-[11px] text-muted-foreground">{{ item.label }}</span>
            <code class="min-w-0 flex-1 truncate font-mono text-xs">{{ item.url }}</code>
            <Button variant="ghost" size="xs" class="h-6 shrink-0 px-1.5" @click="copy(item.url)">
              <IconCopy class="size-3.5" />
            </Button>
          </div>
        </div>

        <p v-else-if="!loading" class="py-2 text-center text-xs text-muted-foreground">
          未获取到可用的播放地址
        </p>
      </div>
    </DialogContent>
  </DialogRoot>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { toast } from '@/lib/toast'
import { IconCopy } from '@tabler/icons-vue'
import {
  DialogRoot,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import Button from '@/components/ui/button/Button.vue'
import GbPlayer from '@/features/gb/GbPlayer.vue'
import { getStreamProxyPlayUrlsApi, getStreamPushPlayUrlsApi } from '@/api/media'
import type { StreamPlayUrl } from '@/types/api'

type Kind = 'proxy' | 'push'

const visible = ref(false)
const loading = ref(false)
const kind = ref<Kind>('proxy')
const id = ref('')
const title = ref('')
const info = ref<StreamPlayUrl | null>(null)

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

const playUrl = computed(() => {
  const u = info.value
  if (!u) return ''
  const ws = toWss(u.wsFlvUrl)
  return ws || u.flvUrl || ''
})

const toWss = (url?: string) =>
  url && window.location.protocol === 'https:' ? url.replace(/^ws:\/\//, 'wss://') : url

const open = async (row: { id: string; app: string; stream: string; status?: number }, k: Kind) => {
  if (k === 'proxy' && row.status !== 1) {
    toast.warning('代理未在推流，请先启用')
    return
  }
  kind.value = k
  id.value = row.id
  title.value = `${row.app}/${row.stream}`
  info.value = null
  visible.value = true
  await nextTick()
  await loadUrls()
}

const loadUrls = async () => {
  loading.value = true
  try {
    const res =
      kind.value === 'proxy'
        ? await getStreamProxyPlayUrlsApi(id.value)
        : await getStreamPushPlayUrlsApi(id.value)
    info.value = res.data
  } finally {
    loading.value = false
  }
}

const copy = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制到剪贴板')
  } catch {
    // 剪贴板权限被拒时忽略
  }
}

const onClose = () => {
  visible.value = false
  info.value = null
}

defineExpose({ open })
</script>
