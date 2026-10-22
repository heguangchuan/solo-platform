<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconMovie class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold font-mono">
          {{ file?.channelId || '录像文件' }}
        </div>
        <div class="truncate text-xs text-muted-foreground font-mono">
          {{ file?.deviceId || '—' }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 回放（仅上传成功） -->
    <template v-if="file && file.status === 1">
      <section>
        <div class="mb-3 flex items-center justify-between">
          <h4 class="text-[13px] font-medium">录像回放</h4>
          <Button variant="ghost" size="sm" class="h-7 text-xs" @click="onDownload">
            <IconDownload class="size-3.5" />
            下载
          </Button>
        </div>
        <div v-if="urlError" class="rounded-md bg-red-50 px-3 py-2 text-xs text-red-600">
          {{ urlError }}
        </div>
        <div class="relative grid min-h-[200px] place-items-center overflow-hidden rounded-md bg-black">
          <IconLoader2 v-if="urlLoading" class="size-6 animate-spin text-neutral-400" />
          <video
            v-else-if="playUrl"
            :src="playUrl"
            controls
            autoplay
            class="max-h-[360px] w-full"
          />
          <div v-else class="flex flex-col items-center gap-2 py-8 text-muted-foreground">
            <Play class="size-6" />
            <Button size="sm" variant="outline" class="h-7 text-xs" @click="loadUrl">
              加载播放地址
            </Button>
          </div>
        </div>
        <p class="mt-2 text-[11px] text-muted-foreground">播放地址为 S3 预签名链接，1 小时内有效。</p>
      </section>

      <div class="my-4 h-px bg-border" />
    </template>

    <!-- 文件信息 -->
    <section v-if="file">
      <h4 class="mb-3.5 text-[13px] font-medium">文件信息</h4>
      <div class="space-y-2 text-xs">
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">状态</span>
          <span
            class="inline-flex items-center rounded-full border px-2 py-0.5"
            :class="statusView(file.status).cls"
          >
            {{ statusView(file.status).text }}
          </span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">开始时间</span>
          <span class="truncate font-mono">{{ file.startTime || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">结束时间</span>
          <span class="truncate font-mono">{{ file.endTime || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">时长</span>
          <span class="font-mono">{{ file.duration != null ? fmtDuration(file.duration) : '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">文件大小</span>
          <span class="font-mono">{{ file.fileSize != null ? fmtSize(file.fileSize) : '—' }}</span>
        </div>
        <div v-if="file.streamId" class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">流 ID</span>
          <span class="truncate font-mono" :title="file.streamId">{{ file.streamId }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">入库时间</span>
          <span class="truncate font-mono">{{ file.createTime || '—' }}</span>
        </div>
        <div v-if="file.status === 2 && file.failReason" class="pt-1">
          <div class="mb-1 text-muted-foreground">失败原因</div>
          <div class="rounded-md bg-red-50 px-2.5 py-1.5 text-[11px] leading-relaxed text-red-600">
            {{ file.failReason }}
          </div>
        </div>
      </div>
    </section>

    <!-- 危险操作 -->
    <template v-if="file">
      <div class="my-4 h-px bg-border" />
      <section>
        <h4 class="mb-2 text-[13px] font-medium text-destructive">危险操作</h4>
        <p class="mb-3 text-[11px] text-muted-foreground">
          删除会一并清理云端存储对象，操作不可恢复。
        </p>
        <Button
          v-access="PERM.GB_RECORD_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="size-3.5" />
          删除该录像
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { IconMovie, IconX, IconTrash, IconDownload, IconLoader2 } from '@tabler/icons-vue'
import { Play } from 'lucide-vue-next'
import Button from '@/components/ui/button/Button.vue'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import {
  getCloudRecordUrlApi,
  deleteCloudRecordApi,
} from '@/api/gb28181'
import type { GbRecordFile } from '@/types/api'

const props = defineProps<{
  file?: GbRecordFile
}>()
const emit = defineEmits<{
  close: []
  deleted: [file: GbRecordFile]
}>()

const playUrl = ref('')
const urlLoading = ref(false)
const urlError = ref('')

const loadUrl = async () => {
  if (!props.file) return
  playUrl.value = ''
  urlError.value = ''
  urlLoading.value = true
  try {
    const { data } = await getCloudRecordUrlApi(props.file.id)
    playUrl.value = data
  } catch (e) {
    urlError.value = e instanceof Error ? e.message : String(e)
  } finally {
    urlLoading.value = false
  }
}

// 切换行时重置；成功文件自动取地址，点开即看
watch(
  () => props.file?.id,
  () => {
    playUrl.value = ''
    urlError.value = ''
    if (props.file?.status === 1) loadUrl()
  },
  { immediate: true },
)

const onDownload = async () => {
  if (!props.file) return
  try {
    // 复用已取到的地址，没有就现取
    const url = playUrl.value || (await getCloudRecordUrlApi(props.file.id)).data
    const a = document.createElement('a')
    a.href = url
    a.download = ''
    a.target = '_blank'
    a.click()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

const onDelete = async () => {
  if (!props.file) return
  const ok = await confirm({
    title: '确定删除该录像文件吗？',
    description: '云端存储对象会一并删除，删除后不可恢复。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteCloudRecordApi(props.file.id)
  toast.success('删除成功')
  emit('deleted', props.file)
}

const statusView = (status: number) => {
  if (status === 1) return { text: '上传成功', cls: 'border-transparent bg-green-50 text-green-600' }
  if (status === 2) return { text: '上传失败', cls: 'border-transparent bg-red-50 text-red-600' }
  return { text: '上传中', cls: 'border-transparent bg-blue-50 text-blue-600' }
}

function fmtDuration(sec: number) {
  const s = Math.max(0, Math.floor(sec))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = s % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(ss)}`
}

function fmtSize(bytes: number) {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}
</script>
