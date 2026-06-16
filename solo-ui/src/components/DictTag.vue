<script setup lang="ts">
import { computed, toRef } from 'vue'
import { useDict } from '@/composables/useDict'

type DictValue = string | number

type DisplayItem = {
  key: string
  label: string
}

const props = withDefaults(
  defineProps<{
    dictType: string
    value?: DictValue | DictValue[]
    fallback?: string
    size?: 'default' | 'small' | 'large'
    plain?: boolean
    showUnknown?: boolean
  }>(),
  {
    value: '',
    fallback: '',
    size: 'default',
    plain: false,
    showUnknown: true,
  },
)

const { options } = useDict(toRef(props, 'dictType'))

const displayItems = computed<DisplayItem[]>(() => {
  const values = Array.isArray(props.value) ? props.value : [props.value]
  return values
    .filter((value) => value !== undefined && value !== null && value !== '')
    .map((value) => buildDisplayItem(value))
    .filter((item): item is DisplayItem => !!item)
})

function buildDisplayItem(value: DictValue): DisplayItem | null {
  const option = options.value.find((item) => String(item.dictValue) === String(value))
  if (option) {
    return {
      key: option.id || option.dictValue,
      label: option.dictLabel,
    }
  }
  if (!props.showUnknown) return null
  return {
    key: String(value),
    label: props.fallback || String(value),
  }
}
</script>

<template>
  <span class="dict-tag-list">
    <el-tag v-for="item in displayItems" :key="item.key" :size="size" :effect="plain ? 'plain' : 'light'">
      {{ item.label }}
    </el-tag>
  </span>
</template>

<style scoped>
.dict-tag-list {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
