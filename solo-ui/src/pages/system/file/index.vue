<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧：单卡片（搜索 / 列表，无嵌套边框） -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">文件列表</span>

        <span v-if="checkedIds.size" class="ml-2 text-xs text-muted-foreground">
          已选 {{ checkedIds.size }} 项
        </span>

        <div class="flex-1" />

        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />

        <Button
          v-access="PERM.FILE_DELETE"
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
          v-access="PERM.FILE_UPLOAD"
          variant="ghost"
          size="sm"
          class="ml-1 gap-1.5"
          @click="onOpenUpload"
        >
          <IconUpload class="size-3.5" />
          上传
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
          v-model="fileName"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入文件名称"
        />
        <input
          ref="uploaderRef"
          v-model="uploaderName"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="请输入上传账号"
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
              <th class="w-64 px-3 py-2 font-normal">文件名称</th>
              <th class="w-24 px-3 py-2 font-normal">类型</th>
              <th class="w-28 px-3 py-2 font-normal">大小</th>
              <th class="w-40 px-3 py-2 font-normal">上传账号</th>
              <th class="w-44 px-3 py-2 font-normal">上传时间</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(f, i) in resource.items.value"
              :key="f.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                selected?.id === f.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(f, i)"
            >
              <td class="px-3 py-2">
                <Checkbox
                  :model-value="checkedIds.has(f.id)"
                  @update:model-value="(v) => onToggleRow(f, v === true)"
                  @click.stop
                />
              </td>
              <td class="relative px-3 py-2">
                <span
                  v-if="selected?.id === f.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <span class="flex min-w-0 items-center gap-1.5">
                  <span class="min-w-0 truncate font-medium" :title="f.originalName">{{ f.originalName }}</span>
                  <button
                    class="shrink-0 text-muted-foreground/60 opacity-0 transition-opacity hover:text-primary group-hover:opacity-100"
                    title="复制链接"
                    @click.stop="onCopyLink(f)"
                  >
                    <IconLink class="h-3.5 w-3.5" />
                  </button>
                </span>
              </td>
              <td class="px-3 py-2">
                <span
                  v-if="f.ext"
                  class="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground"
                >
                  {{ f.ext }}
                </span>
                <span v-else class="text-muted-foreground/50">—</span>
              </td>
              <td class="px-3 py-2 font-mono text-xs">{{ formatSize(f.size) }}</td>
              <td class="truncate px-3 py-2 font-mono text-xs text-muted-foreground" :title="f.uploaderName || ''">{{ f.uploaderName || '—' }}</td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">{{ f.createTime || '—' }}</td>
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
          <IconFiles class="h-8 w-8" />
          <div class="text-[13px]">{{ hasCondition ? '没有匹配的文件' : '还没有上传过文件' }}</div>
          <Button
            v-if="hasCondition"
            variant="ghost"
            size="sm"
            @click="onClear"
          >
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
        <span><kbd>N</kbd> 上传</span>
        <span><kbd>R</kbd> 刷新</span>
        <span><kbd>Del</kbd> 删除</span>
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 个文件</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <FileInspector
            :file="selected"
            @close="onCloseInspector"
            @deleted="onDeleted"
          />
        </div>
      </aside>
    </Transition>

    <!-- 上传弹窗 -->
    <UploadDialog v-model:open="uploadOpen" @uploaded="onUploaded" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconSearch,
  IconX,
  IconUpload,
  IconTrash,
  IconLink,
  IconFiles,
  IconLoader2,
} from '@tabler/icons-vue'
import FileInspector from '@/features/file/FileInspector.vue'
import UploadDialog from '@/features/file/UploadDialog.vue'
import {
  getFilePageApi,
  deleteFileApi,
  deleteFileBatchApi,
} from '@/api/file'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import type { SysFile, FilePageRequest } from '@/types/api'
import { formatSize } from '@/features/file/format'

/* ---------- 搜索条件（严格对应后端 FilePageRequest） ---------- */
const fileName = ref('')
const uploaderName = ref('')

const hasCondition = computed(() => !!fileName.value.trim() || !!uploaderName.value.trim())

const resource = useResource<FilePageRequest, SysFile>({
  fetcher: (q) =>
    getFilePageApi({
      ...q,
      originalName: fileName.value.trim() || undefined,
      uploaderName: uploaderName.value.trim() || undefined,
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
watch([fileName, uploaderName], scheduleSearch)

const onClear = () => {
  fileName.value = ''
  uploaderName.value = ''
  cursor.value = 0
  resource.search()
}

/* ---------- 光标与选择 ---------- */
const cursor = ref(0)
const selected = ref<SysFile | null>(null)

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
const nameRef = ref<HTMLInputElement>()
const uploaderRef = ref<HTMLInputElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  const el = scrollerRef.value?.querySelector(`[data-row="${cursor.value}"]`)
  el?.scrollIntoView({ block: 'nearest' })
}

const onRowClick = (f: SysFile, i: number) => {
  cursor.value = i
  onSelect(f)
}

const onSelect = (f: SysFile) => {
  selected.value = f
}

const onCloseInspector = () => {
  selected.value = null
}

/* ---------- 复制链接 ---------- */
const onCopyLink = async (f: SysFile) => {
  try {
    await navigator.clipboard.writeText(f.url)
    toast.success('链接已复制')
  } catch {
    toast.warning('复制失败，请手动复制')
  }
}

/* ---------- 上传 ---------- */
const uploadOpen = ref(false)
const onOpenUpload = () => {
  uploadOpen.value = true
}

const onUploaded = (files: SysFile[]) => {
  if (files.length) {
    toast.success(`已上传 ${files.length} 个文件`)
    resource.search()
  }
}

/* ---------- 勾选与批量删除 ---------- */
const checkedIds = ref<Set<string>>(new Set())
const batchDeleting = ref(false)

const visibleIds = computed(() => resource.items.value.map((f) => f.id))
const allChecked = computed(
  () => visibleIds.value.length > 0 && visibleIds.value.every((id) => checkedIds.value.has(id)),
)
const someChecked = computed(() => checkedIds.value.size > 0 && !allChecked.value)

const onToggleRow = (f: SysFile, checked: boolean) => {
  const next = new Set(checkedIds.value)
  if (checked) next.add(f.id)
  else next.delete(f.id)
  checkedIds.value = next
}

const onToggleAll = (checked: boolean | 'indeterminate') => {
  checkedIds.value = checked === true ? new Set(visibleIds.value) : new Set()
}

watch([fileName, uploaderName], () => {
  checkedIds.value = new Set()
})

const onBatchDelete = async () => {
  const ids = [...checkedIds.value]
  if (!ids.length) return
  const ok = await confirm({
    title: '批量删除文件',
    description: `确认删除选中的 ${ids.length} 个文件？对象存储中的文件将一并删除，且不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  batchDeleting.value = true
  try {
    await deleteFileBatchApi(ids)
    const idSet = new Set(ids)
    // removeWhere 每次只减 1，批量场景直接同步总数
    const removed = resource.items.value.filter((f) => idSet.has(f.id)).length
    resource.items.value = resource.items.value.filter((f) => !idSet.has(f.id))
    resource.total.value = Math.max(0, resource.total.value - removed)
    if (selected.value && idSet.has(selected.value.id)) selected.value = null
    toast.success(`已删除 ${ids.length} 个文件`)
    checkedIds.value = new Set()
  } finally {
    batchDeleting.value = false
  }
}

const onDeleted = (f: SysFile) => {
  resource.removeWhere((item) => item.id === f.id)
  selected.value = null
}

/* ---------- 行内删除（快捷键 Del） ---------- */
const onDeleteRow = async (f: SysFile) => {
  const ok = await confirm({
    title: '删除文件',
    description: `确认删除文件「${f.originalName}」？对象存储中的文件将一并删除，且不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteFileApi(f.id)
  toast.success('文件已删除')
  onDeleted(f)
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
      onOpenUpload()
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
      const fresh = list.find((f) => f.id === selected.value!.id)
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
