<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧：列表卡片 -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">国标设备</span>
        <div class="ml-3 flex h-full items-center">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="relative flex h-full items-center gap-1.5 px-2 text-[13px] transition-colors"
            :class="
              status === tab.status
                ? 'font-medium text-foreground'
                : 'text-muted-foreground hover:text-foreground'
            "
            @click="onTab(tab.status)"
          >
            <span v-if="tab.dot" class="h-1.5 w-1.5 rounded-full" :class="tab.dotClass" />
            {{ tab.label }}
            <span class="rounded-full bg-muted px-1.5 py-px text-[11px] text-muted-foreground">{{
              counts[tab.key]
            }}</span>
            <span
              v-if="status === tab.status"
              class="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-primary"
            />
          </button>
        </div>
        <span v-if="checkedIds.size" class="ml-2 text-xs text-muted-foreground">
          已选 {{ checkedIds.size }} 项
        </span>
        <div class="flex-1" />
        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
        <Button
          v-access="PERM.GB_DEVICE_SYNC"
          variant="ghost"
          size="icon"
          class="size-8"
          title="检测设备状态"
          :disabled="!checkedIds.size || detecting"
          @click="onDetect"
        >
          <Loader2 v-if="detecting" class="size-4 animate-spin" />
          <Radar v-else class="size-4" />
        </Button>
        <Button
          v-access="PERM.GB_DEVICE_DELETE"
          variant="ghost"
          size="icon"
          class="size-8"
          title="批量删除"
          :disabled="!checkedIds.size || batchDeleting"
          @click="onBatchDelete"
        >
          <Loader2 v-if="batchDeleting" class="size-4 animate-spin" />
          <Trash2 v-else class="size-4" />
        </Button>

        <Button
          v-access="PERM.GB_DEVICE_ADD"
          variant="ghost"
          size="sm"
          class="ml-1 gap-1.5"
          @click="onCreate"
        >
          <Plus class="size-3.5" />
          新建
          <kbd class="rounded border border-border bg-muted px-1 text-[10px] text-muted-foreground"
            >N</kbd
          >
        </Button>
      </div>

      <!-- 搜索行 -->
      <div class="flex h-12 shrink-0 items-center gap-2 px-3">
        <IconSearch class="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        <input
          ref="deviceIdRef"
          v-model="deviceIdQ"
          class="h-7 w-44 rounded-md bg-transparent px-2 font-mono text-[13px] outline-none placeholder:font-sans placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入设备编码"
        />
        <input
          v-model="nameQ"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入设备名称"
        />

        <span v-if="hasCondition" class="text-xs text-muted-foreground">
          匹配 {{ resource.total.value }} 台
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
        <table class="w-full border-collapse text-left text-[13px]">
          <thead class="sticky top-0 z-10">
            <tr class="bg-card text-xs text-muted-foreground">
              <th class="w-9 px-3 py-2">
                <Checkbox
                  :model-value="allChecked ? true : indeterminate ? 'indeterminate' : false"
                  @update:model-value="onToggleAll"
                />
              </th>
              <th class="w-52 px-3 py-2 font-normal">设备编码</th>
              <th class="w-48 px-3 py-2 font-normal">设备名称</th>
              <th class="w-[72px] px-3 py-2">
                <span class="inline-flex items-center gap-1 font-normal">
                  状态
                  <Select v-model="statusFilter">
                    <RawSelectTrigger
                      title="按状态筛选"
                      class="inline-flex size-4 items-center justify-center rounded outline-none hover:text-foreground"
                    >
                      <IconFilter
                        class="size-3.5"
                        :class="
                          statusFilter !== '__all__'
                            ? 'text-primary'
                            : 'text-muted-foreground/60'
                        "
                      />
                    </RawSelectTrigger>
                    <SelectContent>
                      <SelectItem value="__all__">全部</SelectItem>
                      <SelectItem value="1">在线</SelectItem>
                      <SelectItem value="0">离线</SelectItem>
                      <SelectItem value="2">未注册</SelectItem>
                    </SelectContent>
                  </Select>
                </span>
              </th>
              <th class="w-24 px-3 py-2">
                <span class="inline-flex items-center gap-1 font-normal">
                  数据来源
                  <Select v-model="sourceFilter">
                    <RawSelectTrigger
                      title="按数据来源筛选"
                      class="inline-flex size-4 items-center justify-center rounded outline-none hover:text-foreground"
                    >
                      <IconFilter
                        class="size-3.5"
                        :class="
                          sourceFilter !== '__all__'
                            ? 'text-primary'
                            : 'text-muted-foreground/60'
                        "
                      />
                    </RawSelectTrigger>
                    <SelectContent>
                      <SelectItem value="__all__">全部</SelectItem>
                      <SelectItem value="1">系统新增</SelectItem>
                      <SelectItem value="2">表格导入</SelectItem>
                      <SelectItem value="3">设备注册</SelectItem>
                    </SelectContent>
                  </Select>
                </span>
              </th>
              <th class="w-40 px-3 py-2 font-normal">网络地址</th>
              <th class="w-40 px-3 py-2 font-normal">最近心跳</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(d, i) in resource.items.value"
              :key="d.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                !creating && selected?.id === d.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(d, i)"
            >
              <td class="px-3 py-2" @click.stop>
                <Checkbox
                  :model-value="checkedIds.has(d.id)"
                  @update:model-value="(v) => onToggleOne(d, v === true)"
                />
              </td>
              <td class="relative px-3 py-2">
                <span
                  v-if="!creating && selected?.id === d.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <span class="flex min-w-0 items-center gap-1">
                  <span class="min-w-0 truncate font-mono" :title="d.deviceId">{{ d.deviceId }}</span>
                  <button
                    type="button"
                    class="hidden size-5 shrink-0 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground group-hover:flex disabled:hidden"
                    :disabled="d.status !== 1"
                    :title="d.status === 1 ? '同步通道目录' : '设备离线，无法同步'"
                    @click.stop="onSync(d)"
                  >
                    <IconRefresh class="size-3.5" />
                  </button>
                </span>
              </td>
              <td class="max-w-0 truncate px-3 py-2 text-muted-foreground" :title="displayName(d)">
                {{ displayName(d) }}
              </td>
              <td class="px-3 py-2">
                <span class="flex items-center gap-1.5 text-xs">
                  <span
                    v-if="d.status !== 2"
                    class="h-1.5 w-1.5 rounded-full"
                    :class="d.status === 1 ? 'bg-emerald-500' : 'bg-red-500'"
                  />
                  <span :class="d.status === 1 ? 'text-foreground' : 'text-muted-foreground'">
                    {{ statusText(d.status) }}
                  </span>
                </span>
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">{{ sourceText(d.source) }}</td>
              <td class="max-w-0 truncate px-3 py-2 font-mono text-xs" :title="d.hostAddress || ''">
                {{ d.hostAddress || '—' }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ d.keepaliveTime || '—' }}
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
          <IconDeviceCctvOff class="size-8" />
          <div class="text-[13px]">{{ hasCondition ? '没有匹配的设备' : '还没有国标设备' }}</div>
          <Button v-if="hasCondition" variant="ghost" size="sm" @click="onClear">
            清除搜索条件
          </Button>
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

      <!-- 快捷键条 -->
      <div
        class="flex h-8 shrink-0 items-center gap-3 border-t border-border px-3 text-[11px] text-muted-foreground/70"
      >
        <span><kbd>/</kbd> 搜索</span>
        <span><kbd>J</kbd><kbd>K</kbd> 移动</span>
        <span><kbd>Enter</kbd> 查看</span>
        <span><kbd>N</kbd> 新建</span>
        <span><kbd>R</kbd> 刷新</span>
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 台设备</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="creating || selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <GbDeviceInspector
            :device="selected ?? undefined"
            :creating="creating"
            @close="onCloseInspector"
            @saved="onSaved"
            @created="onCreated"
            @deleted="onDeleted"
          />
        </div>
      </aside>
    </Transition>

    <!-- 同步进度弹窗 -->
    <GbSyncProgressDialog ref="syncDialogRef" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconSearch,
  IconX,
  IconFilter,
  IconRefresh,
  IconLoader2,
  IconDeviceCctvOff,
} from '@tabler/icons-vue'
import { Plus, Trash2, Loader2, Radar } from 'lucide-vue-next'
import GbDeviceInspector from '@/features/gb/GbDeviceInspector.vue'
import GbSyncProgressDialog from '@/features/gb/GbSyncProgressDialog.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import {
  getGbDevicePageApi,
  getGbDeviceStatusCountApi,
  syncGbDeviceApi,
  batchDeleteGbDeviceApi,
  detectGbDeviceApi,
} from '@/api/gb28181'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import { Select, SelectContent, SelectItem } from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import type { GbDevice, GbDevicePageRequest } from '@/types/api'

/* ---------------- 搜索 / 状态统计 ---------------- */
const deviceIdQ = ref('')
const nameQ = ref('')
const status = ref<number | null>(null)
const sourceFilter = ref<'__all__' | '1' | '2' | '3'>('__all__')
const statusFilter = ref<'__all__' | '1' | '0' | '2'>('__all__')

// 表头状态漏斗 ↔ 顶部状态 tab：共用 status，双向同步
watch(statusFilter, (v) => {
  const next = v === '__all__' ? null : Number(v)
  if (status.value === next) return
  status.value = next
  cursor.value = 0
  clearChecked()
  resource.search()
})
watch(status, (v) => {
  const next = v === null ? '__all__' : (String(v) as '1' | '0' | '2')
  if (statusFilter.value !== next) statusFilter.value = next
})

const counts = ref({ all: 0, online: 0, offline: 0, unregistered: 0 })
const tabs = computed(() => [
  { key: 'all' as const, label: '全部', status: null, dot: false, dotClass: '' },
  { key: 'online' as const, label: '在线', status: 1, dot: true, dotClass: 'bg-emerald-500' },
  { key: 'offline' as const, label: '离线', status: 0, dot: true, dotClass: 'bg-red-500' },
  {
    key: 'unregistered' as const,
    label: '未注册',
    status: 2,
    dot: true,
    dotClass: 'bg-muted-foreground/50',
  },
])

const fetchStatusCount = async () => {
  const rows = (await getGbDeviceStatusCountApi()).data
  const find = (s: number) => rows.find((r) => r.status === s)?.count ?? 0
  const online = find(1)
  const offline = find(0)
  const unregistered = find(2)
  counts.value = { all: online + offline + unregistered, online, offline, unregistered }
}

/** 手动刷新：重拉列表（保留当前筛选）+ 状态统计 */
const onRefresh = () =>
  Promise.all([resource.load(true), fetchStatusCount()])

const onTab = (s: number | null) => {
  if (status.value === s) return
  status.value = s
  cursor.value = 0
  clearChecked()
  resource.search()
}

const hasCondition = computed(
  () =>
    !!deviceIdQ.value.trim() ||
    !!nameQ.value.trim() ||
    status.value !== null ||
    sourceFilter.value !== '__all__',
)

const resource = useResource<GbDevicePageRequest, GbDevice>({
  fetcher: (q) =>
    getGbDevicePageApi({
      ...q,
      deviceId: deviceIdQ.value.trim() || undefined,
      name: nameQ.value.trim() || undefined,
      status: status.value ?? undefined,
      source: sourceFilter.value === '__all__' ? undefined : Number(sourceFilter.value),
    }),
  initial: { page: 1, pageSize: 50 },
})

let debounceTimer: ReturnType<typeof setTimeout> | null = null
const triggerSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cursor.value = 0
    clearChecked()
    resource.search()
  }, 300)
}
watch([deviceIdQ, nameQ], triggerSearch)
watch(sourceFilter, () => {
  cursor.value = 0
  clearChecked()
  resource.search()
})

const onClear = () => {
  deviceIdQ.value = ''
  nameQ.value = ''
  status.value = null
  sourceFilter.value = '__all__'
  cursor.value = 0
  clearChecked()
  resource.search()
}

/* ---------------- 选择（勾选） ---------------- */
const checkedIds = ref<Set<string>>(new Set())

const pageIds = computed(() => resource.items.value.map((d) => d.id))
const allChecked = computed(
  () => pageIds.value.length > 0 && pageIds.value.every((id) => checkedIds.value.has(id)),
)
const indeterminate = computed(
  () => !allChecked.value && pageIds.value.some((id) => checkedIds.value.has(id)),
)

const onToggleAll = (v: boolean | 'indeterminate') => {
  const next = new Set(checkedIds.value)
  if (v === true) {
    pageIds.value.forEach((id) => next.add(id))
  } else {
    pageIds.value.forEach((id) => next.delete(id))
  }
  checkedIds.value = next
}

const onToggleOne = (d: GbDevice, checked: boolean) => {
  const next = new Set(checkedIds.value)
  if (checked) next.add(d.id)
  else next.delete(d.id)
  checkedIds.value = next
}

const clearChecked = () => {
  checkedIds.value = new Set()
}

/* ---------------- 检视器 ---------------- */
const cursor = ref(0)
const selected = ref<GbDevice | null>(null)
const creating = ref(false)

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const deviceIdRef = ref<HTMLInputElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  scrollerRef.value
    ?.querySelector(`[data-row="${cursor.value}"]`)
    ?.scrollIntoView({ block: 'nearest' })
}

const onRowClick = (d: GbDevice, i: number) => {
  cursor.value = i
  selected.value = d
}

const onCloseInspector = () => {
  creating.value = false
  selected.value = null
}

const onCreate = () => {
  selected.value = null
  creating.value = true
}

const onSaved = () => {
  resource.search()
  fetchStatusCount()
}
const onCreated = () => {
  creating.value = false
  resource.search()
  fetchStatusCount()
}
const onDeleted = (d: GbDevice) => {
  resource.removeWhere((item) => item.id === d.id)
  const next = new Set(checkedIds.value)
  next.delete(d.id)
  checkedIds.value = next
  selected.value = null
  creating.value = false
  fetchStatusCount()
}

/* ---------------- 同步 ---------------- */
const syncDialogRef = ref<InstanceType<typeof GbSyncProgressDialog>>()

const onSync = async (d: GbDevice) => {
  if (d.status !== 1) return
  const res = await syncGbDeviceApi(d.deviceId)
  syncDialogRef.value?.open(d.deviceId, d.name || d.deviceId)
  if (res.data?.result === 'ALREADY_RUNNING') {
    toast.info('设备已有同步在进行中，已为你打开进度窗口')
  }
}

/* ---------------- 批删 / 检测 ---------------- */
const batchDeleting = ref(false)
const onBatchDelete = async () => {
  if (!checkedIds.value.size || batchDeleting.value) return
  const ok = await confirm({
    title: `确定要删除选中的 ${checkedIds.value.size} 台设备吗？`,
    description: '此操作不可恢复。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  batchDeleting.value = true
  try {
    await batchDeleteGbDeviceApi([...checkedIds.value])
    toast.success('删除成功')
    clearChecked()
    resource.search()
    fetchStatusCount()
    if (selected.value) selected.value = null
  } finally {
    batchDeleting.value = false
  }
}

const detecting = ref(false)
const onDetect = async () => {
  if (!checkedIds.value.size || detecting.value) return
  detecting.value = true
  try {
    // 后端异步探测，发完即返回
    await detectGbDeviceApi([...checkedIds.value])
    toast.success('检测已发起，请稍后刷新查看设备状态')
  } finally {
    detecting.value = false
  }
}

/* ---------------- 展示辅助 ---------------- */
const displayName = (d: GbDevice) => d.name || d.reportName || d.hostAddress || '—'

const statusText = (status: number) => {
  if (status === 1) return '在线'
  if (status === 2) return '未注册'
  return '离线'
}

const SOURCE_TEXT: Record<number, string> = {
  1: '系统新增',
  2: '表格导入',
  3: '设备注册',
  4: '级联平台(历史)',
}
const sourceText = (source?: number) =>
  source == null ? '—' : SOURCE_TEXT[source] ?? '—'

/* ---------------- 键盘流 ---------------- */
const isTypingTarget = (el: EventTarget | null) =>
  el instanceof HTMLElement &&
  (el.tagName === 'INPUT' ||
    el.tagName === 'TEXTAREA' ||
    el.tagName === 'SELECT' ||
    el.isContentEditable)

const onGlobalKeydown = (e: KeyboardEvent) => {
  if (isTypingTarget(e.target)) {
    if (e.key === 'Escape') (e.target as HTMLElement).blur()
    return
  }
  const list = resource.items.value
  switch (e.key) {
    case '/':
      e.preventDefault()
      deviceIdRef.value?.focus()
      break
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
      e.preventDefault()
      if (list[cursor.value]) selected.value = list[cursor.value]
      break
    case 'n':
    case 'N':
      e.preventDefault()
      onCreate()
      break
    case 'r':
    case 'R':
      e.preventDefault()
      onRefresh()
      break
    case 'Escape':
      onCloseInspector()
      break
  }
}

/* ---------------- 生命周期 / observer ---------------- */
let observer: IntersectionObserver | null = null

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown)
  await Promise.all([resource.load(true), fetchStatusCount()])
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

watch(
  () => resource.items.value,
  (list) => {
    if (selected.value) {
      const fresh = list.find((d) => d.id === selected.value!.id)
      if (fresh) selected.value = fresh
    }
    if (cursor.value > list.length - 1) cursor.value = Math.max(0, list.length - 1)
  },
)
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
