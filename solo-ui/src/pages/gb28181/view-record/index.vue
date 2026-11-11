<template>
  <div class="flex h-full overflow-hidden p-3">
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">观看记录</span>
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
          title="观看开始时间起"
        />
        <span class="text-xs text-muted-foreground">至</span>
        <input
          v-model="endTimeQ"
          type="datetime-local"
          class="h-7 w-[180px] rounded-md bg-transparent px-2 text-[13px] outline-none focus:bg-muted/70"
          title="观看开始时间止"
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
              <th class="group w-44 px-3 py-2 font-normal">
                <Select v-model="actorTypeQ">
                  <RawSelectTrigger
                    title="按观看者类型筛选"
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    观看者
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="actorTypeQ !== '__all__' ? 'fill-primary text-primary opacity-100' : ''"
                    />
                  </RawSelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">全部类型</SelectItem>
                    <SelectItem value="1">平台用户</SelectItem>
                    <SelectItem value="2">开放API应用</SelectItem>
                  </SelectContent>
                </Select>
              </th>
              <th class="px-3 py-2 font-normal">设备</th>
              <th class="w-44 px-3 py-2 font-normal">通道</th>
              <th class="w-32 px-3 py-2 font-normal">客户端 IP</th>
              <th class="w-44 px-3 py-2 font-normal">开始时间</th>
              <th class="w-24 px-3 py-2 font-normal">时长</th>
              <th class="w-24 px-3 py-2 font-normal">流量</th>
              <th class="group w-28 px-3 py-2 font-normal">
                <Select v-model="statusQ">
                  <RawSelectTrigger
                    title="按状态筛选"
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    状态
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="statusQ !== '__all__' ? 'fill-primary text-primary opacity-100' : ''"
                    />
                  </RawSelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">全部状态</SelectItem>
                    <SelectItem value="0">观看中</SelectItem>
                    <SelectItem value="1">正常结束</SelectItem>
                    <SelectItem value="2">兜底关闭</SelectItem>
                  </SelectContent>
                </Select>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="r in resource.items.value"
              :key="r.id"
              class="hover:bg-muted/30"
            >
              <td class="px-3 py-2 text-xs">
                <div class="flex items-center gap-1.5">
                  <span class="truncate">{{ r.actorName || r.actorId || '—' }}</span>
                  <span
                    class="shrink-0 rounded-full border px-1.5 py-0.5 text-[10px] leading-none"
                    :class="actorView(r.actorType).cls"
                  >
                    {{ actorView(r.actorType).text }}
                  </span>
                </div>
              </td>
              <td class="truncate px-3 py-2 text-xs" :title="r.deviceId">
                {{ r.deviceName || r.deviceId || '—' }}
              </td>
              <td class="truncate px-3 py-2 text-xs text-muted-foreground" :title="r.channelId">
                {{ r.channelName || r.channelId || '—' }}
              </td>
              <td class="truncate px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ r.clientIp || '—' }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ r.startTime || '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ r.durationSec != null ? fmtDuration(r.durationSec) : '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ r.totalBytes != null ? fmtSize(r.totalBytes) : '—' }}
              </td>
              <td class="px-3 py-2 text-xs" :title="r.closeReason ? `关闭原因：${r.closeReason}` : ''">
                <span :class="statusView(r.status).cls">{{ statusView(r.status).text }}</span>
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
          <IconEye class="size-8" />
          <div class="text-[13px]">暂无观看记录</div>
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
        <span>同一路流多人观看各记一条，按播放会话区分</span>
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 条记录</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 观看记录：谁（用户/开放应用）看了哪路设备通道、看了多久、耗了多少流量。
 * 只读审计列表，无限滚动。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { IconLoader2, IconEye, IconX, IconFilter } from '@tabler/icons-vue'
import RefreshButton from '@/components/RefreshButton.vue'
import Button from '@/components/ui/button/Button.vue'
import { Select, SelectContent, SelectItem } from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import GbDeviceSelect from '@/features/gb/GbDeviceSelect.vue'
import GbChannelSelect from '@/features/gb/GbChannelSelect.vue'
import { useResource } from '@/shared/composables/useResource'
import { getViewRecordPageApi } from '@/api/gb28181'
import type { GbViewRecord } from '@/types/api'

/* ---------------- 筛选 ---------------- */
const deviceIdQ = ref<string | undefined>(undefined)
const channelIdQ = ref<string | undefined>(undefined)
const startTimeQ = ref('')
const endTimeQ = ref('')
// __all__ 占位，避免空串在 Select 里的歧义
const actorTypeQ = ref('__all__')
const statusQ = ref('__all__')

// datetime-local 是 "yyyy-MM-ddTHH:mm"，后端要 "yyyy-MM-dd HH:mm:ss"
const fmtTime = (v: string) => `${v.replace('T', ' ')}:00`

const hasCondition = computed(
  () =>
    !!deviceIdQ.value ||
    !!channelIdQ.value ||
    !!startTimeQ.value ||
    !!endTimeQ.value ||
    actorTypeQ.value !== '__all__' ||
    statusQ.value !== '__all__',
)

const resource = useResource<{ page?: number; pageSize?: number }, GbViewRecord>({
  fetcher: (q) =>
    getViewRecordPageApi({
      page: q.page,
      // 后端分页字段是 size
      size: q.pageSize,
      deviceId: deviceIdQ.value || undefined,
      channelId: channelIdQ.value || undefined,
      actorType: actorTypeQ.value === '__all__' ? undefined : Number(actorTypeQ.value),
      status: statusQ.value === '__all__' ? undefined : Number(statusQ.value),
      start: startTimeQ.value ? fmtTime(startTimeQ.value) : undefined,
      end: endTimeQ.value ? fmtTime(endTimeQ.value) : undefined,
    }),
  initial: { page: 1, pageSize: 50 },
})

const onRefresh = () => resource.load(true)

const onClear = () => {
  deviceIdQ.value = undefined
  channelIdQ.value = undefined
  startTimeQ.value = ''
  endTimeQ.value = ''
  actorTypeQ.value = '__all__'
  statusQ.value = '__all__'
  resource.search()
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null
const triggerSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => resource.search(), 300)
}
// 设备切换会经 GbChannelSelect 联动清空通道（额外再触发一次 watch，开销可忽略）
watch([deviceIdQ, channelIdQ], () => resource.search())
watch([startTimeQ, endTimeQ, statusQ, actorTypeQ], triggerSearch)

/* ---------------- 展示辅助 ---------------- */
const actorView = (t: number) => {
  if (t === 2) return { text: '应用', cls: 'border-purple-200 bg-purple-50 text-purple-600' }
  return { text: '用户', cls: 'border-blue-200 bg-blue-50 text-blue-600' }
}

const statusView = (status: number) => {
  if (status === 0) return { text: '观看中', cls: 'text-blue-600' }
  if (status === 2) return { text: '兜底关闭', cls: 'text-amber-600' }
  return { text: '正常结束', cls: 'text-green-600' }
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

/* ---------------- 生命周期 ---------------- */
const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

let observer: IntersectionObserver | null = null

onMounted(async () => {
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
  observer?.disconnect()
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>
