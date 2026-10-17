<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧：单卡片（搜索 / 列表，无嵌套边框） -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">服务器列表</span>

        <span v-if="checkedIds.size" class="ml-2 text-xs text-muted-foreground">
          已选 {{ checkedIds.size }} 项
        </span>

        <div class="flex-1" />

        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />

        <Button
          variant="ghost"
          size="icon"
          class="size-8"
          title="导入"
          @click="onImport"
        >
          <Upload class="size-4" />
        </Button>

        <Button
          variant="ghost"
          size="icon"
          class="size-8"
          title="导出"
          @click="onExport"
        >
          <Download class="size-4" />
        </Button>

        <Button
          v-access="PERM.SERVER_DELETE"
          variant="ghost"
          size="icon"
          class="size-8"
          title="批量删除"
          :disabled="!checkedIds.size || batchDeleting"
          @click="onBatchDelete"
        >
          <IconLoader2 v-if="batchDeleting" class="size-4 animate-spin" />
          <IconTrash v-else class="size-4" />
        </Button>

        <Button
          v-access="PERM.SERVER_ADD"
          variant="ghost"
          size="sm"
          class="ml-1 gap-1.5"
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
          ref="nameRef"
          v-model="serverName"
          class="h-7 w-40 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入服务器名称"
        />
        <input
          ref="hostRef"
          v-model="host"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入IP或域名"
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
          <IconX class="h-3.5 w-3.5" />
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
              <th class="px-3 py-2 font-normal">服务器名称</th>
              <th class="w-36 px-3 py-2 font-normal">主机</th>
              <th class="w-16 px-3 py-2 font-normal">端口</th>
              <th class="w-24 px-3 py-2 font-normal">用户名</th>
              <th class="w-16 px-3 py-2 font-normal">认证</th>
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
                        :class="statusFilter !== '__all__' ? 'text-primary' : 'text-muted-foreground/60'"
                      />
                    </RawSelectTrigger>
                    <SelectContent>
                      <SelectItem value="__all__">全部状态</SelectItem>
                      <SelectItem value="1">启用</SelectItem>
                      <SelectItem value="0">停用</SelectItem>
                    </SelectContent>
                  </Select>
                </span>
              </th>
              <th class="w-44 px-3 py-2 font-normal">创建时间</th>
              <th class="w-44 px-3 py-2 font-normal">更新时间</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(s, i) in resource.items.value"
              :key="s.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                !creating && selected?.id === s.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(s, i)"
            >
              <td class="px-3 py-2">
                <Checkbox
                  :model-value="checkedIds.has(s.id)"
                  @update:model-value="(v) => onToggleRow(s, v === true)"
                  @click.stop
                />
              </td>
              <td class="relative px-3 py-2">
                <span
                  v-if="!creating && selected?.id === s.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <span class="flex min-w-0 items-center gap-1">
                  <span class="min-w-0 truncate font-medium" :title="s.serverName">{{ s.serverName }}</span>
                  <button
                    v-access="PERM.SERVER_TERMINAL"
                    type="button"
                    class="hidden size-5 shrink-0 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground group-hover:flex disabled:opacity-40"
                    title="打开终端"
                    :disabled="s.status !== 1"
                    @click.stop="onOpenTerminal(s)"
                  >
                    <IconTerminal class="h-3.5 w-3.5" />
                  </button>
                </span>
              </td>
              <td class="px-3 py-2 font-mono text-xs truncate">{{ s.host }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ s.port }}</td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground truncate">{{ s.username }}</td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ s.authType === 'PRIVATE_KEY' ? '密钥' : '密码' }}
              </td>
              <td class="px-3 py-2 text-xs">
                <span :class="s.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'">
                  {{ s.status === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground truncate">{{ s.createTime || '—' }}</td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground truncate">{{ s.updateTime || '—' }}</td>
            </tr>
          </tbody>
        </table>

        <!-- 首次加载 -->
        <div v-if="firstLoading" class="grid place-items-center py-16">
          <IconLoader2 class="h-5 w-5 animate-spin text-muted-foreground" />
        </div>

        <!-- 空状态 -->
        <div
          v-else-if="!resource.loading.value && resource.items.value.length === 0"
          class="flex flex-col items-center gap-2 py-16 text-muted-foreground"
        >
          <IconServerOff class="h-8 w-8" />
          <div class="text-[13px]">{{ hasCondition ? '没有匹配的服务器' : '还没有添加服务器' }}</div>
          <Button v-if="hasCondition" variant="ghost" size="sm" @click="onClear">
            清除搜索条件
          </Button>
        </div>

        <!-- 哨兵 -->
        <div ref="sentinelRef" class="grid place-items-center py-4">
          <IconLoader2
            v-if="resource.loading.value && resource.items.value.length"
            class="h-4 w-4 animate-spin text-muted-foreground"
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
        <span><kbd>N</kbd> 新建</span>
        <span><kbd>R</kbd> 刷新</span>
        <span><kbd>Del</kbd> 删除</span>
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 台服务器</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="creating || selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <ServerInspector
            :server="selected ?? undefined"
            :creating="creating"
            @close="onCloseInspector"
            @saved="onSaved"
            @created="onCreated"
            @deleted="onDeleted"
            @terminal="onOpenTerminal"
          />
        </div>
      </aside>
    </Transition>

    <!-- 全屏终端窗口 -->
    <TerminalWindow
      v-if="terminalServer"
      :server="terminalServer"
      @close="terminalServer = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconSearch,
  IconX,
  IconPlus,
  IconTrash,
  IconTerminal,
  IconServerOff,
  IconLoader2,
  IconFilter,
} from '@tabler/icons-vue'
import { Upload, Download } from 'lucide-vue-next'
import ServerInspector from '@/features/server/ServerInspector.vue'
import TerminalWindow from '@/features/server/TerminalWindow.vue'
import {
  getServerPageApi,
  deleteServerApi,
  deleteServerBatchApi,
} from '@/api/server'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import { Select, SelectContent, SelectItem } from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import type { SysServer, ServerPageRequest } from '@/types/api'

/* ---------- 搜索条件（严格对应后端 ServerPageRequest） ---------- */
const serverName = ref('')
const host = ref('')
const statusFilter = ref<'__all__' | '1' | '0'>('__all__')

const hasCondition = computed(
  () => !!serverName.value.trim() || !!host.value.trim() || statusFilter.value !== '__all__',
)

const resource = useResource<ServerPageRequest, SysServer>({
  fetcher: (q) =>
    getServerPageApi({
      ...q,
      serverName: serverName.value.trim() || undefined,
      host: host.value.trim() || undefined,
      status: statusFilter.value === '__all__' ? undefined : Number(statusFilter.value),
    }),
  initial: { page: 1, pageSize: 50 },
})

/** 手动刷新：重拉列表（保留当前筛选） */
const onRefresh = () => resource.load(true)

/* 输入即时筛选（防抖） */
let debounceTimer: ReturnType<typeof setTimeout> | null = null
const scheduleSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cursor.value = 0
    resource.search()
  }, 300)
}
watch([serverName, host], scheduleSearch)
watch(statusFilter, () => {
  cursor.value = 0
  resource.search()
})

const onClear = () => {
  serverName.value = ''
  host.value = ''
  statusFilter.value = '__all__'
  cursor.value = 0
  resource.search()
}

/* ---------- 光标与选择 ---------- */
const cursor = ref(0)
const selected = ref<SysServer | null>(null)
const creating = ref(false)

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const nameRef = ref<HTMLInputElement>()
const hostRef = ref<HTMLInputElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  const el = scrollerRef.value?.querySelector(`[data-row="${cursor.value}"]`)
  el?.scrollIntoView({ block: 'nearest' })
}

const onRowClick = (s: SysServer, i: number) => {
  cursor.value = i
  onSelect(s)
}

const onSelect = (s: SysServer) => {
  creating.value = false
  selected.value = s
}

const onCloseInspector = () => {
  creating.value = false
  selected.value = null
}

const onCreate = () => {
  selected.value = null
  creating.value = true
}

/* ---------- 终端 ---------- */
const terminalServer = ref<SysServer | null>(null)
const onOpenTerminal = (s: SysServer) => {
  terminalServer.value = s
}

/* ---------- 导入 / 导出（占位，暂未实现） ---------- */
const onImport = () => {
  toast.info('导入功能即将上线')
}
const onExport = () => {
  toast.info('导出功能即将上线')
}

/* ---------- 勾选与批量删除 ---------- */
const checkedIds = ref<Set<string>>(new Set())
const batchDeleting = ref(false)

const visibleIds = computed(() => resource.items.value.map((s) => s.id))
const allChecked = computed(
  () => visibleIds.value.length > 0 && visibleIds.value.every((id) => checkedIds.value.has(id)),
)
const someChecked = computed(() => checkedIds.value.size > 0 && !allChecked.value)

const onToggleRow = (s: SysServer, checked: boolean) => {
  const next = new Set(checkedIds.value)
  if (checked) next.add(s.id)
  else next.delete(s.id)
  checkedIds.value = next
}

const onToggleAll = (checked: boolean | 'indeterminate') => {
  checkedIds.value = checked === true ? new Set(visibleIds.value) : new Set()
}

watch([serverName, host, statusFilter], () => {
  checkedIds.value = new Set()
})

const onBatchDelete = async () => {
  const ids = [...checkedIds.value]
  if (!ids.length) return
  const ok = await confirm({
    title: '批量删除服务器',
    description: `确认删除选中的 ${ids.length} 台服务器？配置删除后不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  batchDeleting.value = true
  try {
    await deleteServerBatchApi(ids)
    const idSet = new Set(ids)
    resource.removeWhere((s) => idSet.has(s.id))
    if (selected.value && idSet.has(selected.value.id)) {
      selected.value = null
      creating.value = false
    }
    toast.success(`已删除 ${ids.length} 台服务器`)
    checkedIds.value = new Set()
  } finally {
    batchDeleting.value = false
  }
}

const onCreated = () => {
  creating.value = false
  resource.search()
}

const onDeleted = (s: SysServer) => {
  resource.removeWhere((item) => item.id === s.id)
  selected.value = null
  creating.value = false
}

const onSaved = () => {
  resource.search()
}

/* ---------- 行内删除（快捷键 Del） ---------- */
const onDeleteRow = async (s: SysServer) => {
  const ok = await confirm({
    title: '删除服务器',
    description: `确认删除服务器「${s.serverName}」？配置删除后不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteServerApi(s.id)
  toast.success('服务器已删除')
  onDeleted(s)
}

/* ---------- 键盘流 ---------- */
const isTypingTarget = (el: EventTarget | null): boolean => {
  if (!(el instanceof HTMLElement)) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable
}

const onGlobalKeydown = (e: KeyboardEvent) => {
  /* 终端窗口打开期间，页面键盘流让位 */
  if (terminalServer.value) return

  if (isTypingTarget(e.target)) {
    if (e.key === 'Escape') (e.target as HTMLElement).blur()
    return
  }

  const list = resource.items.value
  switch (e.key) {
    case '/':
      e.preventDefault()
      nameRef.value?.focus()
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
    case 'n':
    case 'N':
      e.preventDefault()
      onCreate()
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

/* 列表刷新后：选中项同步最新数据；光标越界则收敛 */
watch(
  () => resource.items.value,
  (list) => {
    if (selected.value) {
      const fresh = list.find((s) => s.id === selected.value!.id)
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
