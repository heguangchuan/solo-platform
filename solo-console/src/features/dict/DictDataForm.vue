<template>
  <div class="space-y-2 rounded-lg border border-primary/30 bg-primary/[0.04] p-2.5">
    <div class="flex items-center gap-2">
      <input
        v-model="form.dictLabel"
        class="h-7 min-w-0 flex-1 rounded-md bg-transparent px-2 text-xs outline-none focus:bg-background"
        :class="errors.dictLabel ? 'ring-1 ring-destructive' : ''"
        placeholder="标签（如：男）"
        @blur="errors.dictLabel = form.dictLabel.trim() ? '' : '必填'"
      />
      <input
        v-model="form.dictValue"
        class="h-7 w-20 shrink-0 rounded-md bg-transparent px-2 font-mono text-xs outline-none focus:bg-background"
        :class="errors.dictValue ? 'ring-1 ring-destructive' : ''"
        placeholder="键值"
        @blur="errors.dictValue = form.dictValue.trim() ? '' : '必填'"
      />
    </div>
    <div class="flex items-center gap-2">
      <input
        v-model.number="sortValue"
        type="number"
        min="1"
        class="h-7 w-20 shrink-0 rounded-md bg-transparent px-2 text-xs outline-none focus:bg-background"
        placeholder="排序"
      />
      <div class="flex items-center gap-1.5">
        <Switch
          :model-value="form.status === 1"
          @update:model-value="(v) => (form.status = v ? 1 : 0)"
        />
        <span class="text-[11px] text-muted-foreground">{{ form.status === 1 ? '启用' : '禁用' }}</span>
      </div>
      <div class="flex-1" />
      <Button variant="ghost" size="sm" class="h-7 text-xs" @click="emit('cancel')">取消</Button>
      <Button size="sm" class="h-7 text-xs" :loading="saving" @click="onSave">
        {{ data ? '保存' : '添加' }}
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { SysDictData, DictDataRequest } from '@/types/api'
import { addDictDataApi, editDictDataApi } from '@/api/dict'
import { toast } from '@/lib/toast'
import { clearDictCache } from '@/features/dict/useDict'
import Button from '@/components/ui/button/Button.vue'
import Switch from '@/components/ui/switch/Switch.vue'

const props = defineProps<{ dictType: string; data?: SysDictData }>()
const emit = defineEmits<{
  (e: 'cancel'): void
  (e: 'saved', data: SysDictData | null): void
}>()

const form = reactive<DictDataRequest>({
  dictType: props.dictType,
  dictLabel: props.data?.dictLabel ?? '',
  dictValue: props.data?.dictValue ?? '',
  sort: props.data?.sort ?? 1,
  status: props.data?.status ?? 1,
})

const sortValue = ref<number>(form.sort ?? 1)
const saving = ref(false)
const errors = reactive<{ dictLabel: string; dictValue: string }>({
  dictLabel: '',
  dictValue: '',
})

const onSave = async () => {
  errors.dictLabel = form.dictLabel.trim() ? '' : '必填'
  errors.dictValue = form.dictValue.trim() ? '' : '必填'
  if (errors.dictLabel || errors.dictValue) return

  form.sort = sortValue.value || 1
  saving.value = true
  try {
    if (props.data) {
      await editDictDataApi(props.data.id, { ...form })
      toast.success('字典项已保存')
      emit('saved', { ...props.data, ...form })
    } else {
      await addDictDataApi({ ...form })
      toast.success('字典项已添加')
      emit('saved', null)
    }
    clearDictCache(props.dictType)
  } finally {
    saving.value = false
  }
}
</script>
