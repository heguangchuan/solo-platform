<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconAdjustments class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新建参数' : config?.configName }}
        </div>
        <div v-if="!creating" class="truncate font-mono text-xs text-muted-foreground">
          {{ config?.configKey }}
        </div>
        <div v-else class="text-xs text-muted-foreground">填写系统参数</div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 基本信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">{{ creating ? '参数信息' : '基本信息' }}</h4>

      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            名称<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.configName"
              class="h-8 text-xs"
              placeholder="请输入参数名称"
              @blur="errors.configName = !form.configName.trim() ? '请输入参数名称' : ''"
            />
            <p v-if="errors.configName" class="mt-1 text-[11px] text-destructive">
              {{ errors.configName }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            编码<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.configKey"
              class="h-8 font-mono text-xs"
              :disabled="!creating"
              placeholder="如：sys.title"
              @blur="errors.configKey = creating && !form.configKey.trim() ? '请输入参数编码' : ''"
            />
            <p v-if="errors.configKey" class="mt-1 text-[11px] text-destructive">
              {{ errors.configKey }}
            </p>
            <p v-if="!creating" class="mt-1 text-[11px] text-muted-foreground">
              参数编码创建后不可修改
            </p>
          </div>
        </div>

        <div class="flex items-start gap-3">
          <Label class="mt-1.5 w-14 shrink-0 text-xs text-muted-foreground">
            取值<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <textarea
              v-model="form.configValue"
              rows="3"
              class="min-h-16 w-full rounded-md border border-input bg-transparent px-2 py-1.5 text-xs outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/15"
              placeholder="请输入参数取值"
              @blur="errors.configValue = !form.configValue.trim() ? '请输入参数取值' : ''"
            />
            <p v-if="errors.configValue" class="mt-1 text-[11px] text-destructive">
              {{ errors.configValue }}
            </p>
          </div>
        </div>

        <div class="flex items-start gap-3">
          <Label class="mt-1.5 w-14 shrink-0 text-xs text-muted-foreground">备注</Label>
          <Input
            v-model="form.remark"
            class="h-8 text-xs"
            placeholder="请输入备注（可选）"
          />
        </div>
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button size="sm" :loading="saving" @click="onSave">
          {{ creating ? '创建参数' : '保存' }}
        </Button>
      </div>
    </section>

    <!-- 危险操作 -->
    <template v-if="!creating && config">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          v-access="PERM.CONFIG_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="h-3.5 w-3.5" />
          删除该参数
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { IconAdjustments, IconX, IconTrash } from '@tabler/icons-vue'
import type { SysConfig, ConfigRequest } from '@/types/api'
import { addConfigApi, editConfigApi, deleteConfigApi } from '@/api/config'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'

const props = defineProps<{ config?: SysConfig; creating?: boolean }>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [config: SysConfig]
}>()

const buildForm = (): ConfigRequest => ({
  configName: props.config?.configName ?? '',
  configKey: props.config?.configKey ?? '',
  configValue: props.config?.configValue ?? '',
  remark: props.config?.remark ?? '',
})

const form = reactive<ConfigRequest>(buildForm())
const saving = ref(false)
const errors = reactive<{ configName: string; configKey: string; configValue: string }>({
  configName: '',
  configKey: '',
  configValue: '',
})

watch(
  () => props.config?.id,
  () => {
    Object.assign(form, buildForm())
    errors.configName = ''
    errors.configKey = ''
    errors.configValue = ''
  },
)

const validate = (): boolean => {
  errors.configName = form.configName.trim() ? '' : '请输入参数名称'
  if (props.creating) errors.configKey = form.configKey.trim() ? '' : '请输入参数编码'
  errors.configValue = form.configValue.trim() ? '' : '请输入参数取值'
  return !errors.configName && !errors.configKey && !errors.configValue
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (props.creating) {
      await addConfigApi({
        ...form,
        configName: form.configName.trim(),
        configKey: form.configKey.trim(),
      })
      toast.success('参数创建成功')
      emit('created')
    } else if (props.config) {
      await editConfigApi(props.config.id, { ...form })
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  if (!props.config) return
  const ok = await confirm({
    title: '删除参数',
    description: `确认删除参数「${props.config.configName}」？删除后不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteConfigApi(props.config.id)
  toast.success('参数已删除')
  emit('deleted', props.config)
}
</script>
