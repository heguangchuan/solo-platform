<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <Network class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '登记下级平台' : form.name || form.platformId }}
        </div>
        <div class="font-mono text-xs text-muted-foreground">
          {{ creating ? '登记后可控制该下级平台的接入' : form.platformId }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 本平台 SIP 接入信息（提供给下级管理员） -->
    <section class="rounded-lg border border-dashed border-border bg-muted/30 p-3.5">
      <div class="flex items-center gap-1.5">
        <Info class="size-3.5 text-muted-foreground" />
        <p class="text-xs font-medium">把本平台接入信息提供给下级管理员</p>
        <div class="flex-1" />
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded px-1 py-0.5 text-[11px] text-muted-foreground outline-none hover:bg-background/60 hover:text-foreground"
          title="复制全部"
          @click="copyAll"
        >
          <Copy class="size-3" />
        </button>
      </div>
      <p class="mt-1 text-[11px] leading-relaxed text-muted-foreground">
        下级平台需按此信息向上注册到本平台。
      </p>
      <div class="mt-2.5 space-y-1">
        <div
          v-for="row in localRows"
          :key="row.label"
          class="flex w-full items-center gap-2 rounded px-1 py-0.5 text-left text-xs"
        >
          <span class="w-[52px] shrink-0 text-muted-foreground">{{ row.label }}</span>
          <span
            class="min-w-0 flex-1 truncate font-mono"
            :class="row.value ? 'text-foreground/90' : 'text-muted-foreground/70'"
          >
            {{ row.value || (row.label === '鉴权码' ? '无鉴权' : '—') }}
          </span>
        </div>
      </div>
    </section>

    <!-- 信息 -->
    <section class="mt-5">
      <h4 class="mb-3.5 text-[13px] font-medium">下级平台信息</h4>
      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            国标编码<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.platformId"
              class="h-8 font-mono text-xs"
              placeholder="20 位数字"
              :disabled="!creating"
            />
            <p v-if="errors.platformId" class="mt-1 text-[11px] text-destructive">
              {{ errors.platformId }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">备注名</Label>
          <div class="flex-1">
            <Input
              v-model="form.name"
              class="h-8 text-xs"
              placeholder="可空，便于运维辨识"
            />
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">接入方式</Label>
          <div class="flex-1">
            <Select v-model="enabledModel">
              <SelectTrigger class="h-8 w-full text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="0">禁止接入</SelectItem>
                <SelectItem value="1">允许接入</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      <div class="mt-5 flex justify-end gap-2">
        <Button variant="outline" size="sm" @click="emit('close')">取消</Button>
        <Button
          v-access="creating ? PERM.GB_PLATFORM_ADD : PERM.GB_PLATFORM_EDIT"
          size="sm"
          :loading="saving"
          @click="onSubmit"
        >
          {{ creating ? '登记' : '保存' }}
        </Button>
      </div>
    </section>

    <!-- 已有记录：删除 -->
    <template v-if="!creating">
      <div class="my-4 h-px bg-border" />
      <section>
        <h4 class="mb-2 text-[13px] font-medium text-destructive">删除下级平台</h4>
        <p class="mb-3 text-[11px] text-muted-foreground">
          删除后该下级平台的接入登记将被移除。
        </p>
        <Button
          v-access="PERM.GB_PLATFORM_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <Trash2 class="size-3.5" />
          删除下级平台
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { IconX } from '@tabler/icons-vue'
import { Copy, Info, Network, Trash2 } from 'lucide-vue-next'
import Button from '@/components/ui/button/Button.vue'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  saveGbPlatformApi,
  updateGbPlatformApi,
  deleteGbPlatformApi,
} from '@/api/gb28181'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import type { GbLocalPlatformInfo, GbPlatform } from '@/types/api'

const props = defineProps<{
  platform?: GbPlatform
  creating?: boolean
  localInfo?: GbLocalPlatformInfo
}>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [platform: GbPlatform]
}>()

/* 本平台接入信息行 */
const localRows = computed(() => {
  const info = props.localInfo ?? {}
  return [
    { label: 'SIP ID', value: info.id },
    { label: 'SIP 域', value: info.domain },
    {
      label: 'SIP 地址',
      value: info.ip ? `${info.ip}${info.port != null ? `:${info.port}` : ''}` : undefined,
    },
    { label: '鉴权码', value: info.password },
  ]
})

const copyAll = async () => {
  const text = localRows.value
    .map((r) => {
      const value = r.value || (r.label === '鉴权码' ? '无鉴权' : '—')
      return `${r.label}：${value}`
    })
    .join('\n')
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制全部接入信息')
  } catch {
    toast.error('复制失败，请手动选择文本')
  }
}

type FormState = {
  id?: string
  platformId: string
  name: string
  enabled: number
}

const buildForm = (): FormState => ({
  id: props.platform?.id,
  platformId: props.platform?.platformId ?? '',
  name: props.platform?.name ?? '',
  enabled: props.platform?.enabled ?? 0,
})

const form = reactive<FormState>(buildForm())
const errors = reactive<Record<string, string>>({})

watch(
  () => props.platform?.id,
  () => {
    Object.assign(form, buildForm())
    Object.keys(errors).forEach((k) => delete errors[k])
  },
)

const enabledModel = computed({
  get: () => String(form.enabled),
  set: (v: string) => {
    form.enabled = Number(v)
  },
})

const validate = () => {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!/^\d{20}$/.test(form.platformId))
    errors.platformId = '国标编码必须是 20 位数字'
  return Object.keys(errors).length === 0
}

const saving = ref(false)
const onSubmit = async () => {
  if (saving.value || !validate()) return
  saving.value = true
  try {
    if (props.creating) {
      await saveGbPlatformApi({ ...form })
      toast.success('已登记下级平台')
      emit('created')
    } else {
      await updateGbPlatformApi({ ...form })
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  if (!props.platform) return
  const ok = await confirm({
    title: `确定删除下级平台 ${props.platform.platformId} 吗？`,
    description: '删除后该下级平台的接入登记将被移除。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteGbPlatformApi(props.platform.id)
  toast.success('已删除下级平台')
  emit('deleted', props.platform)
}
</script>
