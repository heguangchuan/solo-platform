<template>
  <div class="space-y-2">
    <div class="flex flex-wrap items-center gap-2">
      <template v-for="f in fields" :key="f.key">
        <!-- 文本 -->
        <div v-if="f.type === 'text'" class="relative">
          <Search class="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <input
            :value="(values[f.key] as string) ?? ''"
            type="text"
            :placeholder="f.placeholder || f.label"
            class="h-8 w-40 rounded-md border border-input bg-transparent pl-8 pr-2.5 text-xs outline-none transition-colors focus:ring-2 focus:ring-ring"
            @input="onTextInput(f.key, ($event.target as HTMLInputElement).value)"
          />
        </div>

        <!-- 下拉 -->
        <Select
          v-else
          :model-value="values[f.key] === null ? '__all__' : (values[f.key] as string | number)"
          @update:model-value="(v) => onSelectValue(f.key, v as string | number)"
        >
          <SelectTrigger size="sm" class="w-32 text-xs">
            <SelectValue :placeholder="f.placeholder || f.label" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">{{ f.placeholder || `全部${f.label}` }}</SelectItem>
            <SelectItem
              v-for="o in (f as QuerySelectField).options"
              :key="o.value"
              :value="String(o.value)"
            >
              {{ o.label }}
            </SelectItem>
          </SelectContent>
        </Select>
      </template>

      <button
        v-if="activeCount > 0"
        type="button"
        class="flex h-8 items-center gap-1 rounded-md px-2 text-xs text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
        @click="resetAll"
      >
        <RotateCcw class="h-3 w-3" />
        重置
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { Search, RotateCcw } from 'lucide-vue-next'
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/components/ui/select'

export interface QueryTextField {
  key: string
  label: string
  type: 'text'
  placeholder?: string
  pin?: boolean
}
export interface QuerySelectField {
  key: string
  label: string
  type: 'select'
  placeholder?: string
  options: { label: string; value: string | number }[]
  pin?: boolean
}
export type QueryField = QueryTextField | QuerySelectField
export type QueryValue = string | number | null
export type QueryValues = Record<string, QueryValue>

const props = defineProps<{ fields: QueryField[] }>()
const emit = defineEmits<{ search: [values: QueryValues] }>()

const values = reactive<QueryValues>({})
for (const f of props.fields) values[f.key] = null

const debounceTimers = new Map<string, number>()

const activeCount = computed(
  () => Object.values(values).filter((v) => v !== null && v !== '' && v !== '__all__').length
)

const emitSearch = () => {
  const out: QueryValues = {}
  for (const f of props.fields) {
    const v = values[f.key]
    out[f.key] = v === '__all__' ? null : v === '' ? null : v
  }
  emit('search', out)
}

const onTextInput = (key: string, val: string) => {
  values[key] = val
  window.clearTimeout(debounceTimers.get(key))
  const t = window.setTimeout(emitSearch, 300)
  debounceTimers.set(key, t)
}

const onSelectValue = (key: string, val: string | number) => {
  values[key] = val
  emitSearch()
}

const resetAll = () => {
  for (const f of props.fields) values[f.key] = null
  emitSearch()
}

defineExpose({ reset: resetAll })
</script>
