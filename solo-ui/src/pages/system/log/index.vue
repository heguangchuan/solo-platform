<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  IconSearch,
  IconTrash,
  IconDownload,
  IconLoader2,
  IconFileOff,
  IconX,
  IconFilter,
} from '@tabler/icons-vue'
import {
  getLogPageApi,
  exportLogApi,
  deleteLogApi,
  deleteLogBatchApi,
} from '@/api/log'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import {
  Select,
  SelectContent,
  SelectItem,
} from '@/components/ui/select'
import {
  SelectTrigger as RawSelectTrigger,
  PopoverRoot,
  PopoverTrigger,
  PopoverPortal,
  PopoverContent,
  PopoverClose,
} from 'reka-ui'
import LogInspector from '@/features/log/LogInspector.vue'
import { BUSINESS_TYPE_OPTIONS, businessMetaOf } from '@/features/log/logMeta'
import type { SysLog, LogPageRequest } from '@/types/api'

/* ---------- 查询条件 ---------- */
const operatorName = ref('')
const moduleName = ref('')
const businessType = ref('__all__')
const responseStatus = ref('__all__')
const clientSource = ref('__all__')
const startTime = ref('')
const endTime = ref('')

const hasCondition = computed(
  () =>
    !!operatorName.value.trim() ||
    !!moduleName.value.trim() ||
    businessType.value !== '__all__' ||
    responseStatus.value !== '__all__' ||
    clientSource.value !== '__all__' ||
    !!startTime.value ||
    !!endTime.value,
)

const resource = useResource<LogPageRequest, SysLog>({
  fetcher: (q) =>
    getLogPageApi({
      ...q,
      operatorName: operatorName.value.trim() || undefined,
      module: moduleName.value.trim() || undefined,
      businessType: businessType.value === '__all__' ? undefined : businessType.value,
      responseStatus:
        responseStatus.value === '__all__' ? undefined : Number(responseStatus.value),
      clientSource: clientSource.value === '__all__' ? undefined : clientSource.value,
      startTime: startTime.value ? startTime.value.replace('T', ' ') + ':00' : undefined,
      endTime: endTime.value ? endTime.value.replace('T', ' ') + ':00' : undefined,
    }),
  initial: { page: 1, pageSize: 50 },
})

/** 手动刷新：重拉列表（保留当前筛选） */
const onRefresh = () => resource.load(true)

/* 即时筛选（防抖） */
let debounceTimer: ReturnType<typeof setTimeout> | null = null
const scheduleSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cursor.value = 0
    resource.search()
  }, 300)
}
watch(
  [operatorName, moduleName, businessType, responseStatus, clientSource, startTime, endTime],
  scheduleSearch,
)

const onClear = () => {
  operatorName.value = ''
  moduleName.value = ''
  businessType.value = '__all__'
  responseStatus.value = '__all__'
  clientSource.value = '__all__'
  startTime.value = ''
  endTime.value = ''
  cursor.value = 0
  resource.search()
}

/* ---------- 光标与选择 ---------- */
const cursor = ref(0)
const selected = ref<SysLog | null>(null)

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const searchInputRef = ref<HTMLInputElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  scrollerRef.value
    ?.querySelector(`[data-row="${cursor.value}"]`)
    ?.scrollIntoView({ block: 'nearest' })
}

const onSelect = (r: SysLog) => {
  selected.value = r
}

const onRowClick = (r: SysLog, i: number) => {
  cursor.value = i
  onSelect(r)
}

const onCloseInspector = () => {
  selected.value = null
}

/* ---------- 勾选与批量删除 ---------- */
const checkedIds = ref<Set<string>>(new Set())

const visibleIds = computed(() => resource.items.value.map((r) => r.id))
const allChecked = computed(
  () => visibleIds.value.length > 0 && visibleIds.value.every((id) => checkedIds.value.has(id)),
)
const someChecked = computed(() => checkedIds.value.size > 0 && !allChecked.value)

const onToggleRow = (r: SysLog, checked: boolean) => {
  const next = new Set(checkedIds.value)
  if (checked) next.add(r.id)
  else next.delete(r.id)
  checkedIds.value = next
}

const onToggleAll = (checked: boolean | 'indeterminate') => {
  checkedIds.value = checked === true ? new Set(visibleIds.value) : new Set()
}

watch([operatorName, moduleName, businessType, responseStatus, clientSource], () => {
  checkedIds.value = new Set()
})

const onBatchDelete = async () => {
  const ids = [...checkedIds.value]
  if (!ids.length) return
  const ok = await confirm({
    title: '批量删除日志',
    description: `确定要删除选中的 ${ids.length} 条日志吗？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteLogBatchApi(ids)
  const idSet = new Set(ids)
  resource.removeWhere((r) => idSet.has(r.id))
  if (selected.value && idSet.has(selected.value.id)) selected.value = null
  toast.success(`已删除 ${ids.length} 条日志`)
  checkedIds.value = new Set()
}

/* ---------- 导出 ---------- */
const exporting = ref(false)
const onExport = async () => {
  exporting.value = true
  try {
    const ids = [...checkedIds.value]
    const res = await exportLogApi({
      page: 1,
      pageSize: 50,
      operatorName: operatorName.value.trim() || undefined,
      module: moduleName.value.trim() || undefined,
      businessType: businessType.value === '__all__' ? undefined : businessType.value,
      responseStatus:
        responseStatus.value === '__all__' ? undefined : Number(responseStatus.value),
      clientSource: clientSource.value === '__all__' ? undefined : clientSource.value,
      ids: ids.length ? ids : undefined,
    })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '操作日志.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    toast.success('导出成功')
  } finally {
    exporting.value = false
  }
}

/* ---------- inspector 内删除 ---------- */
const onDeleted = (r: SysLog) => {
  resource.removeWhere((item) => item.id === r.id)
  selected.value = null
}

/* ---------- 行内删除（快捷键 Del） ---------- */
const onDeleteRow = async (r: SysLog) => {
  const ok = await confirm({
    title: '删除日志',
    description: '确定要删除该日志吗？',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteLogApi(r.id)
  toast.success('日志已删除')
  onDeleted(r)
}

/* ---------- 键盘流 ---------- */
const isTypingTarget = (el: EventTarget | null): boolean => {
  if (!(el instanceof HTMLElement)) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable
}

const onGlobalKeydown = (e: KeyboardEvent) => {
  if (isTypingTarget(e.target)) {
    if (e.key === 'Escape') (e.target as HTMLElement).blur()
    return
  }

  const list = resource.items.value
  switch (e.key) {
    case '/':
      e.preventDefault()
      searchInputRef.value?.focus()
      break
    case 'j':
    case 'J':
      e.preventDefault()
      if (!list.length) break
      cursor.value = Math.min(list.length - 1, cursor.value + 1)
      scrollCursorIntoView()
      break
    case 'k':
    case 'K':
      e.preventDefault()
      cursor.value = Math.max(0, cursor.value - 1)
      scrollCursorIntoView()
      break
    case 'Enter':
      e.preventDefault()
      if (list[cursor.value]) onSelect(list[cursor.value])
      break
    case 'Delete':
    case 'Backspace':
      e.preventDefault()
      if (list[cursor.value]) onDeleteRow(list[cursor.value])
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

/* ---------- 生命周期 ---------- */
const route = useRoute()
const router = useRouter()
let observer: IntersectionObserver | null = null

const trySelect = async (id: string) => {
  const hit = resource.items.value.find((r) => r.id === id)
  if (hit) {
    cursor.value = resource.items.value.indexOf(hit)
    onSelect(hit)
    return
  }
  const res = await getLogPageApi({ page: 1, pageSize: 999 })
  const target = res.data.rows.find((r) => r.id === id)
  if (target) onSelect(target)
}

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

  const selectId = route.query.select
  if (typeof selectId === 'string') {
    await trySelect(selectId)
    router.replace({ path: route.path, query: {} })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  observer?.disconnect()
  if (debounceTimer) clearTimeout(debounceTimer)
})

/* 列表刷新后：选中项同步、光标收敛 */
watch(
  () => resource.items.value,
  (list) => {
    if (selected.value) {
      const fresh = list.find((r) => r.id === selected.value!.id)
      if (fresh) selected.value = fresh
    }
    if (cursor.value > list.length - 1) cursor.value = Math.max(0, list.length - 1)
  },
)
</script>

<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧：列表 -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">操作日志</span>
        <span v-if="checkedIds.size" class="ml-2 text-xs text-muted-foreground">
          已选 {{ checkedIds.size }} 项
        </span>
        <div class="flex-1" />
        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
        <Button
          v-access="PERM.LOG_DELETE"
          variant="ghost"
          size="sm"
          class="gap-1.5"
          :disabled="!checkedIds.size"
          @click="onBatchDelete"
        >
          <IconTrash class="size-3.5" />
          批量删除
        </Button>
        <Button
          v-access="PERM.LOG_EXPORT"
          variant="ghost"
          size="sm"
          class="gap-1.5"
          :disabled="exporting"
          @click="onExport"
        >
          <IconLoader2 v-if="exporting" class="size-3.5 animate-spin" />
          <IconDownload v-else class="size-3.5" />
          导出
        </Button>
      </div>

      <!-- 搜索行 -->
      <div class="flex h-12 shrink-0 flex-wrap items-center gap-2 border-b border-border px-3">
        <IconSearch class="size-3.5 shrink-0 text-muted-foreground" />
        <input
          ref="searchInputRef"
          v-model="operatorName"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none focus:bg-muted/70"
          placeholder="请输入操作人"
        />
        <input
          v-model="moduleName"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none focus:bg-muted/70"
          placeholder="请输入模块"
        />
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
              <th class="w-10 px-3 py-2">
                <Checkbox
                  :model-value="allChecked"
                  :indeterminate="someChecked"
                  @update:model-value="onToggleAll"
                  @click.stop
                />
              </th>
              <th class="w-32 px-3 py-2 font-normal">操作人员</th>
              <th class="w-28 px-3 py-2 font-normal">模块</th>
              <th class="group w-24 px-3 py-2 text-center font-normal">
                <Select v-model="businessType">
                  <RawSelectTrigger
                    class="mx-auto inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    <span class="flex items-center gap-1">
                      类型
                      <IconFilter
                        class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                        :class="businessType !== '__all__' ? 'fill-primary text-primary opacity-100' : ''"
                      />
                    </span>
                  </RawSelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">全部类型</SelectItem>
                    <SelectItem v-for="opt in BUSINESS_TYPE_OPTIONS" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </th>
              <th class="px-3 py-2 font-normal">操作描述</th>
              <th class="group w-24 px-3 py-2 text-center font-normal">
                <Select v-model="responseStatus">
                  <RawSelectTrigger
                    class="mx-auto inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    <span class="flex items-center gap-1">
                      状态
                      <IconFilter
                        class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                        :class="responseStatus !== '__all__' ? 'fill-primary text-primary opacity-100' : ''"
                      />
                    </span>
                  </RawSelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">全部状态</SelectItem>
                    <SelectItem value="1">成功</SelectItem>
                    <SelectItem value="0">失败</SelectItem>
                  </SelectContent>
                </Select>
              </th>
              <th class="w-24 px-3 py-2 text-right font-normal">耗时</th>
              <th class="group w-36 px-3 py-2 font-normal">
                <Select v-model="clientSource">
                  <RawSelectTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    <span class="flex items-center gap-1">
                      客户端
                      <IconFilter
                        class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                        :class="clientSource !== '__all__' ? 'fill-primary text-primary opacity-100' : ''"
                      />
                    </span>
                  </RawSelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">全部来源</SelectItem>
                    <SelectItem value="web">web</SelectItem>
                    <SelectItem value="app">app</SelectItem>
                  </SelectContent>
                </Select>
              </th>
              <th class="group w-44 px-3 py-2 font-normal">
                <PopoverRoot>
                  <PopoverTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    时间
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="startTime || endTime ? 'fill-primary text-primary opacity-100' : ''"
                    />
                  </PopoverTrigger>
                  <PopoverPortal>
                    <PopoverContent
                      align="end"
                      side="bottom"
                      :side-offset="6"
                      class="z-50 w-64 rounded-md border border-border bg-popover p-3 text-popover-foreground shadow-md outline-none"
                    >
                      <div class="mb-2 text-xs font-medium text-foreground">时间范围</div>
                      <div class="flex flex-col gap-2">
                        <label class="flex flex-col gap-1 text-[11px] text-muted-foreground">
                          开始
                          <input
                            v-model="startTime"
                            type="datetime-local"
                            class="h-7 rounded-md border border-input bg-transparent px-2 text-xs outline-none focus:border-ring"
                          />
                        </label>
                        <label class="flex flex-col gap-1 text-[11px] text-muted-foreground">
                          结束
                          <input
                            v-model="endTime"
                            type="datetime-local"
                            class="h-7 rounded-md border border-input bg-transparent px-2 text-xs outline-none focus:border-ring"
                          />
                        </label>
                      </div>
                      <div class="mt-3 flex justify-end gap-2">
                        <Button
                          v-if="startTime || endTime"
                          variant="ghost"
                          size="sm"
                          class="h-7 text-xs"
                          @click="startTime = ''; endTime = ''"
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
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(r, i) in resource.items.value"
              :key="r.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                selected?.id === r.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(r, i)"
            >
              <td class="px-3 py-2" @click.stop>
                <Checkbox
                  :model-value="checkedIds.has(r.id)"
                  @update:model-value="(v) => onToggleRow(r, v === true)"
                  @click.stop
                />
              </td>
              <td class="px-3 py-2">{{ r.operatorName || '—' }}</td>
              <td class="px-3 py-2 text-xs">{{ r.module || '—' }}</td>
              <td class="px-3 py-2 text-center">
                <span
                  class="inline-flex rounded px-1.5 py-0.5 text-[11px]"
                  :class="businessMetaOf(r.businessType).badge"
                >
                  {{ businessMetaOf(r.businessType).label }}
                </span>
              </td>
              <td class="truncate px-3 py-2" :title="r.description">
                {{ r.description || '—' }}
              </td>
              <td class="px-3 py-2 text-center">
                <span
                  class="inline-flex rounded px-1.5 py-0.5 text-[11px]"
                  :class="
                    r.responseStatus === 1
                      ? 'bg-emerald-500/10 text-emerald-600'
                      : 'bg-destructive/10 text-destructive'
                  "
                >
                  {{ r.responseStatus === 1 ? '成功' : '失败' }}
                </span>
              </td>
              <td class="px-3 py-2 text-right font-mono text-xs">{{ r.costMs }}ms</td>
              <td class="px-3 py-2 font-mono text-xs">
                <span>{{ r.clientSource || '—' }}</span>
                <span class="ml-1.5 text-muted-foreground">{{ r.clientIp || '' }}</span>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ r.createTime || '—' }}
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
          <IconFileOff class="size-8" />
          <div class="text-[13px]">没有匹配的日志</div>
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

      <!-- 底部快捷键提示 -->
      <div
        class="flex h-8 shrink-0 items-center gap-3 border-t border-border px-3 text-[11px] text-muted-foreground/70"
      >
        <span><kbd>/</kbd> 搜索</span>
        <span><kbd>J</kbd><kbd>K</kbd> 移动</span>
        <span><kbd>Enter</kbd> 查看</span>
        <span><kbd>R</kbd> 刷新</span>
        <span><kbd>Del</kbd> 删除</span>
        <span><kbd>Esc</kbd> 关闭</span>
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 条日志</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <LogInspector :log="selected" @close="onCloseInspector" @deleted="onDeleted" />
        </div>
      </aside>
    </Transition>
  </div>
</template>

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
