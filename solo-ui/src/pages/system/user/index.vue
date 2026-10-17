<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧：单卡片（概览 / 搜索 / 列表，无嵌套边框） -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 概览统计 tab：点击即按状态筛选 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
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
          {{ tab.label }}
          <span class="rounded-full bg-muted px-1.5 py-px text-[11px] text-muted-foreground">
            {{ counts[tab.key] }}
          </span>
          <span
            v-if="status === tab.status"
            class="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-primary"
          />
        </button>

        <span v-if="checkedIds.size" class="ml-2 text-xs text-muted-foreground">
          已选 {{ checkedIds.size }} 项
        </span>

        <div class="flex-1" />

        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
        <Button variant="ghost" size="icon" class="size-8" title="导入" @click="onImport">
          <Upload class="size-4" />
        </Button>
        <Button
          v-access="PERM.USER_EXPORT"
          variant="ghost"
          size="icon"
          class="size-8"
          title="导出"
          :disabled="exporting"
          @click="onExport"
        >
          <Loader2 v-if="exporting" class="size-4 animate-spin" />
          <Download v-else class="size-4" />
        </Button>
        <Button
          v-access="PERM.USER_DELETE"
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

        <Button v-access="PERM.USER_ADD" variant="ghost" size="sm" class="ml-1 gap-1.5" @click="onCreate">
          <Plus class="size-3.5" />
          新建
          <kbd class="rounded border border-border bg-muted px-1 text-[10px] text-muted-foreground"
            >N</kbd
          >
        </Button>
      </div>

      <!-- 搜索行 -->
      <div class="flex h-12 shrink-0 items-center gap-2 px-3">
        <Search class="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        <input
          ref="usernameRef"
          v-model="username"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入账号"
        />
        <input
          ref="nicknameRef"
          v-model="nickname"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入昵称"
        />

        <span v-if="hasCondition" class="text-xs text-muted-foreground">
          筛选出 {{ resource.total.value }} 位
        </span>

        <div class="flex-1" />

        <Button
          v-if="hasCondition"
          variant="ghost"
          size="sm"
          class="h-7 text-xs text-muted-foreground"
          @click="onClear"
        >
          <X class="h-3.5 w-3.5" />
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
              <th class="w-[180px] px-3 py-2 font-normal">账号</th>
              <th class="px-3 py-2 font-normal">昵称</th>
              <th class="w-52 px-3 py-2 font-normal">邮箱</th>
              <th class="w-32 px-3 py-2 font-normal">电话</th>
              <th class="w-20 px-3 py-2 font-normal">状态</th>
              <th class="w-40 px-3 py-2 font-normal">创建时间</th>
              <th class="w-40 px-3 py-2 font-normal">更新时间</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(u, i) in resource.items.value"
              :key="u.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                !creating && selected?.id === u.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(u, i)"
            >
              <td class="px-3 py-2">
                <Checkbox
                  :model-value="checkedIds.has(u.id)"
                  @update:model-value="(v) => onToggleRow(u, v === true)"
                  @click.stop
                />
              </td>
              <td class="relative px-3 py-2">
                <!-- 选中左侧竖线（置于 td 内，避免破坏表格列布局） -->
                <span
                  v-if="!creating && selected?.id === u.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <div class="flex items-center gap-2">
                  <Avatar :size="24">
                    <AvatarImage v-if="u.avatar" :src="u.avatar" />
                    <AvatarFallback>{{ (u.nickname || 'U').charAt(0).toUpperCase() }}</AvatarFallback>
                  </Avatar>
                  <span class="mono text-xs">{{ u.username }}</span>
                </div>
              </td>

              <td class="truncate px-3 py-2 font-medium" :title="u.nickname">{{ u.nickname }}</td>

              <td class="mono truncate px-3 py-2 text-xs text-muted-foreground" :title="u.email || ''">
                {{ u.email || '—' }}
              </td>

              <td class="mono truncate px-3 py-2 text-xs text-muted-foreground" :title="u.phone || ''">
                {{ u.phone || '—' }}
              </td>

              <!-- 状态：文字颜色区分，点击即切换 -->
              <td class="px-3 py-2">
                <button
                  class="flex items-center gap-1.5 text-xs"
                  :disabled="!can(USER_SWITCH)"
                  :title="can(USER_SWITCH) ? '点击切换状态' : ''"
                  @click.stop="onToggleStatus(u)"
                >
                  <span :class="u.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'">
                    {{ u.status === 1 ? '正常' : '停用' }}
                  </span>
                </button>
              </td>

              <td class="mono px-3 py-2 text-xs text-muted-foreground">
                {{ u.createTime || '—' }}
              </td>
              <td class="mono px-3 py-2 text-xs text-muted-foreground">
                {{ u.updateTime || '—' }}
              </td>
            </tr>
          </tbody>
        </table>

        <!-- 首次加载 -->
        <div v-if="firstLoading" class="grid place-items-center py-16">
          <svg class="h-5 w-5 animate-spin text-muted-foreground" viewBox="0 0 24 24" fill="none">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-90" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
          </svg>
        </div>

        <!-- 空状态 -->
        <div
          v-else-if="!resource.loading.value && resource.items.value.length === 0"
          class="flex flex-col items-center gap-2 py-16 text-muted-foreground"
        >
          <UserX class="h-8 w-8" />
          <div class="text-[13px]">没有匹配的用户</div>
          <Button v-if="hasCondition" variant="ghost" size="sm" @click="onClear">
            清除搜索条件
          </Button>
        </div>

        <!-- 哨兵 -->
        <div ref="sentinelRef" class="grid place-items-center py-4">
          <svg
            v-if="resource.loading.value && resource.items.value.length"
            class="h-4 w-4 animate-spin text-muted-foreground"
            viewBox="0 0 24 24"
            fill="none"
          >
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-90" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
          </svg>
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
        <span>共 {{ counts.all }} 位用户</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="creating || selected"
        class="flex w-[380px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <UserInspector
            :user="selected ?? undefined"
            :creating="creating"
            @close="onCloseInspector"
            @saved="onSaved"
            @created="onCreated"
            @deleted="onDeleted"
          />
        </div>
      </aside>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Download, Loader2, Plus, Search, Trash2, Upload, UserX, X } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import UserInspector from '@/features/user/UserInspector.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import {
  getUserPageApi,
  getUserStatusCountApi,
  deleteUserApi,
  switchUserStatusApi,
  deleteUserBatchApi,
  exportUserApi,
} from '@/api/user'
import { useResource } from '@/shared/composables/useResource'
import { useAccess } from '@/features/access/useAccess'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Avatar from '@/components/ui/avatar/Avatar.vue'
import AvatarImage from '@/components/ui/avatar/AvatarImage.vue'
import AvatarFallback from '@/components/ui/avatar/AvatarFallback.vue'
import type { SysUser, UserPageRequest } from '@/types/api'

const { can } = useAccess()
const USER_SWITCH = PERM.USER_SWITCH

/* ---------- 搜索条件（严格对应后端 UserPageRequest） ---------- */
const username = ref('')
const nickname = ref('')
const status = ref<number | null>(null)

const hasCondition = computed(
  () => !!username.value.trim() || !!nickname.value.trim() || status.value !== null
)

const resource = useResource<UserPageRequest, SysUser>({
  fetcher: (q) =>
    getUserPageApi({
      ...q,
      username: username.value.trim() || undefined,
      nickname: nickname.value.trim() || undefined,
      status: status.value === null ? undefined : status.value,
    }),
  initial: { page: 1, pageSize: 50 },
})

/* 输入即时筛选（防抖） */
let debounceTimer: ReturnType<typeof setTimeout> | null = null
const scheduleSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cursor.value = 0
    resource.search()
  }, 300)
}
watch([username, nickname], scheduleSearch)

const onClear = () => {
  username.value = ''
  nickname.value = ''
  status.value = null
  cursor.value = 0
  resource.search()
}

/* ---------- 概览统计 ---------- */
const counts = ref<{ all: number; active: number; disabled: number }>({
  all: 0,
  active: 0,
  disabled: 0,
})

const tabs = computed(() => [
  { key: 'all' as const, label: '全部', status: null },
  { key: 'active' as const, label: '正常', status: 1 },
  { key: 'disabled' as const, label: '停用', status: 0 },
])

/*
 * 统计为全局口径，与查询条件无关：
 * 独立接口一条 GROUP BY SQL 返回，仅在挂载与增删改后调用，
 * 保证每次输入只发 1 次列表请求。
 */
const fetchStatusCount = async () => {
  const rows = (await getUserStatusCountApi()).data
  const active = rows.find((r) => r.status === 1)?.count ?? 0
  const disabled = rows.find((r) => r.status === 0)?.count ?? 0
  counts.value = { all: active + disabled, active, disabled }
}

/** 手动刷新：重拉列表（保留当前筛选）+ 状态统计 */
const onRefresh = () =>
  Promise.all([resource.load(true), fetchStatusCount()])

const onTab = (s: number | null) => {
  status.value = s
  cursor.value = 0
  resource.search()
}

/* ---------- 光标与选择 ---------- */
const cursor = ref(0)
const selected = ref<SysUser | null>(null)
const creating = ref(false)

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const usernameRef = ref<HTMLInputElement>()
const nicknameRef = ref<HTMLInputElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0
)

/** 让光标行滚动到可视区 */
const scrollCursorIntoView = () => {
  const el = scrollerRef.value?.querySelector(`[data-row="${cursor.value}"]`)
  el?.scrollIntoView({ block: 'nearest' })
}

const onRowClick = (u: SysUser, i: number) => {
  cursor.value = i
  onSelect(u)
}

const onSelect = (u: SysUser) => {
  creating.value = false
  selected.value = u
}

const onCloseInspector = () => {
  creating.value = false
  selected.value = null
}

const onCreate = () => {
  selected.value = null
  creating.value = true
}

/* 导入 / 导出占位 */
const onImport = () => {
  toast.info('导入功能开发中')
}

const exporting = ref(false)
const onExport = async () => {
  // 勾选了行则只导出选中，否则按当前账号/昵称/状态筛选条件导出
  const ids = [...checkedIds.value]
  exporting.value = true
  try {
    const res = await exportUserApi({
      username: username.value.trim() || undefined,
      nickname: nickname.value.trim() || undefined,
      status: status.value === null ? undefined : status.value,
      ids: ids.length ? ids : undefined,
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = '用户列表.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    toast.success('导出成功')
  } finally {
    exporting.value = false
  }
}

/* ---------- 勾选与批量删除 ---------- */
const checkedIds = ref<Set<string>>(new Set())
const batchDeleting = ref(false)

const visibleIds = computed(() => resource.items.value.map((u) => u.id))
const checkedCount = computed(() => checkedIds.value.size)
const allChecked = computed(
  () => visibleIds.value.length > 0 && visibleIds.value.every((id) => checkedIds.value.has(id))
)
const someChecked = computed(
  () => checkedCount.value > 0 && !allChecked.value
)

const onToggleRow = (u: SysUser, checked: boolean) => {
  const next = new Set(checkedIds.value)
  if (checked) next.add(u.id)
  else next.delete(u.id)
  checkedIds.value = next
}

const onToggleAll = (checked: boolean | 'indeterminate') => {
  checkedIds.value = checked === true ? new Set(visibleIds.value) : new Set()
}

const clearChecked = () => {
  checkedIds.value = new Set()
}

const onBatchDelete = async () => {
  const ids = [...checkedIds.value]
  if (!ids.length) return
  const ok = await confirm({
    title: '批量删除用户',
    description: `确认删除选中的 ${ids.length} 位用户？删除后这些账号将无法登录。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  batchDeleting.value = true
  try {
    await deleteUserBatchApi(ids)
    const idSet = new Set(ids)
    const removed = resource.items.value.filter((u) => idSet.has(u.id))
    resource.removeWhere((u) => idSet.has(u.id))
    removed.forEach((u) => {
      counts.value.all = Math.max(0, counts.value.all - 1)
      if (u.status === 1) counts.value.active = Math.max(0, counts.value.active - 1)
      else counts.value.disabled = Math.max(0, counts.value.disabled - 1)
    })
    if (selected.value && idSet.has(selected.value.id)) {
      selected.value = null
      creating.value = false
    }
    toast.success(`已删除 ${ids.length} 位用户`)
    clearChecked()
  } finally {
    batchDeleting.value = false
  }
}

/* 切换搜索 / 状态 tab 时清空勾选，避免跨结果集误操作 */
watch([username, nickname, status], clearChecked)

const onCreated = () => {
  creating.value = false
  // 新建用户默认启用状态
  counts.value.all += 1
  counts.value.active += 1
  resource.search()
}

const onDeleted = (u: SysUser) => {
  resource.removeWhere((item) => item.id === u.id)
  counts.value = {
    ...counts.value,
    all: Math.max(0, counts.value.all - 1),
    active: u.status === 1 ? Math.max(0, counts.value.active - 1) : counts.value.active,
    disabled: u.status === 0 ? Math.max(0, counts.value.disabled - 1) : counts.value.disabled,
  }
  selected.value = null
  creating.value = false
}

const onSaved = () => {
  resource.search()
  // 检视器中可能改了状态，重新取状态计数（罕见操作）
  fetchStatusCount()
}

/* ---------------- 状态切换 ---------------- */
/* 点击状态点切换 */
const onToggleStatus = async (u: SysUser) => {
  if (!can(USER_SWITCH)) return
  const next = u.status === 1 ? 0 : 1
  await switchUserStatusApi(u.id, next)
  u.status = next
  if (selected.value?.id === u.id) selected.value = { ...u }
  toast.success(next === 1 ? '已启用' : '已停用')
  // 本地同步计数（启用↔停用，总数不变）
  if (next === 1) {
    counts.value.active += 1
    counts.value.disabled = Math.max(0, counts.value.disabled - 1)
  } else {
    counts.value.disabled += 1
    counts.value.active = Math.max(0, counts.value.active - 1)
  }
  // 筛选中的列表：切换后当前行不再匹配，重载以保持结果一致
  if (status.value !== null && status.value !== next) {
    resource.search()
  }
}

/* ---------- 行内删除 ---------- */
const onDeleteRow = async (u: SysUser) => {
  const ok = await confirm({
    title: '删除用户',
    description: `删除后该账号无法登录，确认删除「${u.nickname}」？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteUserApi(u.id)
  toast.success('用户已删除')
  onDeleted(u)
}

/* ---------- 键盘流 ---------- */
const isTypingTarget = (el: EventTarget | null): boolean => {
  if (!(el instanceof HTMLElement)) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable
}

const onGlobalKeydown = (e: KeyboardEvent) => {
  // 输入框聚焦时：/ 不再触发搜索；Esc 退出焦点
  if (isTypingTarget(e.target)) {
    if (e.key === 'Escape') (e.target as HTMLElement).blur()
    return
  }

  const list = resource.items.value
  switch (e.key) {
    case '/':
      e.preventDefault()
      usernameRef.value?.focus()
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
    case 'r':
    case 'R':
      e.preventDefault()
      onRefresh()
      break
    case 'Delete':
    case 'Backspace':
      e.preventDefault()
      if (can(PERM.USER_DELETE) && list[cursor.value]) onDeleteRow(list[cursor.value])
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

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown)
  await Promise.all([resource.load(true), fetchStatusCount()])

  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) resource.load(false)
    },
    { root: scrollerRef.value, rootMargin: '80px' }
  )
  if (sentinelRef.value) observer.observe(sentinelRef.value)

  // ⌘K 跳转：?select=id
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

/* 从已加载列表选中；找不到则用大页兜底查一次（后端无单用户详情接口） */
const trySelect = async (id: string) => {
  const hit = resource.items.value.find((u) => u.id === id)
  if (hit) {
    const idx = resource.items.value.indexOf(hit)
    cursor.value = idx
    onSelect(hit)
    return
  }
  const res = await getUserPageApi({ page: 1, pageSize: 999 })
  const target = res.data.rows.find((u) => u.id === id)
  if (target) onSelect(target)
}

/* 列表刷新后：选中项同步最新数据；光标越界则收敛 */
watch(
  () => resource.items.value,
  (list) => {
    if (selected.value) {
      const fresh = list.find((u) => u.id === selected.value!.id)
      if (fresh) selected.value = fresh
    }
    if (cursor.value > list.length - 1) cursor.value = Math.max(0, list.length - 1)
  }
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

/* 快捷键提示统一外观 */
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
