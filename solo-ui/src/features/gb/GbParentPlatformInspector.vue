<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <Share2 class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '接入上级平台' : form.name || form.serverGbId }}
        </div>
        <div class="text-xs text-muted-foreground">
          {{ creating ? '向上级注册并推送本平台目录' : '修改注册与推送配置' }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 新增：本平台身份（配置引导，不单独罗列在列表页） -->
    <section v-if="creating" class="rounded-lg border border-dashed border-border bg-muted/30 p-3.5">
      <div class="flex items-center gap-1.5">
        <Info class="size-3.5 text-muted-foreground" />
        <p class="text-xs font-medium">把本平台身份提供给上级管理员</p>
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
        对方需先在其平台将本平台加入白名单，注册才会通过。
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

    <!-- 上级平台信息 -->
    <section :class="creating ? 'mt-5' : ''">
      <h4 class="mb-3.5 text-[13px] font-medium">上级平台信息</h4>
      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            平台名称<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input v-model="form.name" class="h-8 text-xs" placeholder="上级平台显示名称" />
            <p v-if="errors.name" class="mt-1 text-[11px] text-destructive">{{ errors.name }}</p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            国标编码<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.serverGbId"
              class="h-8 font-mono text-xs"
              placeholder="上级平台 20 位编码"
              :disabled="!creating"
            />
            <p v-if="errors.serverGbId" class="mt-1 text-[11px] text-destructive">
              {{ errors.serverGbId }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            国标域<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.serverGbDomain"
              class="h-8 font-mono text-xs"
              placeholder="通常为编码前 10 位"
            />
            <p v-if="errors.serverGbDomain" class="mt-1 text-[11px] text-destructive">
              {{ errors.serverGbDomain }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            上级地址<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.serverIp"
              class="h-8 font-mono text-xs"
              placeholder="上级 SIP 服务器 IP"
            />
            <p v-if="errors.serverIp" class="mt-1 text-[11px] text-destructive">
              {{ errors.serverIp }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            上级端口<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model.number="form.serverPort"
              type="number"
              class="h-8 font-mono text-xs"
              placeholder="5060"
            />
            <p v-if="errors.serverPort" class="mt-1 text-[11px] text-destructive">
              {{ errors.serverPort }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            注册密码<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.password"
              type="password"
              class="h-8 text-xs"
              placeholder="SIP 注册密码"
            />
            <p v-if="errors.password" class="mt-1 text-[11px] text-destructive">
              {{ errors.password }}
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- 推送配置 -->
    <section class="mt-6">
      <h4 class="mb-3.5 text-[13px] font-medium">推送配置</h4>
      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">目录分片</Label>
          <div class="flex-1">
            <Select v-model="catalogGroupModel">
              <SelectTrigger class="h-8 w-full text-xs">
                <SelectValue placeholder="默认（走全局配置）" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__default__">默认（走全局配置）</SelectItem>
                <SelectItem value="1">1 条/包</SelectItem>
                <SelectItem value="2">2 条/包</SelectItem>
                <SelectItem value="4">4 条/包</SelectItem>
                <SelectItem value="8">8 条/包</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">注册期限</Label>
          <div class="flex-1">
            <Input
              v-model.number="form.expires"
              type="number"
              class="h-8 font-mono text-xs"
              placeholder="3600"
            />
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">心跳间隔</Label>
          <div class="flex-1">
            <Input
              v-model.number="form.keepTimeout"
              type="number"
              class="h-8 font-mono text-xs"
              placeholder="60"
            />
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">传输协议</Label>
          <div class="flex-1">
            <Select v-model="form.transport">
              <SelectTrigger class="h-8 w-full text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="UDP">UDP</SelectItem>
                <SelectItem value="TCP">TCP</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">字符编码</Label>
          <div class="flex-1">
            <Select v-model="form.characterSet">
              <SelectTrigger class="h-8 w-full text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="GB2312">GB2312</SelectItem>
                <SelectItem value="UTF-8">UTF-8</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">是否启用</Label>
          <div class="flex-1">
            <Select v-model="enabledModel">
              <SelectTrigger class="h-8 w-full text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="1">启用</SelectItem>
                <SelectItem value="0">禁用</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      <div class="mt-5 flex justify-end gap-2">
        <Button variant="outline" size="sm" @click="emit('close')">取消</Button>
        <Button
          v-access="creating ? PERM.GB_PARENT_PLATFORM_ADD : PERM.GB_PARENT_PLATFORM_EDIT"
          size="sm"
          :loading="saving"
          @click="onSubmit"
        >
          {{ creating ? '接入' : '保存' }}
        </Button>
      </div>
    </section>

    <!-- 已有平台：运行信息 -->
    <template v-if="!creating && platform">
      <div class="my-4 h-px bg-border" />
      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">运行信息</h4>
        <div class="space-y-2 text-xs">
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">接入状态</span>
            <span :class="platform.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'">
              {{ platform.status === 1 ? '在线' : '未接入' }}
            </span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">本地编号</span>
            <span class="truncate font-mono">{{ platform.deviceGbId || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">创建时间</span>
            <span class="truncate font-mono">{{ platform.createTime || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">更新时间</span>
            <span class="truncate font-mono">{{ platform.updateTime || '—' }}</span>
          </div>
        </div>
      </section>

      <!-- 危险操作 -->
      <section class="mt-6">
        <h4 class="mb-2 text-[13px] font-medium text-destructive">危险操作</h4>
        <p class="mb-3 text-[11px] text-muted-foreground">
          删除后本平台将不再向其注册和推送目录。
        </p>
        <Button
          v-access="PERM.GB_PARENT_PLATFORM_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <Trash2 class="size-3.5" />
          删除该上级平台
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { IconX } from '@tabler/icons-vue'
import { Copy, Info, Share2, Trash2 } from 'lucide-vue-next'
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
  saveGbParentPlatformApi,
  updateGbParentPlatformApi,
  deleteGbParentPlatformApi,
} from '@/api/gb28181'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import type { GbLocalPlatformInfo, GbParentPlatform } from '@/types/api'

const props = defineProps<{
  platform?: GbParentPlatform
  creating?: boolean
  localInfo?: GbLocalPlatformInfo
}>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [platform: GbParentPlatform]
}>()

type FormState = {
  id?: string
  name: string
  serverGbId: string
  serverGbDomain: string
  serverIp: string
  serverPort: number
  password: string
  expires: number
  keepTimeout: number
  transport: string
  characterSet: string
  enabled: number
  catalogGroup?: number
}

const buildForm = (): FormState => ({
  id: props.platform?.id,
  name: props.platform?.name ?? '',
  serverGbId: props.platform?.serverGbId ?? '',
  serverGbDomain: props.platform?.serverGbDomain ?? '',
  serverIp: props.platform?.serverIp ?? '',
  serverPort: props.platform?.serverPort ?? 5060,
  password: props.platform?.password ?? '',
  expires: props.platform?.expires ?? 3600,
  keepTimeout: props.platform?.keepTimeout ?? 60,
  transport: props.platform?.transport ?? 'UDP',
  characterSet: props.platform?.characterSet ?? 'GB2312',
  enabled: props.platform?.enabled ?? 1,
  catalogGroup: props.platform?.catalogGroup,
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

/** 目录分片 reka 需要 string 值，与数字字段互转 */
const catalogGroupModel = computed({
  get: () => (form.catalogGroup == null ? '__default__' : String(form.catalogGroup)),
  set: (v: string) => {
    form.catalogGroup = v === '__default__' ? undefined : Number(v)
  },
})
const enabledModel = computed({
  get: () => String(form.enabled),
  set: (v: string) => {
    form.enabled = Number(v)
  },
})

/* 本平台身份复制行 */
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
    toast.success('已复制全部身份信息')
  } catch {
    toast.error('复制失败，请手动选择文本')
  }
}

const validate = () => {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.name.trim()) errors.name = '请输入上级平台名称'
  if (!/^\d{20}$/.test(form.serverGbId)) errors.serverGbId = '国标编码必须是 20 位数字'
  if (!form.serverGbDomain.trim()) errors.serverGbDomain = '请输入上级平台国标域'
  if (!form.serverIp.trim()) errors.serverIp = '请输入上级地址 IP'
  if (!(form.serverPort > 0)) errors.serverPort = '请输入端口号'
  if (!form.password) errors.password = '请输入认证密码'
  return Object.keys(errors).length === 0
}

const saving = ref(false)
const onSubmit = async () => {
  if (saving.value || !validate()) return
  saving.value = true
  try {
    const payload: Partial<GbParentPlatform> = {
      ...form,
      serverPort: form.serverPort || 5060,
      expires: form.expires || 3600,
      keepTimeout: form.keepTimeout || 60,
    }
    if (props.creating) {
      await saveGbParentPlatformApi(payload)
      toast.success('接入成功')
      emit('created')
    } else {
      await updateGbParentPlatformApi(payload)
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
    title: `确定要删除上级平台 “${props.platform.name}” 吗？`,
    description: '删除后本平台将不再向其注册和推送目录。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteGbParentPlatformApi(props.platform.id)
  toast.success('删除成功')
  emit('deleted', props.platform)
}
</script>
