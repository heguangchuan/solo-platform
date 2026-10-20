<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconSearch,
  IconX,
  IconLoader2,
} from '@tabler/icons-vue'
import { Eraser, Plus, Send, Trash2 } from 'lucide-vue-next'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Input from '@/components/ui/input/Input.vue'
import GroupTreeNode from './GroupTreeNode.vue'
import {
  getParentPlatformShareGroupTreeApi,
  savePlatformGroupApi,
  updatePlatformGroupApi,
  deletePlatformGroupApi,
  getParentPlatformSharedByGroupPageApi,
  getParentPlatformUnmountedChannelsApi,
  addSharesApi,
  removeShareApi,
  clearSharesApi,
  clearAllSharesByPlatformApi,
  pushCatalogApi,
} from '@/api/gb28181'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import type {
  GbDeviceChannel,
  GbPlatformGroup,
  GbSharedChannel,
} from '@/types/api'

const props = defineProps<{
  platformId: string
  platformName: string
}>()

const emit = defineEmits<{ close: [] }>()

/* ---------------- 分组树 ---------------- */
const tree = ref<GbPlatformGroup[]>([])
const expanded = ref<Set<string>>(new Set())
const selectedGroupId = ref('')

const loadTree = async () => {
  tree.value = (await getParentPlatformShareGroupTreeApi(props.platformId)).data
}

const onToggle = (id: string) => {
  const next = new Set(expanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expanded.value = next
}

const onSelectGroup = (id: string) => {
  selectedGroupId.value = id
  keyword.value = ''
  shared.value = []
  sharedPage.value = 1
}

/* ---------------- 分组 CRUD（inline 表单） ---------------- */
const groupFormOpen = ref(false)
const groupIsEdit = ref(false)
const groupSaving = ref(false)
const groupForm = ref<{ id?: string; parentId: string; name: string; code: string }>({
  parentId: '0',
  name: '',
  code: '',
})
const groupErrors = ref<Record<string, string>>({})

const onCreateGroup = (parentId: string) => {
  groupIsEdit.value = false
  groupForm.value = { parentId, name: '', code: '' }
  groupErrors.value = {}
  groupFormOpen.value = true
}

const onEditGroup = (node: GbPlatformGroup) => {
  groupIsEdit.value = true
  groupForm.value = {
    id: node.id,
    parentId: node.parentId,
    name: node.name,
    code: node.code,
  }
  groupErrors.value = {}
  groupFormOpen.value = true
}

const validateGroup = () => {
  const e: Record<string, string> = {}
  if (!groupForm.value.name.trim()) e.name = '请输入分组名称'
  if (!/^\d{10}(215|216)\d{7}$/.test(groupForm.value.code))
    e.code = '必须为 20 位数字，且第 11-13 位为 215 或 216'
  groupErrors.value = e
  return Object.keys(e).length === 0
}

const onSubmitGroup = async () => {
  if (groupSaving.value || !validateGroup()) return
  groupSaving.value = true
  try {
    const { name, code, parentId } = groupForm.value
    if (groupIsEdit.value) {
      await updatePlatformGroupApi(props.platformId, groupForm.value.id!, {
        name,
        code,
        parentId,
      })
      toast.success('修改成功')
    } else {
      await savePlatformGroupApi(props.platformId, { name, code, parentId })
      toast.success('新建成功')
    }
    groupFormOpen.value = false
    await loadTree()
  } finally {
    groupSaving.value = false
  }
}

const onDeleteGroup = async (node: GbPlatformGroup) => {
  const ok = await confirm({
    title: `确定删除分组 “${node.name}” 吗？`,
    description: '不影响已挂载的通道记录。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deletePlatformGroupApi(props.platformId, node.id)
  toast.success('删除成功')
  if (selectedGroupId.value === node.id) selectedGroupId.value = ''
  await loadTree()
}

const onClearGroup = async (node: GbPlatformGroup) => {
  const ok = await confirm({
    title: `确定清空分组 “${node.name}” 的挂载吗？`,
    description: '该分组下所有通道将被卸载。',
    confirmText: '清空',
    danger: true,
  })
  if (!ok) return
  const res = await clearSharesApi(props.platformId, node.id)
  toast.success(`已清空 ${res.data.cleared} 条`)
  if (selectedGroupId.value === node.id) {
    shared.value = []
    sharedTotal.value = 0
  }
}

/* ---------------- 已挂载列表 ---------------- */
const shared = ref<GbSharedChannel[]>([])
const sharedTotal = ref(0)
const sharedPage = ref(1)
const loadingShared = ref(false)
const keyword = ref('')
const sharedScroller = ref<HTMLElement>()
const sharedSentinel = ref<HTMLElement>()
const selectedIds = ref<Set<string>>(new Set())

const displayName = (c: GbDeviceChannel) => c.name || c.reportName || c.channelId

const loadShared = async (reset = false) => {
  if (!selectedGroupId.value || loadingShared.value) return
  if (reset) {
    sharedPage.value = 1
    shared.value = []
    selectedIds.value = new Set()
  }
  loadingShared.value = true
  try {
    const res = await getParentPlatformSharedByGroupPageApi(
      props.platformId,
      selectedGroupId.value,
      { page: sharedPage.value, pageSize: 30, keyword: keyword.value.trim() || undefined },
    )
    shared.value = [...shared.value, ...res.data.rows]
    sharedTotal.value = res.data.total
    if (shared.value.length < res.data.total) sharedPage.value += 1
  } finally {
    loadingShared.value = false
  }
}

watch(selectedGroupId, () => loadShared(true))

let kwTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, () => {
  if (kwTimer) clearTimeout(kwTimer)
  kwTimer = setTimeout(() => loadShared(true), 250)
})

const allSelected = computed(
  () => shared.value.length > 0 && shared.value.every((c) => selectedIds.value.has(c.id)),
)

const toggleAll = (v: boolean) => {
  const next = new Set<string>()
  if (v) shared.value.forEach((c) => next.add(c.id))
  selectedIds.value = next
}

const toggleOne = (id: string, checked: boolean) => {
  const next = new Set(selectedIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedIds.value = next
}

const onUnmountSelected = async () => {
  if (selectedIds.value.size === 0) {
    toast.warning('请先勾选要卸载的通道')
    return
  }
  const ok = await confirm({
    title: `确定卸载选中的 ${selectedIds.value.size} 个通道吗？`,
    description: '不影响通道本身。',
    confirmText: '卸载',
    danger: true,
  })
  if (!ok) return
  await Promise.all(
    [...selectedIds.value].map((id) =>
      removeShareApi(props.platformId, selectedGroupId.value, id),
    ),
  )
  toast.success('卸载成功')
  shared.value = shared.value.filter((x) => !selectedIds.value.has(x.id))
  sharedTotal.value = Math.max(0, sharedTotal.value - selectedIds.value.size)
  selectedIds.value = new Set()
}

const onClearAll = async () => {
  const ok = await confirm({
    title: '确定清空该平台的全部挂载吗？',
    description: '所有分组下的通道都将被卸载。',
    confirmText: '全部清空',
    danger: true,
  })
  if (!ok) return
  const res = await clearAllSharesByPlatformApi(props.platformId)
  toast.success(`已清空 ${res.data.cleared} 条`)
  shared.value = []
  sharedTotal.value = 0
  selectedIds.value = new Set()
}

const onPushCatalog = async () => {
  await pushCatalogApi(props.platformId)
  toast.success('已触发推送')
}

/* ---------------- 通道选择（覆盖面板） ---------------- */
const pickerOpen = ref(false)
const pickerItems = ref<GbDeviceChannel[]>([])
const pickerPage = ref(1)
const loadingPicker = ref(false)
const pickerKeyword = ref('')
const pickedIds = ref<Set<string>>(new Set())
const adding = ref(false)
const pickerScroller = ref<HTMLElement>()
const pickerSentinel = ref<HTMLElement>()

const loadPicker = async (reset = false) => {
  if (!selectedGroupId.value || loadingPicker.value) return
  if (reset) {
    pickerPage.value = 1
    pickerItems.value = []
  }
  loadingPicker.value = true
  try {
    const res = await getParentPlatformUnmountedChannelsApi(
      props.platformId,
      selectedGroupId.value,
      { page: pickerPage.value, pageSize: 30, keyword: pickerKeyword.value.trim() || undefined },
    )
    pickerItems.value = [...pickerItems.value, ...res.data.rows]
    if (pickerItems.value.length < res.data.total) pickerPage.value += 1
  } finally {
    loadingPicker.value = false
  }
}

const openPicker = () => {
  pickedIds.value = new Set()
  pickerKeyword.value = ''
  pickerOpen.value = true
  loadPicker(true)
}

let pkTimer: ReturnType<typeof setTimeout> | null = null
watch(pickerKeyword, () => {
  if (pkTimer) clearTimeout(pkTimer)
  pkTimer = setTimeout(() => loadPicker(true), 250)
})

const pickerAllOnPage = () =>
  pickerItems.value.length > 0 &&
  pickerItems.value.every((c) => pickedIds.value.has(c.id))

const onPickerTogglePage = (v: boolean) => {
  const next = new Set(pickedIds.value)
  pickerItems.value.forEach((c) => {
    if (v) next.add(c.id)
    else next.delete(c.id)
  })
  pickedIds.value = next
}

const onPickOne = (id: string, checked: boolean) => {
  const next = new Set(pickedIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  pickedIds.value = next
}

const onConfirmPicker = async () => {
  if (pickedIds.value.size === 0) {
    toast.warning('请先勾选要挂载的通道')
    return
  }
  adding.value = true
  try {
    const res = await addSharesApi(props.platformId, selectedGroupId.value, {
      resourceIds: [...pickedIds.value],
    })
    toast.success(`挂载成功，新增 ${res.data.added} 条`)
    pickerOpen.value = false
    loadShared(true)
  } finally {
    adding.value = false
  }
}

/* ---------------- observers ---------------- */
let sharedObserver: IntersectionObserver | null = null
let pickerObserver: IntersectionObserver | null = null

const setupObservers = () => {
  sharedObserver = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) loadShared(false)
    },
    { root: sharedScroller.value, rootMargin: '60px' },
  )
  if (sharedSentinel.value) sharedObserver.observe(sharedSentinel.value)

  pickerObserver = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) loadPicker(false)
    },
    { root: pickerScroller.value, rootMargin: '60px' },
  )
  if (pickerSentinel.value) pickerObserver.observe(pickerSentinel.value)
}

onMounted(async () => {
  await loadTree()
  setupObservers()
})

onBeforeUnmount(() => {
  sharedObserver?.disconnect()
  pickerObserver?.disconnect()
  if (kwTimer) clearTimeout(kwTimer)
  if (pkTimer) clearTimeout(pkTimer)
})
</script>

<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">推送目录 · {{ platformName }}</div>
        <div class="text-xs text-muted-foreground">
          维护向上级平台推送的目录分组与通道
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="size-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 左右分栏 -->
    <div class="flex min-h-0 flex-1 gap-3">
      <!-- 左：分组树 -->
      <div class="flex w-80 shrink-0 flex-col overflow-hidden rounded-lg border border-border">
        <!-- inline 分组表单 -->
        <div v-if="groupFormOpen" class="space-y-2 border-b border-border p-2">
          <Input
            v-model="groupForm.name"
            maxlength="64"
            placeholder="分组名称"
            class="h-7 text-xs"
          />
          <Input
            v-model="groupForm.code"
            maxlength="20"
            placeholder="分组编码（20位）"
            class="h-7 font-mono text-xs"
          />
          <p v-if="groupErrors.name" class="text-[11px] text-destructive">
            {{ groupErrors.name }}
          </p>
          <p v-if="groupErrors.code" class="text-[11px] text-destructive">
            {{ groupErrors.code }}
          </p>
          <div class="flex justify-end gap-1">
            <Button variant="ghost" size="sm" class="h-7 text-xs" @click="groupFormOpen = false">
              取消
            </Button>
            <Button size="sm" class="h-7 text-xs" :disabled="groupSaving" @click="onSubmitGroup">
              确定
            </Button>
          </div>
        </div>

        <div class="flex h-9 shrink-0 items-center gap-1 border-b border-border px-2">
          <span class="text-xs font-medium">推送分组</span>
          <div class="flex-1" />
          <Button
            v-access="PERM.GB_SHARE_GROUP_ADD"
            variant="ghost"
            size="icon-sm"
            title="新建顶级分组"
            @click="onCreateGroup('0')"
          >
            <Plus class="size-3.5" />
          </Button>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto p-1.5">
          <GroupTreeNode
            v-for="node in tree"
            :key="node.id"
            :node="node"
            :selected-id="selectedGroupId"
            :expanded="expanded"
            @select="onSelectGroup"
            @toggle="onToggle"
            @create-child="onCreateGroup"
            @edit="onEditGroup"
            @delete="onDeleteGroup"
            @clear="onClearGroup"
          />
          <div v-if="!tree.length" class="grid place-items-center py-10 text-xs text-muted-foreground">
            暂无分组，点右上角新建
          </div>
        </div>
      </div>

      <!-- 右：通道列表 -->
      <div class="relative flex min-w-0 flex-1 flex-col overflow-hidden rounded-lg border border-border">
        <div class="flex h-9 shrink-0 items-center gap-2 border-b border-border px-2">
          <template v-if="selectedGroupId">
            <IconSearch class="size-3.5 text-muted-foreground" />
            <input
              v-model="keyword"
              class="h-6 w-48 rounded bg-transparent px-1.5 text-xs outline-none focus:bg-muted/70"
              placeholder="名称 / 编码"
            />
            <span class="text-[11px] text-muted-foreground">共 {{ sharedTotal }} 条</span>
          </template>
          <template v-else>
            <span class="text-xs text-muted-foreground">请先在左侧选择一个推送分组</span>
          </template>
          <div class="flex-1" />
          <Button
            v-access="PERM.GB_PARENT_PLATFORM_DELETE"
            variant="ghost"
            size="icon-sm"
            title="清空平台全部挂载"
            @click="onClearAll"
          >
            <Eraser class="size-3.5" />
          </Button>
          <Button
            v-access="PERM.GB_PARENT_PLATFORM_EDIT"
            variant="ghost"
            size="icon-sm"
            title="主动推送"
            @click="onPushCatalog"
          >
            <Send class="size-3.5" />
          </Button>
          <template v-if="selectedGroupId">
            <Button
              v-access="PERM.GB_PARENT_PLATFORM_EDIT"
              variant="ghost"
              size="icon-sm"
              title="删除"
              @click="onUnmountSelected"
            >
              <Trash2 class="size-3.5" />
            </Button>
            <Button
              v-access="PERM.GB_PARENT_PLATFORM_EDIT"
              variant="ghost"
              size="sm"
              class="h-7 text-xs"
              @click="openPicker"
            >
              <Plus class="size-3.5" />
              添加
            </Button>
          </template>
        </div>

        <div ref="sharedScroller" class="min-h-0 flex-1 overflow-y-auto">
          <table v-if="selectedGroupId" class="w-full table-fixed text-left text-xs">
            <thead class="sticky top-0 z-10">
              <tr class="bg-card text-muted-foreground">
                <th class="w-10 px-3 py-2 font-normal">
                  <Checkbox
                    :model-value="allSelected"
                    @update:model-value="toggleAll($event === true)"
                  />
                </th>
                <th class="w-40 px-3 py-2 font-normal">通道名称</th>
                <th class="w-44 px-3 py-2 font-normal">国标编码</th>
                <th class="w-44 px-3 py-2 font-normal">推送码</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in shared" :key="c.id" class="hover:bg-muted/30">
                <td class="px-3 py-2">
                  <Checkbox
                    :model-value="selectedIds.has(c.id)"
                    @update:model-value="toggleOne(c.id, $event === true)"
                  />
                </td>
                <td class="truncate px-3 py-2" :title="displayName(c)">{{ displayName(c) }}</td>
                <td class="px-3 py-2 font-mono text-muted-foreground">{{ c.channelId }}</td>
                <td class="px-3 py-2 font-mono text-muted-foreground">
                  {{ c.exposedCode || '—' }}
                </td>
              </tr>
            </tbody>
          </table>

          <div
            v-if="selectedGroupId && !loadingShared && shared.length === 0"
            class="grid place-items-center py-12 text-xs text-muted-foreground"
          >
            该分组还没有挂载通道
          </div>

          <div ref="sharedSentinel" class="grid place-items-center py-3">
            <IconLoader2
              v-if="loadingShared && shared.length"
              class="size-4 animate-spin text-muted-foreground"
            />
          </div>
        </div>

        <!-- 通道选择覆盖面板 -->
        <div
          v-if="pickerOpen"
          class="absolute inset-0 z-10 flex flex-col bg-card"
        >
          <div class="flex h-9 shrink-0 items-center gap-2 border-b border-border px-2">
            <IconSearch class="size-3.5 text-muted-foreground" />
            <input
              v-model="pickerKeyword"
              class="h-6 w-40 rounded bg-transparent px-1.5 text-xs outline-none focus:bg-muted/70"
              placeholder="名称 / 编码"
            />
            <div class="flex-1" />
            <Checkbox :model-value="pickerAllOnPage()" @update:model-value="onPickerTogglePage($event === true)" />
            <span class="text-[11px] text-muted-foreground">本页全选</span>
          </div>

          <div ref="pickerScroller" class="min-h-0 flex-1 overflow-y-auto p-2">
            <label
              v-for="c in pickerItems"
              :key="c.id"
              class="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-xs hover:bg-muted/50"
            >
              <Checkbox
                :model-value="pickedIds.has(c.id)"
                @update:model-value="onPickOne(c.id, $event === true)"
              />
              <span class="min-w-0 flex-1 truncate">{{ displayName(c) }}</span>
              <span class="font-mono text-muted-foreground">{{ c.channelId }}</span>
            </label>
            <div
              v-if="!loadingPicker && pickerItems.length === 0"
              class="grid place-items-center py-10 text-xs text-muted-foreground"
            >
              没有可挂载的通道
            </div>
            <div ref="pickerSentinel" class="grid place-items-center py-2">
              <IconLoader2
                v-if="loadingPicker && pickerItems.length"
                class="size-4 animate-spin text-muted-foreground"
              />
            </div>
          </div>

          <div class="flex h-9 shrink-0 items-center gap-2 border-t border-border px-2">
            <span class="mr-auto text-[11px] text-muted-foreground">
              已选 {{ pickedIds.size }} 条
            </span>
            <Button variant="outline" size="sm" class="h-7 text-xs" @click="pickerOpen = false">
              取消
            </Button>
            <Button size="sm" class="h-7 text-xs" :disabled="adding" @click="onConfirmPicker">
              <IconLoader2 v-if="adding" class="size-3.5 animate-spin" />
              挂载
            </Button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
