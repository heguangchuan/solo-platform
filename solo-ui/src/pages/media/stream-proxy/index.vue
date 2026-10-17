<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧：列表卡片 -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">拉流代理</span>

        <span v-if="checkedIds.size" class="ml-2 text-xs text-muted-foreground">
          已选 {{ checkedIds.size }} 项
        </span>

        <div class="flex-1" />

        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />

        <Button
          v-access="PERM.MEDIA_STREAM_PROXY_DELETE"
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
          v-access="PERM.MEDIA_STREAM_PROXY_ADD"
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
          ref="streamRef"
          v-model="stream"
          class="h-7 w-56 rounded-md bg-transparent px-2 font-mono text-[13px] outline-none placeholder:font-sans placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="流标识"
        />

        <Select v-model="statusFilter">
          <RawSelectTrigger
            class="inline-flex h-7 items-center gap-1 rounded-md px-2 text-xs text-muted-foreground outline-none hover:bg-muted/70"
          >
            <span class="flex items-center gap-1">
              状态
              <IconFilter
                class="size-3 opacity-40"
                :class="statusFilter !== '__all__' ? 'text-primary opacity-100' : ''"
              />
            </span>
          </RawSelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">全部</SelectItem>
            <SelectItem value="1">运行中</SelectItem>
            <SelectItem value="0">已停止</SelectItem>
          </SelectContent>
        </Select>

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
              <th class="w-10 px-3 py-2">
                <Checkbox
                  :model-value="allChecked"
                  :indeterminate="someChecked"
                  @update:model-value="onToggleAll"
                  @click.stop
                />
              </th>
              <th class="w-32 px-3 py-2 font-normal">应用名</th>
              <th class="w-52 px-3 py-2 font-normal">流标识</th>
              <th class="w-64 px-3 py-2 font-normal">源地址</th>
              <th class="w-36 px-3 py-2 font-normal">目标节点</th>
              <th class="w-24 px-3 py-2 font-normal">状态</th>
              <th class="w-44 px-3 py-2 font-normal">更新时间</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(p, i) in resource.items.value"
              :key="p.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                !creating && selected?.id === p.id
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
                  v-if="!creating && selected?.id === p.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <span class="truncate font-mono text-xs" :title="p.app">{{ p.app }}</span>
              </td>
              <td class="px-3 py-2">
                <span class="flex min-w-0 items-center gap-1">
                  <span class="min-w-0 truncate font-mono" :title="p.stream">{{ p.stream }}</span>
                  <button
                    type="button"
                    class="hidden size-5 shrink-0 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground group-hover:flex"
                    title="播放"
                    :disabled="p.status !== 1"
                    @click.stop="onPlay(p)"
                  >
                    <IconPlayerPlay class="size-3.5" />
                  </button>
                </span>
              </td>
              <td class="px-3 py-2">
                <span
                  class="block truncate font-mono text-xs text-muted-foreground"
                  :title="p.srcUrl"
                  >{{ p.srcUrl }}</span
                >
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ p.mediaServerId || '自动' }}
              </td>
              <td class="px-3 py-2">
                <span
                  class="text-xs"
                  :class="p.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'"
                  :title="p.status !== 1 && p.lastError ? p.lastError : ''"
                >
                  {{ p.status === 1 ? '运行中' : '已停止' }}
                </span>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ p.updateTime || '—' }}
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
          <IconShareOff class="size-8" />
          <div class="text-[13px]">{{ hasCondition ? '没有匹配的代理' : '还没有创建拉流代理' }}</div>
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
        <span>共 {{ resource.total.value }} 个代理</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="creating || selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <StreamProxyInspector
            :proxy="selected ?? undefined"
            :servers="servers"
            :creating="creating"
            @close="onCloseInspector"
            @saved="onSaved"
            @created="onCreated"
            @deleted="onDeleted"
            @enable="onEnable"
            @disable="onDisable"
            @play="onPlay"
          />
        </div>
      </aside>
    </Transition>

    <!-- 播放弹窗 -->
    <StreamPlayDialog ref="playDialogRef" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconSearch,
  IconX,
  IconLoader2,
  IconFilter,
  IconPlayerPlay,
  IconShareOff,
} from '@tabler/icons-vue'
import { Plus, Trash2, Loader2 } from 'lucide-vue-next'
import StreamProxyInspector from '@/features/media/StreamProxyInspector.vue'
import StreamPlayDialog from '@/features/media/StreamPlayDialog.vue'
import {
  getStreamProxyPageApi,
  listMediaServerApi,
  enableStreamProxyApi,
  disableStreamProxyApi,
  batchDeleteStreamProxyApi,
} from '@/api/media'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem } from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import type {
  MediaServer,
  StreamProxy,
  StreamProxyPageRequest,
} from '@/types/api'

const stream = ref('')
const statusFilter = ref<'__all__' | '1' | '0'>('__all__')

const hasCondition = computed(
  () => !!stream.value.trim() || statusFilter.value !== '__all__',
)

const resource = useResource<StreamProxyPageRequest, StreamProxy>({
  fetcher: (q) =>
    getStreamProxyPageApi({
      ...q,
      stream: stream.value.trim() || undefined,
      status: statusFilter.value === '__all__' ? undefined : Number(statusFilter.value),
    }),
  initial: { page: 1, pageSize: 50 },
})

let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(stream, () => {
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
  stream.value = ''
  statusFilter.value = '__all__'
  cursor.value = 0
  resource.search()
}

const cursor = ref(0)
const selected = ref<StreamProxy | null>(null)
const creating = ref(false)
const servers = ref<MediaServer[]>([])

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const streamRef = ref<HTMLInputElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  scrollerRef.value?.querySelector(`[data-row="${cursor.value}"]`)?.scrollIntoView({ block: 'nearest' })
}

const onRowClick = (p: StreamProxy, i: number) => {
  cursor.value = i
  selected.value = p
}

const onCloseInspector = () => {
  creating.value = false
  selected.value = null
}

const onCreate = () => {
  selected.value = null
  creating.value = true
}

const onSaved = () => resource.search()
const onCreated = () => {
  creating.value = false
  resource.search()
}
const onDeleted = (p: StreamProxy) => {
  resource.removeWhere((item) => item.id === p.id)
  selected.value = null
  creating.value = false
}

/* 启停 */
const onEnable = async (p: StreamProxy) => {
  await enableStreamProxyApi(p.id)
  toast.success('已启用')
  resource.search()
}
const onDisable = async (p: StreamProxy) => {
  await disableStreamProxyApi(p.id)
  toast.success('已停用')
  resource.search()
}

/* 播放 */
const playDialogRef = ref<InstanceType<typeof StreamPlayDialog>>()
const onPlay = (p: StreamProxy) => playDialogRef.value?.open(p, 'proxy')

/* 批量勾选 / 删除 */
const checkedIds = ref<Set<string>>(new Set())
const batchDeleting = ref(false)

const visibleIds = computed(() => resource.items.value.map((p) => p.id))
const allChecked = computed(
  () => visibleIds.value.length > 0 && visibleIds.value.every((id) => checkedIds.value.has(id)),
)
const someChecked = computed(() => checkedIds.value.size > 0 && !allChecked.value)

const onToggleRow = (p: StreamProxy, checked: boolean) => {
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
    title: '批量删除拉流代理',
    description: `确认删除选中的 ${ids.length} 个代理？删除后不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  batchDeleting.value = true
  try {
    await batchDeleteStreamProxyApi(ids)
    toast.success('已删除')
    checkedIds.value = new Set()
    resource.search()
  } finally {
    batchDeleting.value = false
  }
}

/* 键盘流 */
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
      streamRef.value?.focus()
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

/** 手动刷新：重拉列表（保留当前筛选）+ 节点下拉 */
const onRefresh = async () => {
  const [, serverRes] = await Promise.all([
    resource.load(true),
    listMediaServerApi().catch(() => null),
  ])
  if (serverRes?.data) servers.value = serverRes.data
}

let observer: IntersectionObserver | null = null

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown)
  const [, serverRes] = await Promise.all([
    resource.load(true),
    listMediaServerApi().catch(() => null),
  ])
  if (serverRes?.data) servers.value = serverRes.data

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
