<template>
  <div class="min-w-0">
    <div
      class="flex min-w-0 items-center gap-1.5 pr-2"
      :style="{ paddingLeft: `${depth * 16 + 6}px` }"
    >
      <!-- 展开箭头（叶子留占位对齐） -->
      <button
        class="flex h-4 w-4 shrink-0 items-center justify-center text-muted-foreground"
        @click="api.toggleExpand(node.id)"
      >
        <ChevronRight
          v-if="node.children?.length"
          class="h-3.5 w-3.5 transition-transform"
          :class="api.isExpanded(node.id) ? 'rotate-90' : ''"
        />
      </button>

      <!-- 三态复选框 -->
      <button
        class="flex h-4 w-4 shrink-0 items-center justify-center rounded-[4px] border shadow-xs transition-colors"
        :class="stateBoxClass"
        :disabled="api.disabled"
        @click="api.toggle(node)"
      >
        <Check v-if="state === 'checked'" class="h-3 w-3" />
        <Minus v-else-if="state === 'indeterminate'" class="h-3 w-3" />
      </button>

      <span
        class="min-w-0 flex-1 select-none truncate text-xs"
        :class="[
          node.type === 2 ? 'text-muted-foreground' : 'text-foreground',
          api.disabled ? '' : 'cursor-pointer',
        ]"
        @click="api.toggle(node)"
      >
        {{ node.permissionName }}
      </span>
      <span class="mono max-w-[140px] shrink-0 truncate text-[10px] text-muted-foreground/60">
        {{ node.permissionCode }}
      </span>
    </div>

    <template v-if="node.children?.length && api.isExpanded(node.id)">
      <PermissionTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :depth="depth + 1"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check, ChevronRight, Minus } from 'lucide-vue-next'
import type { SysPermission } from '@/types/api'
import { TREE_API } from './treeApi'
import type { CheckState } from './treeApi'

const props = withDefaults(defineProps<{ node: SysPermission; depth?: number }>(), { depth: 0 })

const api = inject(TREE_API)!
const state = computed<CheckState>(() => api.getState(props.node))

const stateBoxClass = computed(() => {
  const base =
    state.value === 'checked'
      ? 'border-primary bg-primary text-primary-foreground'
      : state.value === 'indeterminate'
        ? 'border-primary bg-primary text-primary-foreground'
        : 'border-input bg-background text-transparent hover:border-muted-foreground/50'
  return api.disabled ? `${base} cursor-not-allowed opacity-60` : base
})
</script>
