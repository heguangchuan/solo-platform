<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  IconPlus,
  IconTrash,
  IconChevronRight,
  IconChevronsDown,
  IconChevronsUp,
  IconLoader2,
  IconFileOff,
} from '@tabler/icons-vue'
import {
  getPermissionTreeApi,
  editPermissionApi,
  deletePermissionApi,
  deletePermissionBatchApi,
} from '@/api/permission'
import type { SysPermission } from '@/types/api'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import DynamicIcon from '@/features/permission/DynamicIcon.vue'
import PermissionInspector from '@/features/permission/PermissionInspector.vue'
import RefreshButton from '@/components/RefreshButton.vue'

interface FlatRow {
  node: SysPermission
  depth: number
}

/* ---------- 树数据 ---------- */
const loading = ref(false)
const treeData = ref<SysPermission[]>([])
const menuTree = ref<SysPermission[]>([])

const expandedIds = ref<Set<string>>(new Set())
const allExpanded = ref(true)

const collectIds = (list: SysPermission[], acc: string[] = []): string[] => {
  list.forEach((n) => {
    acc.push(n.id)
    if (n.children?.length) collectIds(n.children, acc)
  })
  return acc
}

const findNode = (list: SysPermission[], id: string): SysPermission | undefined => {
  for (const n of list) {
    if (n.id === id) return n
    if (n.children?.length) {
      const hit = findNode(n.children, id)
      if (hit) return hit
    }
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const [treeRes, menuRes] = await Promise.all([
      getPermissionTreeApi(),
      getPermissionTreeApi(1),
    ])
    treeData.value = treeRes.data
    menuTree.value = menuRes.data
    if (allExpanded.value) expandedIds.value = new Set(collectIds(treeData.value))
    if (selected.value) {
      const fresh = findNode(treeData.value, selected.value.id)
      if (fresh) selected.value = fresh
    }
  } finally {
    loading.value = false
  }
}

const toggleExpandAll = () => {
  allExpanded.value = !allExpanded.value
  expandedIds.value = allExpanded.value
    ? new Set(collectIds(treeData.value))
    : new Set()
}

const onToggle = (row: SysPermission) => {
  const next = new Set(expandedIds.value)
  next.has(row.id) ? next.delete(row.id) : next.add(row.id)
  expandedIds.value = next
}

/* ---------- 扁平化 ---------- */
/** 按展开态扁平化，供光标键盘导航 */
const flatRows = computed<FlatRow[]>(() => {
  const acc: FlatRow[] = []
  const walk = (list: SysPermission[], depth: number) => {
    for (const n of list) {
      acc.push({ node: n, depth })
      if (n.children?.length && expandedIds.value.has(n.id)) {
        walk(n.children, depth + 1)
      }
    }
  }
  walk(treeData.value, 0)
  return acc
})

/* ---------- 光标与选择 ---------- */
const cursor = ref(0)
const selected = ref<SysPermission | null>(null)
const creating = ref(false)
const presetParentId = ref<string | undefined>()

const scrollerRef = ref<HTMLElement>()

const inspectorOpen = computed(() => !!selected.value || creating.value)

const scrollCursorIntoView = () => {
  scrollerRef.value
    ?.querySelector(`[data-row="${cursor.value}"]`)
    ?.scrollIntoView({ block: 'nearest' })
}

const onRowClick = (row: SysPermission, i: number) => {
  cursor.value = i
  selected.value = row
  // 选行必须退出新建态，否则 inspector 会停留在"新建"表单
  creating.value = false
}

const openCreate = (parent?: SysPermission) => {
  presetParentId.value = parent?.id
  selected.value = null
  creating.value = true
}

const onCloseInspector = () => {
  selected.value = null
  creating.value = false
}

/* ---------- 勾选与批量删除 ---------- */
const checkedIds = ref<Set<string>>(new Set())

const visibleIds = computed(() => flatRows.value.map((r) => r.node.id))
const allChecked = computed(
  () =>
    visibleIds.value.length > 0 &&
    visibleIds.value.every((id) => checkedIds.value.has(id)),
)
const someChecked = computed(() => checkedIds.value.size > 0 && !allChecked.value)

const onToggleRow = (row: SysPermission, checked: boolean) => {
  const next = new Set(checkedIds.value)
  if (checked) next.add(row.id)
  else next.delete(row.id)
  checkedIds.value = next
}

const onToggleAll = (checked: boolean | 'indeterminate') => {
  checkedIds.value = checked === true ? new Set(visibleIds.value) : new Set()
}

const onBatchDelete = async () => {
  const ids = [...checkedIds.value]
  if (!ids.length) return
  const ok = await confirm({
    title: '批量删除权限',
    description: `确定要删除选中的 ${ids.length} 项权限吗？子权限也将一并删除。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deletePermissionBatchApi(ids)
  const idSet = new Set(ids)
  if (selected.value && idSet.has(selected.value.id)) selected.value = null
  toast.success(`已删除 ${ids.length} 项权限`)
  checkedIds.value = new Set()
  await fetchData()
}

/* ---------- 启停 ---------- */
const onToggleStatus = async (row: SysPermission) => {
  const nextStatus = row.status === 1 ? 0 : 1
  await editPermissionApi(row.id, {
    permissionName: row.permissionName,
    permissionCode: row.permissionCode,
    url: row.url,
    type: row.type,
    parentId: row.parentId,
    sort: row.sort,
    icon: row.icon,
    status: nextStatus,
  })
  row.status = nextStatus
  toast.success(nextStatus === 1 ? '已启用' : '已禁用')
}

/* ---------- 单条删除 ---------- */
const onDelete = async (row: SysPermission) => {
  const ok = await confirm({
    title: '删除权限',
    description: `确定要删除权限 "${row.permissionName}" 吗？其子权限也将被一并删除。`,
    danger: true,
    confirmText: '删除',
  })
  if (!ok) return
  await deletePermissionApi(row.id)
  toast.success('删除成功')
  if (selected.value?.id === row.id) selected.value = null
  await fetchData()
}

/* ---------- inspector 事件 ---------- */
const onSaved = async () => {
  await fetchData()
}
const onCreated = async () => {
  creating.value = false
  await fetchData()
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

  const rows = flatRows.value
  switch (e.key) {
    case 'j':
    case 'J':
      e.preventDefault()
      if (!rows.length) break
      cursor.value = Math.min(rows.length - 1, cursor.value + 1)
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
      if (rows[cursor.value]) {
        onRowClick(rows[cursor.value].node, cursor.value)
      }
      break
    case 'n':
    case 'N':
      e.preventDefault()
      openCreate()
      break
    case 'Delete':
    case 'Backspace':
      e.preventDefault()
      if (rows[cursor.value]) onDelete(rows[cursor.value].node)
      break
    case 'r':
    case 'R':
      e.preventDefault()
      fetchData()
      break
    case 'Escape':
      onCloseInspector()
      break
  }
}

/* ---------- 生命周期 ---------- */
const route = useRoute()
const router = useRouter()

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown)
  await fetchData()

  const selectId = route.query.select
  if (typeof selectId === 'string') {
    const target = findNode(treeData.value, selectId)
    if (target) selected.value = target
    router.replace({ path: route.path, query: {} })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})

/* 扁平化结果变化：光标收敛 */
watch(flatRows, (rows) => {
  if (cursor.value > rows.length - 1) {
    cursor.value = Math.max(0, rows.length - 1)
  }
})
</script>

<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧：树列表 -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">权限管理</span>
        <span v-if="checkedIds.size" class="ml-2 text-xs text-muted-foreground">
          已选 {{ checkedIds.size }} 项
        </span>
        <div class="flex-1" />
        <RefreshButton :loading="loading" :on-refresh="fetchData" />
        <Button
          v-access="PERM.PERMISSION_DELETE"
          variant="ghost"
          size="sm"
          class="gap-1.5"
          :disabled="!checkedIds.size"
          @click="onBatchDelete"
        >
          <IconTrash class="size-3.5" />
          批量删除
        </Button>
        <Button variant="ghost" size="sm" class="gap-1.5" @click="toggleExpandAll">
          <IconChevronsUp v-if="allExpanded" class="size-3.5" />
          <IconChevronsDown v-else class="size-3.5" />
          {{ allExpanded ? '全部折叠' : '全部展开' }}
        </Button>
        <Button
          v-access="PERM.PERMISSION_ADD"
          variant="ghost"
          size="sm"
          class="ml-1 gap-1.5"
          @click="openCreate()"
        >
          <IconPlus class="size-3.5" />
          新增
        </Button>
      </div>

      <!-- 树表 -->
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
              <th class="w-56 px-3 py-2 font-normal">权限名称</th>
              <th class="w-[200px] px-3 py-2 font-normal">权限编码</th>
              <th class="w-[180px] px-3 py-2 font-normal">路由地址</th>
              <th class="w-20 px-3 py-2 font-normal">类型</th>
              <th class="w-20 px-3 py-2 font-normal">图标</th>
              <th class="w-20 px-3 py-2 font-normal">排序</th>
              <th class="w-20 px-3 py-2 font-normal">状态</th>
            </tr>
          </thead>
          <tbody v-if="!loading">
            <tr
              v-for="(row, i) in flatRows"
              :key="row.node.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                selected?.id === row.node.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(row.node, i)"
            >
              <td class="px-3 py-1.5" @click.stop>
                <Checkbox
                  :model-value="checkedIds.has(row.node.id)"
                  @update:model-value="(v) => onToggleRow(row.node, v === true)"
                  @click.stop
                />
              </td>
              <td class="px-3 py-1.5">
                <div
                  class="flex min-w-0 items-center gap-1.5"
                  :style="{ paddingLeft: `${row.depth * 18}px` }"
                >
                  <button
                    v-if="row.node.children?.length"
                    type="button"
                    class="flex size-4 shrink-0 items-center justify-center text-muted-foreground hover:text-foreground"
                    @click.stop="onToggle(row.node)"
                  >
                    <IconChevronRight
                      class="size-3.5 transition-transform"
                      :class="expandedIds.has(row.node.id) ? 'rotate-90' : ''"
                    />
                  </button>
                  <span v-else class="size-4 shrink-0" />
                  <span
                    class="flex h-4 w-4 shrink-0 items-center justify-center rounded-[3px] text-[9px] font-semibold"
                    :class="row.node.type === 1
                      ? 'bg-primary/10 text-primary'
                      : 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'"
                  >
                    {{ row.node.type === 1 ? 'M' : 'B' }}
                  </span>
                  <span class="min-w-0 truncate">{{ row.node.permissionName }}</span>
                </div>
              </td>
              <td class="w-[200px] px-3 py-1.5 font-mono text-xs text-muted-foreground">
                {{ row.node.permissionCode }}
              </td>
              <td class="w-[180px] truncate px-3 py-1.5 text-xs text-muted-foreground" :title="row.node.url">
                {{ row.node.url || '—' }}
              </td>
              <td class="w-20 px-3 py-1.5 text-xs text-muted-foreground">
                {{ row.node.type === 1 ? '菜单' : row.node.type === 2 ? '按钮' : '未知' }}
              </td>
              <td class="w-20 px-3 py-1.5 text-muted-foreground">
                <DynamicIcon v-if="row.node.icon" :name="row.node.icon" :size="17" />
                <span v-else class="text-xs text-muted-foreground/50">—</span>
              </td>
              <td class="w-20 px-3 py-1.5 text-xs">{{ row.node.sort }}</td>
              <td class="w-20 px-3 py-1.5" @click.stop>
                <button
                  type="button"
                  class="cursor-pointer rounded px-1.5 py-0.5 text-[11px]"
                  :class="row.node.status === 1
                    ? 'bg-emerald-500/10 text-emerald-600 hover:bg-emerald-500/20 dark:text-emerald-400'
                    : 'bg-destructive/10 text-destructive hover:bg-destructive/20'"
                  :title="row.node.status === 1 ? '点击禁用' : '点击启用'"
                  @click="onToggleStatus(row.node)"
                >
                  {{ row.node.status === 1 ? '启用' : '禁用' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="loading" class="grid place-items-center py-16">
          <IconLoader2 class="size-5 animate-spin text-muted-foreground" />
        </div>
        <div
          v-else-if="!flatRows.length"
          class="flex flex-col items-center gap-2 py-16 text-muted-foreground"
        >
          <IconFileOff class="size-8" />
          <div class="text-[13px]">暂无权限数据</div>
        </div>
      </div>

      <!-- 底部快捷键提示 -->
      <div
        class="flex h-8 shrink-0 items-center gap-3 border-t border-border px-3 text-[11px] text-muted-foreground/70"
      >
        <span><kbd>J</kbd><kbd>K</kbd> 移动</span>
        <span><kbd>Enter</kbd> 查看</span>
        <span><kbd>N</kbd> 新建</span>
        <span><kbd>Del</kbd> 删除</span>
        <span><kbd>R</kbd> 刷新</span>
        <span><kbd>Esc</kbd> 关闭</span>
        <div class="flex-1" />
        <span>共 {{ flatRows.length }} 项</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="inspectorOpen"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <PermissionInspector
            :permission="selected ?? undefined"
            :preset-parent-id="presetParentId"
            :menu-tree="menuTree"
            :creating="creating"
            @close="onCloseInspector"
            @saved="onSaved"
            @created="onCreated"
            @deleted="onDelete"
            @add-child="openCreate"
          />
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
