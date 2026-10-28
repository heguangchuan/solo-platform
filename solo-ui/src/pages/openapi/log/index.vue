<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconSearch,
  IconDownload,
  IconLoader2,
  IconFileOff,
  IconX,
  IconFilter,
} from '@tabler/icons-vue'
import { getOpenApiLogPageApi, exportOpenApiLogApi } from '@/api/openapi'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import Button from '@/components/ui/button/Button.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import { Select, SelectContent, SelectItem } from '@/components/ui/select'
import {
  SelectTrigger as RawSelectTrigger,
  PopoverRoot,
  PopoverTrigger,
  PopoverPortal,
  PopoverContent,
  PopoverClose,
} from 'reka-ui'
import OpenApiLogInspector from '@/features/openapi/OpenApiLogInspector.vue'
import type { OpenApiLog, OpenApiLogPageRequest } from '@/types/api'

/* ---------- 查询条件 ---------- */
const appKey = ref('')
const path = ref('')
const result = ref<'__all__' | 'success' | 'fail'>('__all__')
const startTime = ref('')
const endTime = ref('')

const hasCondition = computed(
  () =>
    !!appKey.value.trim() ||
    !!path.value.trim() ||
    result.value !== '__all__' ||
    !!startTime.value ||
    !!endTime.value,
)

/** 组装后端查询体（时间选择器的 2026-10-28T10:00 转成 2026-10-28 10:00:00） */
const buildParams = (q: { page: number; pageSize: number }): OpenApiLogPageRequest => ({
  ...q,
  appKey: appKey.value.trim() || undefined,
  path: path.value.trim() || undefined,
  success:
    result.value === '__all__' ? undefined : result.value === 'success',
  startTime: startTime.value ? startTime.value.replace('T', ' ') + ':00' : undefined,
  endTime: endTime.value ? endTime.value.replace('T', ' ') + ':00' : undefined,
})

const resource = useResource<OpenApiLogPageRequest, OpenApiLog>({
  fetcher: (q) => getOpenApiLogPageApi(buildParams(q)),
  initial: { page: 1, pageSize: 50 },
})

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
watch([appKey, path, result, startTime, endTime], scheduleSearch)

const onClear = () => {
  appKey.value = ''
  path.value = ''
  result.value = '__all__'
  startTime.value = ''
  endTime.value = ''
  cursor.value = 0
  resource.search()
}

/* ---------- 光标与选中 ---------- */
const cursor = ref(0)
const selected = ref<OpenApiLog | null>(null)

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

const onRowClick = (r: OpenApiLog, i: number) => {
  cursor.value = i
  selected.value = r
}

const onCloseInspector = () => {
  selected.value = null
}

/* ---------- 导出（按当前筛选条件全量导出） ---------- */
const exporting = ref(false)
const onExport = async () => {
  exporting.value = true
  try {
    const res = await exportOpenApiLogApi(buildParams({ page: 1, pageSize: 50 }))
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '开放API调用日志.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    toast.success('导出成功')
  } finally {
    exporting.value = false
  }
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
      if (list[cursor.value]) onRowClick(list[cursor.value], cursor.value)
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
        <span class="text-[13px] font-medium">调用日志</span>
        <div class="flex-1" />
        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
        <Button
          v-access="PERM.OPENAPI_LOG_EXPORT"
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
          v-model="appKey"
          class="h-7 w-52 rounded-md bg-transparent px-2 font-mono text-[13px] outline-none focus:bg-muted/70"
          placeholder="appKey"
        />
        <input
          v-model="path"
          class="h-7 w-52 rounded-md bg-transparent px-2 font-mono text-[13px] outline-none focus:bg-muted/70"
          placeholder="请求路径"
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
              <th class="w-52 px-3 py-2 font-normal">appKey</th>
              <th class="w-16 px-3 py-2 font-normal">方法</th>
              <th class="px-3 py-2 font-normal">路径</th>
              <th class="w-32 px-3 py-2 font-normal">来源 IP</th>
              <th class="group w-24 px-3 py-2 font-normal">
                <Select v-model="result">
                  <RawSelectTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    <span class="flex items-center gap-1">
                      结果
                      <IconFilter
                        class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                        :class="result !== '__all__' ? 'fill-primary text-primary opacity-100' : ''"
                      />
                    </span>
                  </RawSelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">全部结果</SelectItem>
                    <SelectItem value="success">成功</SelectItem>
                    <SelectItem value="fail">失败</SelectItem>
                  </SelectContent>
                </Select>
              </th>
              <th class="w-24 px-3 py-2 font-normal">耗时</th>
              <th class="group w-44 px-3 py-2 font-normal">
                <PopoverRoot>
                  <PopoverTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    调用时间
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
              <td class="truncate px-3 py-2 font-mono text-xs" :title="r.appKey || ''">
                {{ r.appKey || '—' }}
              </td>
              <td class="px-3 py-2">
                <span class="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px]">{{ r.method }}</span>
              </td>
              <td class="truncate px-3 py-2 font-mono text-xs" :title="r.path">
                {{ r.path }}
              </td>
              <td class="truncate px-3 py-2 font-mono text-xs text-muted-foreground" :title="r.clientIp || ''">
                {{ r.clientIp || '—' }}
              </td>
              <td class="px-3 py-2">
                <span
                  class="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[11px]"
                  :class="
                    r.bizCode === 200
                      ? 'bg-emerald-500/10 text-emerald-600'
                      : 'bg-destructive/10 text-destructive'
                  "
                  :title="r.bizCode === 200 ? '' : (r.errorMsg || `业务码 ${r.bizCode}`)"
                >
                  {{ r.bizCode === 200 ? '成功' : `失败 ${r.bizCode ?? ''}` }}
                </span>
              </td>
              <td class="px-3 py-2 font-mono text-xs">{{ r.costMs }}ms</td>
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
          <OpenApiLogInspector :log="selected" @close="onCloseInspector" />
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
