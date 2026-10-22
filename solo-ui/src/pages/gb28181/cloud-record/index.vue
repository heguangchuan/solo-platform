<template>
  <div class="flex h-full flex-col overflow-hidden p-3">
    <div class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">云端录像中心</span>
        <div class="flex-1" />
        <Button
          v-access="PERM.GB_RECORD_CONTROL"
          :variant="recording ? 'destructive' : 'outline'"
          size="sm"
          class="h-7 text-xs"
          @click="recording ? openRecording() : openStart()"
        >
          <IconCircle :class="['size-3.5', recording ? 'animate-pulse' : '']" />
          {{ recording ? '录制中 · 停止' : '手动录制' }}
        </Button>
        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
      </div>

      <!-- 搜索行 -->
      <div class="flex h-12 shrink-0 items-center gap-2 px-3">
        <GbDeviceSelect v-model="deviceIdQ" />
        <input
          v-model="startTimeQ"
          type="datetime-local"
          class="h-7 w-[180px] rounded-md bg-transparent px-2 text-[13px] outline-none focus:bg-muted/70"
          title="录制开始时间起"
        />
        <span class="text-xs text-muted-foreground">至</span>
        <input
          v-model="endTimeQ"
          type="datetime-local"
          class="h-7 w-[180px] rounded-md bg-transparent px-2 text-[13px] outline-none focus:bg-muted/70"
          title="录制开始时间止"
        />
        <Select v-model="statusQ">
          <RawSelectTrigger
            class="inline-flex h-7 w-28 items-center gap-1 rounded-md px-2 text-[13px] outline-none hover:bg-muted/70"
          >
            <SelectValue placeholder="状态" />
          </RawSelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">全部状态</SelectItem>
            <SelectItem value="1">上传成功</SelectItem>
            <SelectItem value="0">上传中</SelectItem>
            <SelectItem value="2">上传失败</SelectItem>
          </SelectContent>
        </Select>

        <span v-if="hasCondition" class="text-xs text-muted-foreground">
          匹配 {{ resource.total.value }} 条
        </span>

        <div class="flex-1" />

        <Button
          v-if="hasCondition"
          variant="ghost"
          size="sm"
          class="h-7 text-xs text-muted-foreground"
          @click="onClear"
        >
          <IconX class="size-3.5" />
          清除
        </Button>
      </div>

      <!-- 列表 -->
      <div ref="scrollerRef" class="min-h-0 flex-1 overflow-auto">
        <table class="w-full table-fixed border-collapse text-left text-[13px]">
          <thead class="sticky top-0 z-10">
            <tr class="bg-card text-xs text-muted-foreground">
              <th class="w-56 px-3 py-2 font-normal">设备 / 通道</th>
              <th class="w-44 px-3 py-2 font-normal">开始时间</th>
              <th class="w-44 px-3 py-2 font-normal">结束时间</th>
              <th class="w-24 px-3 py-2 font-normal">时长</th>
              <th class="w-24 px-3 py-2 font-normal">大小</th>
              <th class="w-24 px-3 py-2 font-normal">状态</th>
              <th class="w-28 px-3 py-2 font-normal">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(f, i) in filteredItems"
              :key="f.id"
              :data-row="i"
              class="group"
              :class="cursor === i ? 'bg-muted/50' : 'hover:bg-muted/30'"
              @click="cursor = i"
            >
              <td class="px-3 py-2">
                <div class="truncate font-mono text-xs">{{ f.deviceId || '—' }}</div>
                <div class="truncate font-mono text-[11px] text-muted-foreground">
                  {{ f.channelId || '—' }}
                </div>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ f.startTime || '—' }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ f.endTime || '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ f.duration != null ? fmtDuration(f.duration) : '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ f.fileSize != null ? fmtSize(f.fileSize) : '—' }}
              </td>
              <td class="px-3 py-2">
                <span
                  class="inline-flex items-center rounded-full border px-2 py-0.5 text-xs"
                  :class="statusView(f.status).cls"
                >
                  {{ statusView(f.status).text }}
                </span>
              </td>
              <td class="px-3 py-2">
                <div class="flex items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                  <button
                    v-if="f.status === 1"
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
                    title="播放"
                    @click.stop="onPlay(f)"
                  >
                    <Play class="size-3.5" />
                  </button>
                  <button
                    v-if="f.status === 1"
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
                    title="下载"
                    @click.stop="onDownload(f)"
                  >
                    <Download class="size-3.5" />
                  </button>
                  <button
                    v-access="PERM.GB_RECORD_DELETE"
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-red-400"
                    title="删除"
                    @click.stop="onDelete(f)"
                  >
                    <Trash2 class="size-3.5" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="firstLoading" class="grid place-items-center py-16">
          <IconLoader2 class="size-5 animate-spin text-muted-foreground" />
        </div>

        <div
          v-else-if="!resource.loading.value && filteredItems.length === 0"
          class="flex flex-col items-center gap-2 py-16 text-muted-foreground"
        >
          <IconMovie class="size-8" />
          <div class="text-[13px]">暂无录像文件</div>
        </div>

        <div ref="sentinelRef" class="grid place-items-center py-4">
          <IconLoader2
            v-if="resource.loading.value && resource.items.value.length"
            class="size-4 animate-spin text-muted-foreground"
          />
          <span
            v-else-if="resource.finished.value && resource.items.value.length"
            class="text-xs text-muted-foreground"
          >
            已全部加载
          </span>
        </div>
      </div>

      <!-- 底部状态条 -->
      <div
        class="flex h-8 shrink-0 items-center gap-3 border-t border-border px-3 text-[11px] text-muted-foreground/70"
      >
        <span><kbd>J</kbd><kbd>K</kbd> 移动</span>
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 条录像</span>
      </div>
    </div>

    <!-- 播放弹窗 -->
    <Dialog v-model:open="playOpen">
      <DialogContent class="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle class="text-base">云端录像回放</DialogTitle>
          <DialogDescription />
        </DialogHeader>
        <div v-if="playRow" class="space-y-3">
          <div class="space-y-1 font-mono text-xs text-muted-foreground">
            <div class="truncate">{{ playRow.deviceId }} / {{ playRow.channelId }}</div>
            <div>{{ playRow.startTime }} ~ {{ playRow.endTime }}</div>
          </div>
          <div v-if="playError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">
            {{ playError }}
          </div>
          <div class="relative grid min-h-[240px] place-items-center overflow-hidden rounded-md bg-black">
            <IconLoader2 v-if="playLoading" class="size-6 animate-spin text-neutral-400" />
            <video
              v-else-if="playUrl"
              :src="playUrl"
              controls
              autoplay
              class="max-h-[480px] w-full"
            />
          </div>
          <div class="text-xs text-muted-foreground">播放地址为 S3 预签名链接，1 小时内有效。</div>
        </div>
      </DialogContent>
    </Dialog>

    <!-- 手动录制弹窗（开始） -->
    <Dialog v-model:open="startOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle class="text-base">手动录制</DialogTitle>
          <DialogDescription>选择设备与通道后立即开始录制，停止后录像自动上传云端。</DialogDescription>
        </DialogHeader>
        <div class="space-y-3">
          <div class="space-y-1.5">
            <Label>设备</Label>
            <div class="h-9 w-full rounded-md border border-border px-1">
              <GbDeviceSelect v-model="manual.deviceId" class="w-full" />
            </div>
          </div>
          <div class="space-y-1.5">
            <Label>通道</Label>
            <Select v-model="manual.channelId">
              <RawSelectTrigger class="h-9 w-full px-2 text-[13px]">
                <SelectValue :placeholder="channelsLoading ? '通道加载中...' : '请选择通道'" />
              </RawSelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="c in channels"
                  :key="c.channelId"
                  :value="c.channelId"
                >
                  {{ c.name || c.reportName || c.channelId }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1.5">
              <Label>收流模式</Label>
              <Select v-model="manual.transport">
                <RawSelectTrigger class="h-9 w-full px-2 text-[13px]">
                  <SelectValue placeholder="随设备" />
                </RawSelectTrigger>
                <SelectContent>
                  <SelectItem value="">随设备</SelectItem>
                  <SelectItem value="UDP">UDP</SelectItem>
                  <SelectItem value="TCP-passive">TCP 被动</SelectItem>
                  <SelectItem value="TCP-active">TCP 主动</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-1.5">
              <Label>码流</Label>
              <Select v-model="manual.streamNumber">
                <RawSelectTrigger class="h-9 w-full px-2 text-[13px]">
                  <SelectValue placeholder="主码流" />
                </RawSelectTrigger>
                <SelectContent>
                  <SelectItem value="">主码流</SelectItem>
                  <SelectItem value="stream:1">子码流</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" size="sm" @click="startOpen = false">取消</Button>
          <Button size="sm" :loading="starting" @click="onStart">开始录制</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 录制中弹窗（停止） -->
    <Dialog v-model:open="recordingOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle class="text-base">录制中</DialogTitle>
          <DialogDescription />
        </DialogHeader>
        <div class="space-y-3 text-[13px]">
          <div class="flex items-center gap-2">
            <span class="relative flex size-2.5">
              <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-400 opacity-75" />
              <span class="relative inline-flex size-2.5 rounded-full bg-red-500" />
            </span>
            <span class="text-sm">正在录制，停止后录像将上传云端。</span>
          </div>
          <div class="rounded-md bg-muted px-3 py-2 font-mono text-xs break-all">
            streamId: {{ streamId }}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" size="sm" @click="recordingOpen = false">继续录制</Button>
          <Button variant="destructive" size="sm" :loading="stopping" @click="onStop">
            停止录制
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 云端录像中心
 * - 分页查询 S3 上的录像文件（无限滚动）
 * - 成功文件可在线播放 / 下载（预签名地址，1 小时有效）
 * - 手动录制：开始后本地留存 streamId，同页可停止
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { IconLoader2, IconMovie, IconX, IconCircle } from '@tabler/icons-vue'
import { Play, Download, Trash2 } from 'lucide-vue-next'
import RefreshButton from '@/components/RefreshButton.vue'
import Button from '@/components/ui/button/Button.vue'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectValue } from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import GbDeviceSelect from '@/features/gb/GbDeviceSelect.vue'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import {
  getCloudRecordPageApi,
  getCloudRecordUrlApi,
  deleteCloudRecordApi,
  startCloudRecordApi,
  stopCloudRecordApi,
  getGbDeviceChannelPageApi,
} from '@/api/gb28181'
import type { GbRecordFile, GbDeviceChannel } from '@/types/api'

/* ---------------- 筛选 ---------------- */
const deviceIdQ = ref<string | undefined>(undefined)
const startTimeQ = ref('')
const endTimeQ = ref('')
// __all__ 占位，避免空串在 Select 里的歧义
const statusQ = ref('__all__')

const fmtTime = (v: string) => v.replace('T', ' ')

const hasCondition = computed(
  () =>
    !!deviceIdQ.value ||
    !!startTimeQ.value ||
    !!endTimeQ.value ||
    statusQ.value !== '__all__',
)

const resource = useResource<{ page?: number; pageSize?: number }, GbRecordFile>({
  fetcher: (q) =>
    getCloudRecordPageApi({
      page: q.page,
      // 后端分页字段是 size
      size: q.pageSize,
      deviceId: deviceIdQ.value || undefined,
      start: startTimeQ.value ? fmtTime(startTimeQ.value) : undefined,
      end: endTimeQ.value ? fmtTime(endTimeQ.value) : undefined,
    }),
  initial: { page: 1, pageSize: 50 },
})

// 状态筛选在前端内存里做（上传中的文件一般很少，走后端要多传一个非契约参数，不值当）
const filteredItems = computed(() =>
  statusQ.value === '__all__'
    ? resource.items.value
    : resource.items.value.filter((f) => f.status === Number(statusQ.value)),
)

const onRefresh = () => resource.load(true)

const onClear = () => {
  deviceIdQ.value = undefined
  startTimeQ.value = ''
  endTimeQ.value = ''
  statusQ.value = '__all__'
  cursor.value = 0
  resource.search()
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null
const triggerSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cursor.value = 0
    resource.search()
  }, 300)
}
watch(deviceIdQ, () => {
  cursor.value = 0
  resource.search()
})
watch([startTimeQ, endTimeQ], triggerSearch)

/* ---------------- 展示辅助 ---------------- */
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

/* ---------------- 播放 / 下载 / 删除 ---------------- */
const playOpen = ref(false)
const playRow = ref<GbRecordFile | null>(null)
const playUrl = ref('')
const playLoading = ref(false)
const playError = ref('')

async function resolveUrl(f: GbRecordFile) {
  const { data } = await getCloudRecordUrlApi(f.id)
  return data
}

const onPlay = async (f: GbRecordFile) => {
  playRow.value = f
  playUrl.value = ''
  playError.value = ''
  playLoading.value = true
  playOpen.value = true
  try {
    playUrl.value = await resolveUrl(f)
  } catch (e) {
    playError.value = e instanceof Error ? e.message : String(e)
  } finally {
    playLoading.value = false
  }
}

const onDownload = async (f: GbRecordFile) => {
  try {
    const url = await resolveUrl(f)
    // 预签名 URL 带 query 参数，直接交给浏览器下载
    const a = document.createElement('a')
    a.href = url
    a.download = ''
    a.target = '_blank'
    a.click()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

const onDelete = async (f: GbRecordFile) => {
  const ok = await confirm({
    title: '确定删除该录像文件吗？',
    description: '云端存储对象会一并删除，删除后不可恢复。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteCloudRecordApi(f.id)
  toast.success('删除成功')
  resource.removeWhere((item) => item.id === f.id)
}

/* ---------------- 手动录制 ---------------- */
const startOpen = ref(false)
const recordingOpen = ref(false)
const starting = ref(false)
const stopping = ref(false)
const streamId = ref('')
const recording = computed(() => !!streamId.value)

const manual = reactive({
  deviceId: '' as string | undefined,
  channelId: '' as string | undefined,
  transport: 'TCP-passive',
  streamNumber: '',
})

const channels = ref<GbDeviceChannel[]>([])
const channelsLoading = ref(false)

// 设备选定后拉通道（只取叶子通道）
watch(
  () => manual.deviceId,
  async (deviceId) => {
    manual.channelId = undefined
    channels.value = []
    if (!deviceId) return
    channelsLoading.value = true
    try {
      const { data } = await getGbDeviceChannelPageApi({
        page: 1,
        pageSize: 1000,
        deviceId,
        parental: 0,
      })
      channels.value = data.rows
    } finally {
      channelsLoading.value = false
    }
  },
)

function resetManual() {
  manual.deviceId = undefined
  manual.channelId = undefined
  manual.transport = 'TCP-passive'
  manual.streamNumber = ''
  channels.value = []
}

const openStart = () => {
  resetManual()
  startOpen.value = true
}

const openRecording = () => {
  recordingOpen.value = true
}

const onStart = async () => {
  if (!manual.deviceId || !manual.channelId) {
    toast.warning('请选择设备与通道')
    return
  }
  starting.value = true
  try {
    const { data } = await startCloudRecordApi({
      deviceId: manual.deviceId,
      channelId: manual.channelId,
      transport: manual.transport || undefined,
      streamNumber: manual.streamNumber || undefined,
    })
    streamId.value = data
    startOpen.value = false
    toast.success('已开始录制')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    starting.value = false
  }
}

const onStop = async () => {
  if (!streamId.value) return
  stopping.value = true
  try {
    await stopCloudRecordApi(streamId.value)
    toast.success('已停止录制，录像上传中')
    streamId.value = ''
    recordingOpen.value = false
    // 稍等后端落库，给列表刷一下
    setTimeout(() => resource.load(true), 1500)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    stopping.value = false
  }
}

/* ---------------- 键盘流 ---------------- */
const cursor = ref(0)
const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  scrollerRef.value
    ?.querySelector(`[data-row="${cursor.value}"]`)
    ?.scrollIntoView({ block: 'nearest' })
}

const isTypingTarget = (el: EventTarget | null) =>
  el instanceof HTMLElement &&
  (el.tagName === 'INPUT' ||
    el.tagName === 'TEXTAREA' ||
    el.tagName === 'SELECT' ||
    el.isContentEditable)

const onGlobalKeydown = (e: KeyboardEvent) => {
  if (isTypingTarget(e.target)) return
  const list = filteredItems.value
  switch (e.key) {
    case 'j':
    case 'J':
      e.preventDefault()
      if (list.length) {
        cursor.value = Math.min(list.length - 1, cursor.value + 1)
        scrollCursorIntoView()
      }
      break
    case 'k':
    case 'K':
      e.preventDefault()
      cursor.value = Math.max(0, cursor.value - 1)
      scrollCursorIntoView()
      break
  }
}

/* ---------------- 生命周期 ---------------- */
let observer: IntersectionObserver | null = null

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown)
  await resource.load(true)
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) resource.load(false)
    },
    { root: scrollerRef.value, rootMargin: '80px' },
  )
  if (sentinelRef.value) observer.observe(sentinelRef.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  observer?.disconnect()
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped>
kbd {
  display: inline-block;
  border: 1px solid var(--border);
  border-radius: 3px;
  background: var(--muted);
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  color: var(--muted-foreground);
  font-family: ui-monospace, monospace;
}
</style>
