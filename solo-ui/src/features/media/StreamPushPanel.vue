<template>
  <div class="flex h-full gap-3 overflow-hidden">
    <!-- 左侧：列表 -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">推流列表</span>

        <span v-if="checkedIds.size" class="ml-2 text-xs text-muted-foreground">
          已选 {{ checkedIds.size }} 项
        </span>

        <div class="flex-1" />

        <Button
          v-access="PERM.MEDIA_STREAM_PUSH_DELETE"
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
      </div>

      <!-- 搜索行 -->
      <div class="flex h-12 shrink-0 items-center gap-2 px-3">
        <IconSearch class="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        <input
          ref="appRef"
          v-model="app"
          class="h-7 w-32 rounded-md bg-transparent px-2 font-mono text-[13px] outline-none placeholder:font-sans placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="应用名"
        />
        <input
          ref="streamInputRef"
          v-model="stream"
          class="h-7 w-44 rounded-md bg-transparent px-2 font-mono text-[13px] outline-none placeholder:font-sans placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="流标识"
        />

        <Select v-model="serverFilter">
          <RawSelectTrigger
            class="inline-flex h-7 items-center gap-1 rounded-md px-2 text-xs text-muted-foreground outline-none hover:bg-muted/70"
          >
            <span class="flex items-center gap-1">
              节点
              <IconFilter
                class="size-3 opacity-40"
                :class="serverFilter !== '__all__' ? 'text-primary opacity-100' : ''"
              />
            </span>
          </RawSelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">全部节点</SelectItem>
            <SelectItem v-for="s in servers" :key="s.serverId" :value="s.serverId">
              {{ s.name || s.serverId }}
            </SelectItem>
          </SelectContent>
        </Select>

        <span v-if="hasCondition" class="text-xs text-muted-foreground">
          匹配 {{ resource.total.value }} 路
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
              <th class="w-10 px-3 py-2">
                <Checkbox
                  :model-value="allChecked"
                  :indeterminate="someChecked"
                  @update:model-value="onToggleAll"
                  @click.stop
                />
              </th>
              <th class="w-36 px-3 py-2 font-normal">应用名</th>
              <th class="w-52 px-3 py-2 font-normal">流标识</th>
              <th class="w-32 px-3 py-2 font-normal">推流类型</th>
              <th class="w-40 px-3 py-2 font-normal">目标节点</th>
              <th class="w-32 px-3 py-2 font-normal">推流方</th>
              <th class="w-24 px-3 py-2 font-normal">已持续</th>
              <th class="w-44 px-3 py-2 font-normal">开始时间</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(p, i) in resource.items.value"
              :key="p.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                selected?.id === p.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(p, i)"
            >
              <td class="px-3 py-2">
                <Checkbox
                  :model-value="checkedIds.has(p.id)"
                  @update:model-value="(v) => onToggleRow(p, v === true)"
                  @click.stop
                />
              </td>
              <td class="relative px-3 py-2">
                <span
                  v-if="selected?.id === p.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <span class="truncate font-mono text-xs" :title="p.app">{{ p.app }}</span>
              </td>
              <td class="truncate px-3 py-2 font-mono" :title="p.stream">{{ p.stream }}</td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ originTypeText(p.originType) }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ p.mediaServerId }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ p.clientId || '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ formatDuration(p.aliveSecond) }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ p.pushTime || '—' }}
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
          <IconBroadcast class="size-8" />
          <div class="text-[13px]">{{ hasCondition ? '没有匹配的推流' : '当前没有活动推流' }}</div>
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
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 路推流</span>
      </div>
    </div>

    <!-- 右侧：检视器（顶部实时画面，选中即播） -->
    <Transition name="inspector">
      <aside
        v-if="selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <StreamPlayPanel
            kind="push"
            :target="selected"
            :show-header="false"
            class="mb-4"
          />
          <StreamPushInspector
            :push="selected"
            @close="onCloseInspector"
            @stopped="onChanged"
            @deleted="onDeleted"
          />
        </div>
      </aside>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconSearch,
  IconX,
  IconLoader2,
  IconFilter,
  IconBroadcast,
} from '@tabler/icons-vue'
import { Trash2, Loader2 } from 'lucide-vue-next'
import StreamPushInspector from '@/features/media/StreamPushInspector.vue'
import StreamPlayPanel from '@/features/media/StreamPlayPanel.vue'
import {
  getStreamPushPageApi,
  batchDeleteStreamPushApi,
} from '@/api/media'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem } from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import type {
  MediaServer,
  StreamPush,
  StreamPushPageRequest,
} from '@/types/api'

const props = defineProps<{
  servers: MediaServer[]
  active: boolean
}>()

const app = ref('')
const stream = ref('')
const serverFilter = ref('__all__')

const hasCondition = computed(
  () =>
    !!app.value.trim() ||
    !!stream.value.trim() ||
    serverFilter.value !== '__all__',
)

const resource = useResource<StreamPushPageRequest, StreamPush>({
  fetcher: (q) =>
    getStreamPushPageApi({
      ...q,
      app: app.value.trim() || undefined,
      stream: stream.value.trim() || undefined,
      mediaServerId: serverFilter.value === '__all__' ? undefined : serverFilter.value,
    }),
  initial: { page: 1, pageSize: 50 },
})

let debounceTimer: ReturnType<typeof setTimeout> | null = null
const triggerSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cursor.value = 0
    resource.search()
  }, 300)
}
watch(app, triggerSearch)
watch(stream, triggerSearch)
watch(serverFilter, () => {
  cursor.value = 0
  resource.search()
})

const onClear = () => {
  app.value = ''
  stream.value = ''
  serverFilter.value = '__all__'
  cursor.value = 0
  resource.search()
}

const cursor = ref(0)
const selected = ref<StreamPush | null>(null)

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const appRef = ref<HTMLInputElement>()
const streamInputRef = ref<HTMLInputElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  scrollerRef.value?.querySelector(`[data-row="${cursor.value}"]`)?.scrollIntoView({ block: 'nearest' })
}

const onRowClick = (p: StreamPush, i: number) => {
  cursor.value = i
  selected.value = p
}

const onCloseInspector = () => {
  selected.value = null
}

const onChanged = () => resource.search()
const onDeleted = (p: StreamPush) => {
  resource.removeWhere((item) => item.id === p.id)
  selected.value = null
}

/* 批量勾选 / 删除 */
const checkedIds = ref<Set<string>>(new Set())
const batchDeleting = ref(false)

const visibleIds = computed(() => resource.items.value.map((p) => p.id))
const allChecked = computed(
  () => visibleIds.value.length > 0 && visibleIds.value.every((id) => checkedIds.value.has(id)),
)
const someChecked = computed(() => checkedIds.value.size > 0 && !allChecked.value)

const onToggleRow = (p: StreamPush, checked: boolean) => {
  const next = new Set(checkedIds.value)
  if (checked) next.add(p.id)
  else next.delete(p.id)
  checkedIds.value = next
}

const onToggleAll = (checked: boolean | 'indeterminate') => {
  checkedIds.value = checked === true ? new Set(visibleIds.value) : new Set()
}

const onBatchDelete = async () => {
  const ids = [...checkedIds.value]
  if (!ids.length) return
  const ok = await confirm({
    title: '批量删除推流记录',
    description: `确认删除选中的 ${ids.length} 条记录？仍在推流的行会跳过。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  batchDeleting.value = true
  try {
    await batchDeleteStreamPushApi(ids)
    toast.success('操作完成')
    checkedIds.value = new Set()
    resource.search()
  } finally {
    batchDeleting.value = false
  }
}

const ORIGIN_TYPE_MAP: Record<string, string> = {
  rtmp: 'RTMP 推流',
  rtsp: 'RTSP 推流',
  rtc: 'WebRTC 推流',
}
const originTypeText = (t?: string) =>
  !t ? '—' : (ORIGIN_TYPE_MAP[t] ?? t.toUpperCase())

const formatDuration = (sec?: number) => {
  if (sec === undefined || sec === null) return '—'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  if (h) return `${h}h ${m}m`
  if (m) return `${m}m ${s}s`
  return `${s}s`
}

/* 键盘流：仅面板激活时响应 */
const isTypingTarget = (el: EventTarget | null) =>
  el instanceof HTMLElement &&
  (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable)

const onGlobalKeydown = (e: KeyboardEvent) => {
  if (!props.active) return
  if (isTypingTarget(e.target)) {
    if (e.key === 'Escape') (e.target as HTMLElement).blur()
    return
  }
  const list = resource.items.value
  switch (e.key) {
    case '/':
      e.preventDefault()
      streamInputRef.value?.focus()
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
      const fresh = list.find((p) => p.id === selected.value!.id)
      if (fresh) selected.value = fresh
    }
    if (cursor.value > list.length - 1) cursor.value = Math.max(0, list.length - 1)
  },
)

defineExpose({ reload: () => resource.search() })
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
