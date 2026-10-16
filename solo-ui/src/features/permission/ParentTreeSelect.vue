<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { IconSearch, IconChevronDown, IconCheck } from '@tabler/icons-vue'
import type { SysPermission } from '@/types/api'

const props = defineProps<{
  modelValue: string | number
  options: SysPermission[]
  excludeId?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

interface FlatItem {
  id: string
  name: string
  depth: number
}

const open = ref(false)
const keyword = ref('')

/** 扁平化菜单树；编辑时排除当前节点及其子孙，防止挂到自己下面成环 */
const flat = computed<FlatItem[]>(() => {
  const blocked = new Set<string>()
  if (props.excludeId) {
    const find = (list: SysPermission[]): SysPermission | undefined => {
      for (const n of list) {
        if (n.id === props.excludeId) return n
        if (n.children?.length) {
          const hit = find(n.children)
          if (hit) return hit
        }
      }
    }
    const collect = (n: SysPermission) => {
      blocked.add(n.id)
      n.children?.forEach(collect)
    }
    const target = find(props.options)
    if (target) collect(target)
  }

  const result: FlatItem[] = []
  const walk = (list: SysPermission[], depth: number) => {
    list.forEach((n) => {
      if (!blocked.has(n.id)) {
        result.push({ id: String(n.id), name: n.permissionName, depth })
        if (n.children?.length) walk(n.children, depth + 1)
      }
    })
  }
  walk(props.options, 0)
  return result
})

const items = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return flat.value
  return flat.value.filter((i) => i.name.toLowerCase().includes(kw) || i.id.includes(kw))
})

const labelOf = (id: string) => flat.value.find((i) => i.id === id)?.name

const select = (id: string) => {
  emit('update:modelValue', id)
  open.value = false
}

watch(open, (v) => {
  if (v) keyword.value = ''
})

const panelRef = ref<HTMLElement>()
onClickOutside(panelRef, () => (open.value = false))
</script>

<template>
  <div ref="panelRef" class="relative">
    <button
      type="button"
      class="flex h-8 w-full items-center gap-2 rounded-md border border-input bg-transparent px-2 text-xs hover:bg-muted/60"
      @click="open = !open"
    >
      <span class="min-w-0 flex-1 truncate text-left">
        {{ String(props.modelValue) === '0' ? '顶级权限' : (labelOf(String(props.modelValue)) || '请选择上级权限') }}
      </span>
      <IconChevronDown class="size-3.5 shrink-0 text-muted-foreground" />
    </button>

    <div
      v-if="open"
      class="absolute z-50 mt-1 flex w-full min-w-[240px] flex-col gap-1 rounded-md border border-border bg-popover p-1 shadow-md"
    >
      <div class="flex items-center gap-1.5 rounded-md bg-muted px-2">
        <IconSearch class="size-3.5 shrink-0 text-muted-foreground" />
        <input
          v-model="keyword"
          class="h-7 w-full bg-transparent text-xs outline-none placeholder:text-muted-foreground"
          placeholder="搜索菜单…"
        />
      </div>
      <div class="max-h-60 overflow-y-auto py-1">
        <button
          type="button"
          class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-xs hover:bg-accent"
          :class="String(props.modelValue) === '0' ? 'font-medium' : ''"
          @click="select('0')"
        >
          <span class="flex-1">顶级权限</span>
          <IconCheck v-if="String(props.modelValue) === '0'" class="size-3.5 text-primary" />
        </button>
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-xs hover:bg-accent"
          @click="select(item.id)"
        >
          <span class="flex-1 truncate" :style="{ paddingLeft: `${item.depth * 14}px` }">{{ item.name }}</span>
          <IconCheck v-if="String(props.modelValue) === item.id" class="size-3.5 shrink-0 text-primary" />
        </button>
      </div>
    </div>
  </div>
</template>
