<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">云端录像中心</span>
        <div class="flex-1" />
        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
      </div>

      <!-- 搜索行 -->
      <div class="flex h-12 shrink-0 items-center gap-2 px-3">
        <GbDeviceSelect v-model="deviceIdQ" />
        <GbChannelSelect v-model="channelIdQ" :device-id="deviceIdQ" />
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
              <th class="w-44 px-3 py-2 font-normal">设备</th>
              <th class="w-44 px-3 py-2 font-normal">通道</th>
              <th class="w-44 px-3 py-2 font-normal">开始时间</th>
              <th class="w-44 px-3 py-2 font-normal">结束时间</th>
              <th class="w-24 px-3 py-2 font-normal">时长</th>
              <th class="w-24 px-3 py-2 font-normal">大小</th>
              <th class="w-24 px-3 py-2 font-normal">
                <span class="inline-flex items-center gap-1">
                  状态
                  <Select v-model="statusQ">
                    <RawSelectTrigger
                      title="按状态筛选"
                      class="inline-flex size-4 items-center justify-center rounded outline-none hover:text-foreground"
                    >
                      <IconFilter
                        class="size-3.5"
                        :class="
                          statusQ !== '__all__'
                            ? 'text-primary'
                            : 'text-muted-foreground/60'
                        "
                      />
                    </RawSelectTrigger>
                    <SelectContent>
                      <SelectItem value="__all__">全部状态</SelectItem>
                      <SelectItem value="1">上传成功</SelectItem>
                      <SelectItem value="0">上传中</SelectItem>
                      <SelectItem value="2">上传失败</SelectItem>
                    </SelectContent>
                  </Select>
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(f, i) in resource.items.value"
              :key="f.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                selected?.id === f.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(f, i)"
            >
              <td class="relative px-3 py-2 text-xs" :title="f.deviceId">
                <span
                  v-if="selected?.id === f.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <span class="block truncate">{{ f.deviceName || f.deviceId || '—' }}</span>
              </td>
              <td class="truncate px-3 py-2 text-xs text-muted-foreground" :title="f.channelId">
                {{ f.channelName || f.channelId || '—' }}
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
            </tr>
          </tbody>
        </table>

        <div v-if="firstLoading" class="grid place-items-center py-16">
          <IconLoader2 class="size-5 animate-spin text-muted-foreground" />
        </div>

        <div
          v-else-if="!resource.loading.value && resource.items.value.length === 0"
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
        <span><kbd>Enter</kbd> 查看</span>
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 条录像</span>
      </div>
    </div>

    <!-- 右侧：检视器（回放 / 下载 / 元数据 / 删除） -->
    <Transition name="inspector">
      <aside
        v-if="selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <GbCloudRecordInspector
            :file="selected"
            @close="onCloseInspector"
            @deleted="onDeleted"
          />
        </div>
      </aside>
    </Transition>
  </div>
</template>

<script setup lang="ts">
/**
 * 云端录像中心
 * - 分页查询 S3 上的录像文件（无限滚动）
 * - 行点击打开右侧 Inspector：在线回放 / 下载预签名地址 / 元数据 / 删除
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { IconLoader2, IconMovie, IconX, IconFilter } from '@tabler/icons-vue'
import RefreshButton from '@/components/RefreshButton.vue'
import { Select, SelectContent, SelectItem, SelectValue } from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import GbDeviceSelect from '@/features/gb/GbDeviceSelect.vue'
import GbChannelSelect from '@/features/gb/GbChannelSelect.vue'
import GbCloudRecordInspector from '@/features/gb/GbCloudRecordInspector.vue'
import { useResource } from '@/shared/composables/useResource'
import { getCloudRecordPageApi } from '@/api/gb28181'
import type { GbRecordFile } from '@/types/api'

/* ---------------- 筛选 ---------------- */
const deviceIdQ = ref<string | undefined>(undefined)
const channelIdQ = ref<string | undefined>(undefined)
const startTimeQ = ref('')
const endTimeQ = ref('')
// __all__ 占位，避免空串在 Select 里的歧义
const statusQ = ref('__all__')

const fmtTime = (v: string) => v.replace('T', ' ')

const hasCondition = computed(
  () =>
    !!deviceIdQ.value ||
    !!channelIdQ.value ||
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
      channelId: channelIdQ.value || undefined,
      start: startTimeQ.value ? fmtTime(startTimeQ.value) : undefined,
      end: endTimeQ.value ? fmtTime(endTimeQ.value) : undefined,
      status: statusQ.value === '__all__' ? undefined : Number(statusQ.value),
    }),
  initial: { page: 1, pageSize: 50 },
})

const onRefresh = () => resource.load(true)

const onClear = () => {
  deviceIdQ.value = undefined
  channelIdQ.value = undefined
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
// 设备切换会经 GbChannelSelect 联动清空通道（额外再触发一次 watch，开销可忽略）
watch([deviceIdQ, channelIdQ], () => {
  cursor.value = 0
  resource.search()
})
watch([startTimeQ, endTimeQ, statusQ], triggerSearch)

/* ---------------- 检视器 ---------------- */
const selected = ref<GbRecordFile | null>(null)

const onRowClick = (f: GbRecordFile, i: number) => {
  cursor.value = i
  selected.value = f
}

const onCloseInspector = () => {
  selected.value = null
}

const onDeleted = (f: GbRecordFile) => {
  resource.removeWhere((item) => item.id === f.id)
  selected.value = null
}

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
  const list = resource.items.value
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
    case 'Enter':
      // 检视器或弹窗打开时不抢占回车
      if (selected.value || startOpen.value || recordingOpen.value) return
      if (list[cursor.value]) onRowClick(list[cursor.value], cursor.value)
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
.inspector-enter-active,
.inspector-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.inspector-enter-from,
.inspector-leave-to {
  opacity: 0;
  transform: translateX(12px);
}

/* GbDeviceSelect 内部 trigger 是定宽按钮，弹窗内拉通 */
.device-select :deep(button) {
  width: 100%;
  height: 2rem;
}

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
