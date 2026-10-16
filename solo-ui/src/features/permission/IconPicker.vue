<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { IconSearch, IconX, IconChevronDown } from '@tabler/icons-vue'
import { loadAllIcons } from './iconLibrary'
import DynamicIcon from './DynamicIcon.vue'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const open = ref(false)
const keyword = ref('')
const allNames = ref<string[]>([])
const renderCount = ref(200)

loadAllIcons().then((icons) => {
  allNames.value = Object.keys(icons).sort()
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const names = kw ? allNames.value.filter((n) => n.toLowerCase().includes(kw)) : allNames.value
  return names.slice(0, renderCount.value)
})

const onScroll = (e: Event) => {
  const el = e.target as HTMLElement
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 20) renderCount.value += 200
}

const select = (name: string) => {
  emit('update:modelValue', name)
  open.value = false
}

const clear = () => emit('update:modelValue', '')

watch(open, (v) => {
  if (v) {
    keyword.value = ''
    renderCount.value = 200
  }
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
      <DynamicIcon v-if="props.modelValue" :name="props.modelValue" :size="15" />
      <span :class="props.modelValue ? '' : 'text-muted-foreground'" class="min-w-0 flex-1 truncate text-left">
        {{ props.modelValue || '请选择图标' }}
      </span>
      <IconX v-if="props.modelValue" class="size-3.5 shrink-0 text-muted-foreground" @click.stop="clear" />
      <IconChevronDown v-else class="size-3.5 shrink-0 text-muted-foreground" />
    </button>

    <div
      v-if="open"
      class="absolute z-50 mt-1 flex w-[340px] flex-col gap-2 rounded-md border border-border bg-popover p-2 shadow-md"
    >
      <div class="flex items-center gap-1.5 rounded-md bg-muted px-2">
        <IconSearch class="size-3.5 shrink-0 text-muted-foreground" />
        <input
          v-model="keyword"
          class="h-7 w-full bg-transparent text-xs outline-none placeholder:text-muted-foreground"
          placeholder="搜索图标名（英文）…"
        />
      </div>
      <div class="grid max-h-64 grid-cols-8 gap-1 overflow-y-auto pr-1" @scroll="onScroll">
        <button
          v-for="name in filtered"
          :key="name"
          type="button"
          class="flex size-8 items-center justify-center rounded-md border border-transparent text-muted-foreground hover:border-border hover:bg-accent hover:text-accent-foreground"
          :class="props.modelValue === name ? 'border-primary bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground' : ''"
          :title="name"
          @click="select(name)"
        >
          <DynamicIcon :name="name" :size="17" />
        </button>
        <span v-if="!filtered.length" class="col-span-8 py-6 text-center text-xs text-muted-foreground">
          未找到匹配图标
        </span>
      </div>
    </div>
  </div>
</template>
