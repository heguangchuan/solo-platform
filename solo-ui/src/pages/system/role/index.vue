<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧：单卡片（搜索 / 列表，无嵌套边框） -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">角色列表</span>

        <span v-if="checkedIds.size" class="ml-2 text-xs text-muted-foreground">
          已选 {{ checkedIds.size }} 项
        </span>

        <div class="flex-1" />

        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
        <Button variant="ghost" size="icon" class="size-8" title="导入" @click="onImport">
          <Upload class="size-4" />
        </Button>
        <Button
          v-access="PERM.ROLE_EXPORT"
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
          v-access="PERM.ROLE_DELETE"
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
          v-access="PERM.ROLE_ADD"
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
        <Search class="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        <input
          ref="roleCodeRef"
          v-model="roleCode"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入角色编码"
        />
        <input
          ref="roleNameRef"
          v-model="roleName"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入角色名称"
        />

        <span v-if="hasCondition" class="text-xs text-muted-foreground">
          筛选出 {{ resource.total.value }} 个
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
              <th class="w-[180px] px-3 py-2 font-normal">角色编码</th>
              <th class="w-[180px] px-3 py-2 font-normal">角色名称</th>
              <th class="px-3 py-2 font-normal">角色描述</th>
              <th class="w-24 px-3 py-2 font-normal">状态</th>
              <th class="w-44 px-3 py-2 font-normal">创建时间</th>
              <th class="w-44 px-3 py-2 font-normal">更新时间</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(r, i) in resource.items.value"
              :key="r.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                !creating && selected?.id === r.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(r, i)"
            >
              <td class="px-3 py-2">
                <Checkbox
                  :model-value="checkedIds.has(r.id)"
                  @update:model-value="(v) => onToggleRow(r, v === true)"
                  @click.stop
                />
              </td>
              <td class="relative px-3 py-2">
                <span
                  v-if="!creating && selected?.id === r.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <span class="mono text-xs">{{ r.roleCode }}</span>
              </td>
              <td class="px-3 py-2 font-medium">{{ r.roleName }}</td>
              <td class="truncate px-3 py-2 text-xs text-muted-foreground" :title="r.description">
                {{ r.description || '—' }}
              </td>
              <td class="px-3 py-2">
                <span
                  class="text-xs"
                  :class="r.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'"
                >
                  {{ r.status === 1 ? '启用' : '禁用' }}
                </span>
              </td>
              <td class="mono px-3 py-2 text-xs text-muted-foreground">
                {{ r.createTime || '—' }}
              </td>
              <td class="mono px-3 py-2 text-xs text-muted-foreground">
                {{ r.updateTime || '—' }}
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
          <ShieldX class="h-8 w-8" />
          <div class="text-[13px]">没有匹配的角色</div>
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
        <span>共 {{ resource.total.value }} 个角色</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="creating || selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <RoleInspector
            :role="selected ?? undefined"
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
import {
  Download,
  Loader2,
  Plus,
  Search,
  ShieldX,
  Trash2,
  Upload,
  X,
} from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import RoleInspector from '@/features/role/RoleInspector.vue'
import {
  getRolePageApi,
  deleteRoleApi,
  deleteRoleBatchApi,
  exportRoleApi,
} from '@/api/role'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import type { SysRole, RolePageRequest } from '@/types/api'

/* ---------- 搜索条件（严格对应后端 RolePageRequest） ---------- */
const roleCode = ref('')
const roleName = ref('')

const hasCondition = computed(() => !!roleCode.value.trim() || !!roleName.value.trim())

const resource = useResource<RolePageRequest, SysRole>({
  fetcher: (q) =>
    getRolePageApi({
      ...q,
      roleCode: roleCode.value.trim() || undefined,
      roleName: roleName.value.trim() || undefined,
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
watch([roleCode, roleName], scheduleSearch)

const onClear = () => {
  roleCode.value = ''
  roleName.value = ''
  cursor.value = 0
  resource.search()
}

/* ---------- 光标与选择 ---------- */
const cursor = ref(0)
const selected = ref<SysRole | null>(null)
const creating = ref(false)

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const roleCodeRef = ref<HTMLInputElement>()
const roleNameRef = ref<HTMLInputElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  const el = scrollerRef.value?.querySelector(`[data-row="${cursor.value}"]`)
  el?.scrollIntoView({ block: 'nearest' })
}

const onRowClick = (r: SysRole, i: number) => {
  cursor.value = i
  onSelect(r)
}

const onSelect = (r: SysRole) => {
  creating.value = false
  selected.value = r
}

const onCloseInspector = () => {
  creating.value = false
  selected.value = null
}

const onCreate = () => {
  selected.value = null
  creating.value = true
}

/* 导入占位 */
const onImport = () => {
  toast.info('导入功能开发中')
}

/* ---------- 导出 ---------- */
const exporting = ref(false)
const onExport = async () => {
  const ids = [...checkedIds.value]
  exporting.value = true
  try {
    const res = await exportRoleApi({
      roleCode: roleCode.value.trim() || undefined,
      roleName: roleName.value.trim() || undefined,
      ids: ids.length ? ids : undefined,
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = '角色列表.xlsx'
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

const visibleIds = computed(() => resource.items.value.map((r) => r.id))
const allChecked = computed(
  () => visibleIds.value.length > 0 && visibleIds.value.every((id) => checkedIds.value.has(id)),
)
const someChecked = computed(() => checkedIds.value.size > 0 && !allChecked.value)

const onToggleRow = (r: SysRole, checked: boolean) => {
  const next = new Set(checkedIds.value)
  if (checked) next.add(r.id)
  else next.delete(r.id)
  checkedIds.value = next
}

const onToggleAll = (checked: boolean | 'indeterminate') => {
  checkedIds.value = checked === true ? new Set(visibleIds.value) : new Set()
}

watch([roleCode, roleName], () => {
  checkedIds.value = new Set()
})

const onBatchDelete = async () => {
  const ids = [...checkedIds.value]
  if (!ids.length) return
  const ok = await confirm({
    title: '批量删除角色',
    description: `确认删除选中的 ${ids.length} 个角色？已分配这些角色的用户将失去对应权限。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  batchDeleting.value = true
  try {
    await deleteRoleBatchApi(ids)
    const idSet = new Set(ids)
    resource.removeWhere((r) => idSet.has(r.id))
    if (selected.value && idSet.has(selected.value.id)) {
      selected.value = null
      creating.value = false
    }
    toast.success(`已删除 ${ids.length} 个角色`)
    checkedIds.value = new Set()
  } finally {
    batchDeleting.value = false
  }
}

const onCreated = () => {
  creating.value = false
  resource.search()
}

const onDeleted = (r: SysRole) => {
  resource.removeWhere((item) => item.id === r.id)
  selected.value = null
  creating.value = false
}

const onSaved = () => {
  resource.search()
}

/* ---------- 行内删除（快捷键 Del） ---------- */
const onDeleteRow = async (r: SysRole) => {
  const ok = await confirm({
    title: '删除角色',
    description: `确认删除角色「${r.roleName}」？已分配该角色的用户将失去对应权限。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteRoleApi(r.id)
  toast.success('角色已删除')
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
      roleCodeRef.value?.focus()
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
const route = useRoute()
const router = useRouter()
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

/* 从已加载列表选中；找不到则用大页兜底查一次 */
const trySelect = async (id: string) => {
  const hit = resource.items.value.find((r) => r.id === id)
  if (hit) {
    cursor.value = resource.items.value.indexOf(hit)
    onSelect(hit)
    return
  }
  const res = await getRolePageApi({ page: 1, pageSize: 999 })
  const target = res.data.rows.find((r) => r.id === id)
  if (target) onSelect(target)
}

/* 列表刷新后：选中项同步最新数据；光标越界则收敛 */
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
