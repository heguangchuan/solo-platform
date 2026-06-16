<script setup lang="ts">
import { computed, toRef, watch } from 'vue'
import type { SysDictData } from '@/types/api'
import { useDict } from '@/composables/useDict'

type DictValue = string | number

const props = withDefaults(
  defineProps<{
    modelValue?: DictValue | DictValue[]
    dictType: string
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
    multiple?: boolean
    filterable?: boolean
    valueType?: 'string' | 'number'
  }>(),
  {
    modelValue: '',
    placeholder: '请选择',
    clearable: true,
    disabled: false,
    multiple: false,
    filterable: false,
    valueType: 'string',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: DictValue | DictValue[] | undefined]
  change: [value: DictValue | DictValue[] | undefined, option?: SysDictData | SysDictData[]]
  loaded: [options: SysDictData[]]
}>()

const { options, loading } = useDict(toRef(props, 'dictType'))

const selectValue = computed({
  get: () => props.modelValue,
  set: (value) => {
    emit('update:modelValue', value)
    emit('change', value, findOption(value))
  },
})

const dictOptions = computed(() =>
  options.value.map((item) => ({
    ...item,
    optionValue: normalizeValue(item.dictValue),
  })),
)

watch(options, (value) => emit('loaded', value))

function normalizeValue(value: string): DictValue {
  if (props.valueType === 'number') return Number(value)
  return value
}

function findOption(value: DictValue | DictValue[] | undefined) {
  if (Array.isArray(value)) {
    return options.value.filter((item) => value.includes(normalizeValue(item.dictValue)))
  }
  return options.value.find((item) => normalizeValue(item.dictValue) === value)
}
</script>

<template>
  <el-select
    v-model="selectValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :multiple="multiple"
    :filterable="filterable"
    :loading="loading"
  >
    <el-option
      v-for="item in dictOptions"
      :key="item.id || item.dictValue"
      :label="item.dictLabel"
      :value="item.optionValue"
    />
  </el-select>
</template>
