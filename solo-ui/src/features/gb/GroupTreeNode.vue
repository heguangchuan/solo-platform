<script setup lang="ts">
import {
  ChevronRight as IconChevron,
  Eraser as IconEraser,
  Folder as IconFolder,
  Pencil as IconPencil,
  Plus,
  Trash2,
} from 'lucide-vue-next'
import type { GbPlatformGroup } from '@/types/api'

defineOptions({ name: 'GroupTreeNode' })

const props = defineProps<{
  node: GbPlatformGroup
  selectedId: string
  expanded: Set<string>
}>()

const emit = defineEmits<{
  select: [id: string]
  toggle: [id: string]
  createChild: [parentId: string]
  edit: [node: GbPlatformGroup]
  delete: [node: GbPlatformGroup]
  clear: [node: GbPlatformGroup]
}>()

const hasChildren = () => !!props.node.children?.length
const isOpen = () => props.expanded.has(props.node.id)
const isSelected = () => props.selectedId === props.node.id
</script>

<template>
  <div>
    <div
      class="group flex h-7 cursor-pointer items-center gap-1 rounded px-1.5 text-xs"
      :class="isSelected() ? 'bg-primary/10 text-foreground' : 'hover:bg-muted/50'"
      @click="emit('select', node.id)"
    >
      <button
        type="button"
        class="grid size-4 shrink-0 place-items-center text-muted-foreground outline-none"
        @click.stop="hasChildren() && emit('toggle', node.id)"
      >
        <IconChevron
          class="size-3 transition-transform"
          :class="isOpen() ? 'rotate-90' : ''"
        />
      </button>
      <IconFolder class="size-3.5 shrink-0 text-amber-400/80" />
      <span class="min-w-0 flex-1 truncate">{{ node.name }}</span>
      <span class="font-mono text-[10px] text-muted-foreground/70">{{ node.code }}</span>
      <span class="flex shrink-0 items-center opacity-0 group-hover:opacity-100">
        <button
          type="button"
          title="新建子分组"
          class="grid size-5 place-items-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
          @click.stop="emit('createChild', node.id)"
        >
          <Plus class="size-3" />
        </button>
        <button
          type="button"
          title="编辑"
          class="grid size-5 place-items-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
          @click.stop="emit('edit', node)"
        >
          <IconPencil class="size-3" />
        </button>
        <button
          type="button"
          title="清空本组挂载"
          class="grid size-5 place-items-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
          @click.stop="emit('clear', node)"
        >
          <IconEraser class="size-3" />
        </button>
        <button
          type="button"
          title="删除分组"
          class="grid size-5 place-items-center rounded text-muted-foreground hover:bg-muted hover:text-destructive"
          @click.stop="emit('delete', node)"
        >
          <Trash2 class="size-3" />
        </button>
      </span>
    </div>
    <div v-if="isOpen() && hasChildren()" class="pl-4">
      <GroupTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :selected-id="selectedId"
        :expanded="expanded"
        @select="emit('select', $event)"
        @toggle="emit('toggle', $event)"
        @create-child="emit('createChild', $event)"
        @edit="emit('edit', $event)"
        @delete="emit('delete', $event)"
        @clear="emit('clear', $event)"
      />
    </div>
  </div>
</template>
