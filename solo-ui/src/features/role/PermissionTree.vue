<template>
  <div>
    <div class="flex items-center justify-between gap-2 pb-2">
      <label class="flex min-w-0 cursor-pointer items-center gap-1.5">
        <Checkbox
          :model-value="strictly"
          :disabled="disabled"
          @update:model-value="(v: boolean | 'indeterminate') => onToggleStrictly(v === true)"
        />
        <span class="whitespace-nowrap text-xs text-muted-foreground">父子节点独立</span>
      </label>
      <div class="flex items-center gap-1">
        <Button
          variant="ghost"
          size="sm"
          class="h-7 px-2 text-xs text-muted-foreground"
          @click="setExpandAll(true)"
        >
          <ChevronsUpDown class="h-3.5 w-3.5" />
          全部展开
        </Button>
        <Button
          variant="ghost"
          size="sm"
          class="h-7 px-2 text-xs text-muted-foreground"
          @click="setExpandAll(false)"
        >
          <ChevronsDownUp class="h-3.5 w-3.5" />
          全部折叠
        </Button>
      </div>
    </div>

    <div
      class="max-h-[calc(100vh_-_280px)] overflow-y-auto rounded-md border border-border py-1.5"
    >
      <PermissionTreeNode v-for="n in data" :key="n.id" :node="n" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { provide, reactive, ref } from 'vue'
import { ChevronsDownUp, ChevronsUpDown } from 'lucide-vue-next'
import type { SysPermission } from '@/types/api'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import PermissionTreeNode from './PermissionTreeNode.vue'
import { TREE_API } from './treeApi'
import type { CheckState } from './treeApi'

const props = withDefaults(
  defineProps<{
    data: SysPermission[]
    /** 无授权权限时只读 */
    disabled?: boolean
  }>(),
  { disabled: false },
)

/* 严格模式：checked 存全部勾选节点；非严格模式：leafChecked 仅存显式勾选的叶子 */
const strictly = ref(false)
const checked = reactive(new Set<string>())
const leafChecked = reactive(new Set<string>())
const leafIds = new Set<string>()
const expanded = reactive(new Set<string>())

/** 节点 -> 全部后代叶子（按需缓存） */
const descendantLeaves = new Map<string, string[]>()

/** 收集某节点全部后代叶子（含跨层） */
const getLeaves = (node: SysPermission): string[] => {
  const cached = descendantLeaves.get(node.id)
  if (cached) return cached
  const out: string[] = []
  const walk = (n: SysPermission) => {
    if (!n.children?.length) out.push(n.id)
    else n.children.forEach(walk)
  }
  walk(node)
  descendantLeaves.set(node.id, out)
  return out
}

/* 初始化：默认全部展开 */
;(function init() {
  const walk = (nodes: SysPermission[]) => {
    nodes.forEach((n) => {
      expanded.add(n.id)
      getLeaves(n)
      if (n.children?.length) walk(n.children)
      else leafIds.add(n.id)
    })
  }
  walk(props.data)
})()

const isLeaf = (node: SysPermission) => !node.children?.length

const getState = (node: SysPermission): CheckState => {
  if (strictly.value) {
    return checked.has(node.id) ? 'checked' : 'unchecked'
  }
  if (isLeaf(node)) {
    return leafChecked.has(node.id) ? 'checked' : 'unchecked'
  }
  const leaves = getLeaves(node)
  const hit = leaves.filter((id) => leafChecked.has(id)).length
  if (hit === 0) return 'unchecked'
  return hit === leaves.length ? 'checked' : 'indeterminate'
}

const toggle = (node: SysPermission) => {
  if (props.disabled) return
  const state = getState(node)
  if (strictly.value) {
    if (state === 'checked') checked.delete(node.id)
    else checked.add(node.id)
    return
  }
  if (isLeaf(node)) {
    if (state === 'checked') leafChecked.delete(node.id)
    else leafChecked.add(node.id)
    return
  }
  // 父节点：未全选（含半选）-> 全选；全选 -> 清空
  const leaves = getLeaves(node)
  const shouldCheck = state !== 'checked'
  leaves.forEach((id) => {
    if (shouldCheck) leafChecked.add(id)
    else leafChecked.delete(id)
  })
}

const isExpanded = (id: string) => expanded.has(id)
const toggleExpand = (id: string) => {
  if (expanded.has(id)) expanded.delete(id)
  else expanded.add(id)
}

const onToggleStrictly = (next: boolean) => {
  if (next) {
    // 非严格 -> 严格：把当前叶子勾选 + 全选父节点纳入
    checked.clear()
    leafChecked.forEach((id) => checked.add(id))
    props.data.forEach(function walk(n) {
      if (!isLeaf(n) && getState(n) === 'checked') checked.add(n.id)
      n.children?.forEach(walk)
    })
  } else {
    // 严格 -> 非严格：只保留勾选叶子
    leafChecked.clear()
    checked.forEach((id) => {
      if (leafIds.has(id)) leafChecked.add(id)
    })
  }
  strictly.value = next
}

/** 重置并回填已分配权限；与原系统一致，传入的父 id 会被叶子过滤 */
const setChecked = (ids: (string | number)[]) => {
  checked.clear()
  leafChecked.clear()
  const str = ids.map((v) => String(v))
  if (strictly.value) {
    str.forEach((id) => checked.add(id))
  } else {
    str.forEach((id) => {
      if (leafIds.has(id)) leafChecked.add(id)
    })
  }
}

/**
 * 获取保存用的权限 id：
 * 严格模式返回全部勾选；
 * 非严格模式 = 勾选叶子 + 全选父节点 + 半选父节点，即所有"非未选"节点
 * （对应原系统 getCheckedKeys + getHalfCheckedKeys。
 * 注意中间层全选父节点不能漏，否则该菜单本身的授权关系会丢失）
 */
const getChecked = (): string[] => {
  if (strictly.value) return [...checked]
  const result = new Set<string>()
  leafChecked.forEach((id) => result.add(id))
  props.data.forEach(function walk(n) {
    if (!isLeaf(n) && getState(n) !== 'unchecked') result.add(n.id)
    n.children?.forEach(walk)
  })
  return [...result]
}

const setExpandAll = (value: boolean) => {
  const walk = (nodes: SysPermission[]) => {
    nodes.forEach((n) => {
      if (!isLeaf(n)) {
        if (value) expanded.add(n.id)
        else expanded.delete(n.id)
        if (n.children) walk(n.children)
      }
    })
  }
  walk(props.data)
}

provide(TREE_API, {
  getState,
  toggle,
  isExpanded,
  toggleExpand,
  disabled: props.disabled,
})

defineExpose({ setChecked, getChecked })
</script>
