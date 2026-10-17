<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏：处置状态 tabs -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">报警中心</span>
        <div class="ml-3 flex h-full items-center">
          <button
            v-for="tab in statusTabs"
            :key="String(tab.value)"
            class="relative flex h-full items-center gap-1.5 px-2 text-[13px] transition-colors"
            :class="
              handleStatus === tab.value
                ? 'font-medium text-foreground'
                : 'text-muted-foreground hover:text-foreground'
            "
            @click="onStatusTab(tab.value)"
          >
            {{ tab.label }}
            <span
              v-if="tab.value === 0 && unhandledCount"
              class="rounded-full bg-red-500/15 px-1.5 py-px text-[11px] text-red-400"
            >
              {{ unhandledCount }}
            </span>
            <span
              v-if="handleStatus === tab.value"
              class="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-primary"
            />
          </button>
        </div>
        <div class="flex-1" />
        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
      </div>

      <!-- 列表 -->
      <div ref="scrollerRef" class="min-h-0 flex-1 overflow-auto">
        <table class="w-full table-fixed border-collapse text-left text-[13px]">
          <thead class="sticky top-0 z-10">
            <tr class="bg-card text-xs text-muted-foreground">
              <th class="group w-[72px] px-3 py-2 font-normal">
                <Select v-model="priorityFilter">
                  <RawSelectTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    级别
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="priorityFilter !== '__all__' ? 'fill-primary text-primary opacity-100' : ''"
                    />
                  </RawSelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">全部级别</SelectItem>
                    <SelectItem v-for="p in 4" :key="p" :value="String(p)">
                      {{ priorityLabel(p) }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </th>
              <th class="group w-44 px-3 py-2 font-normal">
                <PopoverRoot>
                  <PopoverTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    通道
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="
                        deviceIdQ.trim() || channelIdQ.trim()
                          ? 'fill-primary text-primary opacity-100'
                          : ''
                      "
                    />
                  </PopoverTrigger>
                  <PopoverPortal>
                    <PopoverContent
                      align="start"
                      side="bottom"
                      :side-offset="6"
                      class="z-50 w-60 rounded-md border border-border bg-popover p-3 text-popover-foreground shadow-md outline-none"
                    >
                      <div class="flex flex-col gap-2">
                        <label class="flex flex-col gap-1 text-[11px] text-muted-foreground">
                          设备编码
                          <input
                            v-model="deviceIdQ"
                            class="h-7 rounded-md border border-input bg-transparent px-2 font-mono text-xs outline-none focus:border-ring"
                          />
                        </label>
                        <label class="flex flex-col gap-1 text-[11px] text-muted-foreground">
                          通道编码
                          <input
                            v-model="channelIdQ"
                            class="h-7 rounded-md border border-input bg-transparent px-2 font-mono text-xs outline-none focus:border-ring"
                          />
                        </label>
                      </div>
                      <div class="mt-3 flex justify-end gap-2">
                        <Button
                          v-if="deviceIdQ || channelIdQ"
                          variant="ghost"
                          size="sm"
                          class="h-7 text-xs"
                          @click="deviceIdQ = ''; channelIdQ = ''"
                        >
                          清除
                        </Button>
                        <PopoverClose as-child>
                          <Button size="sm" class="h-7 text-xs">完成</Button>
                        </PopoverClose>
                      </div>
                    </PopoverContent>
                  </PopoverPortal>
                </PopoverRoot>
              </th>
              <th class="w-24 px-3 py-2 font-normal">报警方式</th>
              <th class="w-32 px-3 py-2 font-normal">报警类型</th>
              <th class="w-56 px-3 py-2 font-normal">报警描述</th>
              <th class="group w-44 px-3 py-2 font-normal">
                <PopoverRoot>
                  <PopoverTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    报警时间
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="
                        startTimeQ || endTimeQ ? 'fill-primary text-primary opacity-100' : ''
                      "
                    />
                  </PopoverTrigger>
                  <PopoverPortal>
                    <PopoverContent
                      align="end"
                      side="bottom"
                      :side-offset="6"
                      class="z-50 w-64 rounded-md border border-border bg-popover p-3 text-popover-foreground shadow-md outline-none"
                    >
                      <div class="flex flex-col gap-2">
                        <label class="flex flex-col gap-1 text-[11px] text-muted-foreground">
                          开始
                          <input
                            v-model="startTimeQ"
                            type="datetime-local"
                            class="h-7 rounded-md border border-input bg-transparent px-2 text-xs outline-none focus:border-ring"
                          />
                        </label>
                        <label class="flex flex-col gap-1 text-[11px] text-muted-foreground">
                          结束
                          <input
                            v-model="endTimeQ"
                            type="datetime-local"
                            class="h-7 rounded-md border border-input bg-transparent px-2 text-xs outline-none focus:border-ring"
                          />
                        </label>
                      </div>
                      <div class="mt-3 flex justify-end gap-2">
                        <Button
                          v-if="startTimeQ || endTimeQ"
                          variant="ghost"
                          size="sm"
                          class="h-7 text-xs"
                          @click="startTimeQ = ''; endTimeQ = ''"
                        >
                          清除
                        </Button>
                        <PopoverClose as-child>
                          <Button size="sm" class="h-7 text-xs">完成</Button>
                        </PopoverClose>
                      </div>
                    </PopoverContent>
                  </PopoverPortal>
                </PopoverRoot>
              </th>
              <th class="w-[88px] px-3 py-2 font-normal">状态</th>
              <th class="w-28 px-3 py-2 font-normal">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(a, i) in resource.items.value"
              :key="a.id"
              :data-row="i"
              class="group"
              :class="cursor === i ? 'bg-muted/50' : 'hover:bg-muted/30'"
              @click="cursor = i"
            >
              <td class="px-3 py-2">
                <span class="text-xs" :class="priorityTextClass(a.alarmPriority)">
                  {{ priorityLabel(a.alarmPriority) }}
                </span>
              </td>
              <td class="px-3 py-2">
                <div class="truncate" :title="a.channelName">{{ a.channelName || '—' }}</div>
                <div class="truncate font-mono text-[11px] text-muted-foreground">
                  {{ a.channelId || a.deviceId }}
                </div>
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ a.alarmMethodDesc || a.alarmMethod || '—' }}
              </td>
              <td class="px-3 py-2 text-xs">
                {{ alarmTypeText(a.alarmType) }}
              </td>
              <td class="truncate px-3 py-2 text-muted-foreground" :title="a.alarmDescription">
                {{ a.alarmDescription || '—' }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ a.alarmTime || '—' }}
              </td>
              <td class="px-3 py-2">
                <span class="text-xs" :class="handleStatusClass(a.handleStatus)">
                  {{ handleStatusLabel(a.handleStatus) }}
                </span>
              </td>
              <td class="px-3 py-2">
                <div class="flex items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                  <button
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
                    title="详情"
                    @click.stop="openDetail(a)"
                  >
                    <Eye class="size-3.5" />
                  </button>
                  <button
                    v-access="PERM.GB_ALARM_HANDLE"
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-emerald-400"
                    title="处置"
                    @click.stop="openHandle(a)"
                  >
                    <CheckCheck class="size-3.5" />
                  </button>
                  <button
                    v-access="PERM.GB_ALARM_DELETE"
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-red-400"
                    title="删除"
                    @click.stop="onDelete(a)"
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
          v-else-if="!resource.loading.value && resource.items.value.length === 0"
          class="flex flex-col items-center gap-2 py-16 text-muted-foreground"
        >
          <BellOff class="size-8" />
          <div class="text-[13px]">暂无报警记录</div>
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
        <span>共 {{ resource.total.value }} 条报警</span>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <Dialog :open="detailOpen" @update:open="(v) => (detailOpen = v)">
      <DialogContent class="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>报警详情</DialogTitle>
          <DialogDescription />
        </DialogHeader>
        <dl v-if="detail" class="grid grid-cols-[96px_1fr] gap-x-4 gap-y-3 text-[13px]">
          <dt class="text-muted-foreground">设备编码</dt>
          <dd class="font-mono">{{ detail.deviceId || '—' }}</dd>
          <dt class="text-muted-foreground">通道名称</dt>
          <dd>{{ detail.channelName || '—' }}</dd>
          <dt class="text-muted-foreground">通道编码</dt>
          <dd class="font-mono">{{ detail.channelId || '—' }}</dd>
          <dt class="text-muted-foreground">报警级别</dt>
          <dd>{{ priorityLabel(detail.alarmPriority) }}</dd>
          <dt class="text-muted-foreground">报警方式</dt>
          <dd>{{ detail.alarmMethodDesc || detail.alarmMethod || '—' }}</dd>
          <dt class="text-muted-foreground">报警类型</dt>
          <dd>{{ alarmTypeText(detail.alarmType) }}</dd>
          <dt class="text-muted-foreground">报警时间</dt>
          <dd class="font-mono">{{ detail.alarmTime || '—' }}</dd>
          <dt class="text-muted-foreground">接收时间</dt>
          <dd class="font-mono">{{ detail.receiveTime || '—' }}</dd>
          <dt class="text-muted-foreground">经纬度</dt>
          <dd class="font-mono">
            <template v-if="detail.longitude != null && detail.latitude != null">
              {{ detail.longitude }}, {{ detail.latitude }}
            </template>
            <template v-else>—</template>
          </dd>
          <dt class="text-muted-foreground">报警描述</dt>
          <dd>{{ detail.alarmDescription || '—' }}</dd>
          <dt class="text-muted-foreground">处置状态</dt>
          <dd :class="handleStatusClass(detail.handleStatus)">
            {{ handleStatusLabel(detail.handleStatus) }}
          </dd>
          <template v-if="detail.handleStatus !== 0">
            <dt class="text-muted-foreground">处置人</dt>
            <dd>{{ detail.handleUser || '—' }}</dd>
            <dt class="text-muted-foreground">处置时间</dt>
            <dd class="font-mono">{{ detail.handleTime || '—' }}</dd>
            <dt class="text-muted-foreground">处置备注</dt>
            <dd>{{ detail.handleRemark || '—' }}</dd>
          </template>
        </dl>
      </DialogContent>
    </Dialog>

    <!-- 处置弹窗 -->
    <Dialog :open="handleOpen" @update:open="(v) => (handleOpen = v)">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>报警处置</DialogTitle>
          <DialogDescription />
        </DialogHeader>
        <div v-if="handleTarget" class="space-y-4">
          <div class="flex items-center gap-2 text-[13px]">
            <span :class="priorityTextClass(handleTarget.alarmPriority)">
              {{ priorityLabel(handleTarget.alarmPriority) }}
            </span>
            <span class="truncate text-muted-foreground">
              {{ handleTarget.channelName || handleTarget.channelId || handleTarget.deviceId }}
            </span>
          </div>

          <div class="flex flex-col gap-2">
            <button
              v-for="c in handleChoices"
              :key="c.value"
              class="flex items-start gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors"
              :class="
                handleForm.status === c.value
                  ? 'border-primary/60 bg-primary/[0.07]'
                  : 'border-border hover:bg-muted/50'
              "
              @click="handleForm.status = c.value"
            >
              <span
                class="mt-0.5 size-3.5 shrink-0 rounded-full border"
                :class="handleForm.status === c.value ? 'border-[5px] border-primary' : 'border-muted-foreground'"
              />
              <span>
                <span class="block text-[13px] font-medium">{{ c.label }}</span>
                <span class="block text-xs text-muted-foreground">{{ c.desc }}</span>
              </span>
            </button>
          </div>

          <textarea
            v-model="handleForm.remark"
            rows="3"
            maxlength="500"
            placeholder="处置备注（选填）"
            class="w-full resize-none rounded-lg border border-border bg-transparent px-3 py-2 text-[13px] outline-none placeholder:text-muted-foreground focus:border-primary/60"
          />
        </div>
        <DialogFooter>
          <Button variant="outline" size="sm" @click="handleOpen = false">取消</Button>
          <Button size="sm" :disabled="handling" @click="confirmHandle">确定</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { IconFilter, IconLoader2 } from '@tabler/icons-vue'
import { BellOff, CheckCheck, Eye, Trash2 } from 'lucide-vue-next'
import RefreshButton from '@/components/RefreshButton.vue'
import Button from '@/components/ui/button/Button.vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem } from '@/components/ui/select'
import {
  SelectTrigger as RawSelectTrigger,
  PopoverRoot,
  PopoverTrigger,
  PopoverPortal,
  PopoverContent,
  PopoverClose,
} from 'reka-ui'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import {
  deleteGbAlarmApi,
  getGbAlarmDetailApi,
  getGbAlarmPageApi,
  handleGbAlarmApi,
} from '@/api/gb28181'
import type {
  GbDeviceAlarm,
  GbDeviceAlarmPageRequest,
} from '@/types/api'

/* ---------------- 筛选条件 ---------------- */
const deviceIdQ = ref('')
const channelIdQ = ref('')
const alarmPriorityQ = ref<number | undefined>(undefined)
const handleStatus = ref<number | null>(null)
const startTimeQ = ref('')
const endTimeQ = ref('')

const priorityFilter = ref('__all__')

const statusTabs = computed(() => [
  { label: '全部', value: null },
  { label: '未处理', value: 0 },
  { label: '已确认', value: 1 },
  { label: '已处置', value: 2 },
  { label: '误报', value: 3 },
])

const unhandledCount = ref(0)

const fmtTime = (v: string) => v.replace('T', ' ')

const resource = useResource<GbDeviceAlarmPageRequest, GbDeviceAlarm>({
  fetcher: (q) =>
    getGbAlarmPageApi({
      ...q,
      deviceId: deviceIdQ.value.trim() || undefined,
      channelId: channelIdQ.value.trim() || undefined,
      alarmPriority: alarmPriorityQ.value,
      handleStatus: handleStatus.value ?? undefined,
      startTime: startTimeQ.value ? fmtTime(startTimeQ.value) : undefined,
      endTime: endTimeQ.value ? fmtTime(endTimeQ.value) : undefined,
    }),
  initial: { page: 1, pageSize: 50 },
})

const onRefresh = () => Promise.all([resource.load(true), fetchUnhandled()])

const fetchUnhandled = async () => {
  const res = await getGbAlarmPageApi({ page: 1, pageSize: 1, handleStatus: 0 })
  unhandledCount.value = res.data.total
}

const onStatusTab = (v: number | null) => {
  if (handleStatus.value === v) return
  handleStatus.value = v
  cursor.value = 0
  resource.search()
}

watch(priorityFilter, (v) => {
  alarmPriorityQ.value = v === '__all__' ? undefined : Number(v)
  cursor.value = 0
  resource.search()
})

let debounceTimer: ReturnType<typeof setTimeout> | null = null
const triggerSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cursor.value = 0
    resource.search()
  }, 300)
}
watch([deviceIdQ, channelIdQ, startTimeQ, endTimeQ], triggerSearch)

/* ---------------- 展示映射 ---------------- */
const priorityLabel = (p?: number | null) => {
  if (p == null) return '—'
  return ['一级', '二级', '三级', '四级'][p - 1] ?? String(p)
}

const priorityTextClass = (p?: number | null) =>
  ({
    1: 'text-red-400',
    2: 'text-amber-400',
    3: 'text-sky-400',
    4: 'text-muted-foreground',
  })[p ?? 0] ?? 'text-muted-foreground'

/** GB28181 设备报警子类型（AlarmType，method=2/5/6 时有意义） */
const ALARM_TYPE_TEXT: Record<string, string> = {
  '1': '视频丢失',
  '2': '视频遮挡',
  '3': '移动侦测',
  '4': '硬盘满',
  '5': '硬盘故障',
  '6': '网线断开',
  '7': 'IP冲突',
  '8': '非法访问',
  '9': '视频信号异常',
  '10': '录像异常',
  '11': '智能分析',
}
const alarmTypeText = (t?: string | null) =>
  t == null || t === '' ? '—' : ALARM_TYPE_TEXT[t] ?? `类型${t}`

const handleStatusLabel = (s?: number | null) =>
  (['未处理', '已确认', '已处置', '误报'] as const)[s ?? 0] ?? '—'

const handleStatusClass = (s?: number | null) =>
  ({
    0: 'text-red-400',
    1: 'text-amber-400',
    2: 'text-emerald-400',
    3: 'text-muted-foreground',
  })[s ?? 0] ?? 'text-muted-foreground'

/* ---------------- 详情 ---------------- */
const detailOpen = ref(false)
const detail = ref<GbDeviceAlarm | null>(null)

const openDetail = async (a: GbDeviceAlarm) => {
  detailOpen.value = true
  detail.value = a
  const res = await getGbAlarmDetailApi(a.id)
  detail.value = res.data
}

/* ---------------- 处置 ---------------- */
const handleOpen = ref(false)
const handleTarget = ref<GbDeviceAlarm | null>(null)
const handleForm = ref<{ status: number; remark: string }>({ status: 1, remark: '' })
const handling = ref(false)

const handleChoices = [
  { value: 1, label: '已确认', desc: '已知悉该报警，正在跟进' },
  { value: 2, label: '已处置', desc: '报警已处理完毕' },
  { value: 3, label: '误报', desc: '经核实为误报，无需处理' },
]

const openHandle = (a: GbDeviceAlarm) => {
  handleTarget.value = a
  handleForm.value = {
    status: a.handleStatus === 1 ? 2 : 1,
    remark: a.handleRemark || '',
  }
  handleOpen.value = true
}

const confirmHandle = async () => {
  if (!handleTarget.value || handling.value) return
  handling.value = true
  try {
    await handleGbAlarmApi({
      id: handleTarget.value.id,
      handleStatus: handleForm.value.status,
      handleRemark: handleForm.value.remark || undefined,
    })
    toast.success('处置成功')
    handleOpen.value = false
    await Promise.all([resource.search(), fetchUnhandled()])
  } finally {
    handling.value = false
  }
}

/* ---------------- 删除 ---------------- */
const onDelete = async (a: GbDeviceAlarm) => {
  const ok = await confirm({
    title: '确定删除该报警记录吗？',
    description: '删除后不可恢复。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteGbAlarmApi(a.id)
  toast.success('删除成功')
  resource.removeWhere((item) => item.id === a.id)
  if (a.handleStatus === 0) fetchUnhandled()
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
  }
}

/* ---------------- 生命周期 ---------------- */
let observer: IntersectionObserver | null = null

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown)
  await Promise.all([resource.load(true), fetchUnhandled()])
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
