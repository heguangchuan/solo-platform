<template>
  <div ref="rootRef" class="relative flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧：应用列表 -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">开放应用</span>
        <div class="flex-1" />
        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
        <Button
          v-access="PERM.OPENAPI_APP_ADD"
          variant="ghost"
          size="sm"
          class="gap-1.5"
          @click="onCreate"
        >
          <IconPlus class="size-3.5" />
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
          ref="appNameRef"
          v-model="appName"
          class="h-7 w-56 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="搜索应用名称"
        />

        <span v-if="hasCondition" class="text-xs text-muted-foreground">
          匹配 {{ resource.total.value }} 个
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
              <th class="w-48 px-3 py-2 font-normal">appKey</th>
              <th class="w-40 px-3 py-2 font-normal">应用名称</th>
              <th class="w-44 px-3 py-2 font-normal">授权范围</th>
              <th class="group w-20 px-3 py-2 font-normal">
                <Select v-model="statusFilter">
                  <RawSelectTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    状态
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="
                        statusFilter !== '__all__'
                          ? 'fill-primary text-primary opacity-100'
                          : ''
                      "
                    />
                  </RawSelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">全部</SelectItem>
                    <SelectItem value="1">启用</SelectItem>
                    <SelectItem value="0">停用</SelectItem>
                  </SelectContent>
                </Select>
              </th>
              <th class="w-16 px-3 py-2 font-normal">QPS</th>
              <th class="w-40 px-3 py-2 font-normal">过期时间</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(a, i) in resource.items.value"
              :key="a.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                !creating && selected?.id === a.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(a, i)"
            >
              <td class="relative px-3 py-2">
                <span
                  v-if="!creating && selected?.id === a.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <span class="truncate font-mono text-xs" :title="a.appKey">{{ a.appKey }}</span>
              </td>
              <td class="truncate px-3 py-2" :title="a.appName">{{ a.appName }}</td>
              <td class="truncate px-3 py-2 font-mono text-xs text-muted-foreground" :title="a.scopes || ''">
                {{ a.scopes || '—' }}
              </td>
              <td class="px-3 py-2">
                <span
                  class="text-xs"
                  :class="a.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'"
                >
                  {{ a.status === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ a.qpsLimit ? a.qpsLimit : '不限' }}
              </td>
              <td class="truncate px-3 py-2 font-mono text-xs text-muted-foreground" :title="a.expireTime || ''">
                {{ a.expireTime || '永不过期' }}
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
          <IconKey class="size-8" />
          <div class="text-[13px]">{{ hasCondition ? '没有匹配的应用' : '还没有创建开放应用' }}</div>
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
        <span>共 {{ resource.total.value }} 个应用</span>
      </div>
    </div>

    <!-- 右侧：检视器。拖宽超过阈值后浮在列表上方，避免挤窄表格 -->
    <Transition name="inspector">
      <div
        v-if="creating || selected"
        class="flex shrink-0"
        :class="inspectorFloating ? 'absolute inset-y-3 right-3 z-20' : 'relative'"
        :style="{ width: `${inspectorWidthPx}px` }"
      >
        <button
          type="button"
          aria-label="调整检视器宽度"
          title="拖动调整宽度，双击恢复默认"
          class="group absolute inset-y-0 -left-2 z-10 flex w-4 cursor-col-resize items-center justify-center outline-none"
          @pointerdown="onResizeStart"
          @dblclick="resetInspectorWidth"
          @keydown.left.prevent="nudgeInspectorWidth(24)"
          @keydown.right.prevent="nudgeInspectorWidth(-24)"
        >
          <span
            class="h-10 w-0.5 rounded-full bg-border transition-colors group-hover:bg-primary/70 group-focus-visible:bg-primary"
            :class="dragging && 'bg-primary'"
          />
        </button>

        <aside
          class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card"
          :class="inspectorFloating && 'shadow-2xl shadow-black/30'"
        >
          <div class="min-h-0 flex-1 overflow-y-auto p-5">
            <OpenAppInspector
              :app="selected ?? undefined"
              :creating="creating"
              @close="onCloseInspector"
              @changed="onChanged"
            />
          </div>
        </aside>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { IconSearch, IconX, IconPlus, IconKey, IconLoader2, IconFilter } from '@tabler/icons-vue'
import OpenAppInspector from '@/features/openapi/OpenAppInspector.vue'
import { getOpenAppPageApi } from '@/api/openapi'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import Button from '@/components/ui/button/Button.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import { Select, SelectContent, SelectItem } from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import type { OpenApp, OpenAppPageRequest } from '@/types/api'

const appName = ref('')
const statusFilter = ref<'__all__' | '1' | '0'>('__all__')

const hasCondition = computed(
  () => !!appName.value.trim() || statusFilter.value !== '__all__',
)

const resource = useResource<OpenAppPageRequest, OpenApp>({
  fetcher: (q) =>
    getOpenAppPageApi({
      ...q,
      appName: appName.value.trim() || undefined,
      status: statusFilter.value === '__all__' ? undefined : Number(statusFilter.value),
    }),
  initial: { page: 1, pageSize: 50 },
})

const onRefresh = () => resource.load(true)

let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(appName, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cursor.value = 0
    resource.search()
  }, 300)
})
watch(statusFilter, () => {
  cursor.value = 0
  resource.search()
})

const onClear = () => {
  appName.value = ''
  statusFilter.value = '__all__'
  cursor.value = 0
  resource.search()
}

const cursor = ref(0)
const selected = ref<OpenApp | null>(null)
const creating = ref(false)

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const appNameRef = ref<HTMLInputElement>()

/* ---------- 检视器宽度：可拖拽，过宽改为浮层 ---------- */
const INSPECTOR_WIDTH_KEY = 'openapi.app.inspector-width'
const INSPECTOR_DEFAULT_WIDTH = 440
const INSPECTOR_MIN_WIDTH = 360
const INSPECTOR_MAX_WIDTH = 960
const INSPECTOR_DOCK_MAX_WIDTH = 560

const readStoredWidth = () => {
  const stored = Number(localStorage.getItem(INSPECTOR_WIDTH_KEY))
  if (!Number.isFinite(stored) || stored <= 0) return INSPECTOR_DEFAULT_WIDTH
  return Math.min(INSPECTOR_MAX_WIDTH, Math.max(INSPECTOR_MIN_WIDTH, stored))
}

const rootRef = ref<HTMLElement>()
const rootWidth = ref(0)
const inspectorWidth = ref(readStoredWidth())
const dragging = ref(false)

const clampInspectorWidth = (width: number) => {
  const upper = rootWidth.value
    ? Math.max(INSPECTOR_MIN_WIDTH, Math.min(INSPECTOR_MAX_WIDTH, rootWidth.value - 48))
    : INSPECTOR_MAX_WIDTH
  return Math.round(Math.min(upper, Math.max(INSPECTOR_MIN_WIDTH, width)))
}

const inspectorWidthPx = computed(() => clampInspectorWidth(inspectorWidth.value))
// 超过停靠上限后不再挤压列表，改为浮在列表上方
const inspectorFloating = computed(() => inspectorWidthPx.value > INSPECTOR_DOCK_MAX_WIDTH)

const persistInspectorWidth = () =>
  localStorage.setItem(INSPECTOR_WIDTH_KEY, String(inspectorWidth.value))

let dragStartX = 0
let dragStartWidth = 0

const onResizeStart = (e: PointerEvent) => {
  if (e.button !== 0) return
  dragging.value = true
  dragStartX = e.clientX
  dragStartWidth = inspectorWidthPx.value
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', onResizeEnd)
}

const onResizeMove = (e: PointerEvent) => {
  inspectorWidth.value = clampInspectorWidth(dragStartWidth + (dragStartX - e.clientX))
}

const onResizeEnd = () => {
  dragging.value = false
  document.body.style.userSelect = ''
  document.body.style.cursor = ''
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', onResizeEnd)
  persistInspectorWidth()
}

const resetInspectorWidth = () => {
  inspectorWidth.value = INSPECTOR_DEFAULT_WIDTH
  persistInspectorWidth()
}

const nudgeInspectorWidth = (delta: number) => {
  inspectorWidth.value = clampInspectorWidth(inspectorWidthPx.value + delta)
  persistInspectorWidth()
}

let widthObserver: ResizeObserver | null = null

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  scrollerRef.value?.querySelector(`[data-row="${cursor.value}"]`)?.scrollIntoView({ block: 'nearest' })
}

const onRowClick = (a: OpenApp, i: number) => {
  cursor.value = i
  selected.value = a
  creating.value = false
}

const onCloseInspector = () => {
  creating.value = false
  selected.value = null
}

const onCreate = () => {
  selected.value = null
  creating.value = true
}

// 应用增改/启停/换钥后重拉列表，当前选中项用最新数据刷新
const onChanged = () => resource.load(true)

const isTypingTarget = (el: EventTarget | null) =>
  el instanceof HTMLElement &&
  (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable)

const onGlobalKeydown = (e: KeyboardEvent) => {
  if (isTypingTarget(e.target)) {
    if (e.key === 'Escape') (e.target as HTMLElement).blur()
    return
  }
  const list = resource.items.value
  switch (e.key) {
    case '/':
      e.preventDefault()
      appNameRef.value?.focus()
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
      if (list[cursor.value]) onRowClick(list[cursor.value], cursor.value)
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

let observer: IntersectionObserver | null = null

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown)
  if (rootRef.value) {
    rootWidth.value = rootRef.value.clientWidth
    widthObserver = new ResizeObserver(() => {
      rootWidth.value = rootRef.value?.clientWidth ?? 0
    })
    widthObserver.observe(rootRef.value)
  }
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
  widthObserver?.disconnect()
  onResizeEnd()
  if (debounceTimer) clearTimeout(debounceTimer)
})

watch(
  () => resource.items.value,
  (list) => {
    if (selected.value) {
      const fresh = list.find((a) => a.id === selected.value!.id)
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
