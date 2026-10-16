<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconKey class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新增推流方' : client?.clientId }}
        </div>
        <div v-if="creating" class="text-xs text-muted-foreground">
          创建一个可推流的客户端身份
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 授权信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">授权信息</h4>

      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">
            推流方<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.clientId"
              class="h-8 font-mono text-xs"
              placeholder="唯一标识，如 obs / cam-01"
              :disabled="!creating"
            />
            <p v-if="errors.clientId" class="mt-1 text-[11px] text-destructive">
              {{ errors.clientId }}
            </p>
            <p v-else-if="!creating" class="mt-1 text-[11px] text-muted-foreground">
              推流方标识创建后不可修改
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">状态</Label>
          <div class="flex-1">
            <Switch
              :model-value="form.enabled === 1"
              @update:model-value="(v) => (form.enabled = v ? 1 : 0)"
            />
          </div>
        </div>

        <div class="flex items-start gap-3">
          <Label class="mt-1.5 w-16 shrink-0 text-xs text-muted-foreground">备注</Label>
          <textarea
            v-model="form.remark"
            rows="2"
            class="min-h-14 w-full rounded-md border border-input bg-transparent px-2 py-1.5 text-xs outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/15"
            placeholder="备注（可选）"
          />
        </div>
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button size="sm" :loading="saving" @click="onSave">
          {{ creating ? '创建' : '保存' }}
        </Button>
      </div>
    </section>

    <!-- 已有推流方：Token 与危险操作 -->
    <template v-if="!creating && client">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">Token</h4>
        <div class="rounded-md border bg-muted/40 p-2.5">
          <div class="flex items-start gap-2">
            <code class="min-w-0 flex-1 break-all font-mono text-xs leading-5">
              {{ client.token }}
            </code>
            <Button
              variant="ghost"
              size="icon-sm"
              class="size-7 shrink-0"
              title="复制 token"
              @click="copyToken(client.token)"
            >
              <IconCopy class="size-3.5" />
            </Button>
          </div>
        </div>

        <Button
          v-access="PERM.MEDIA_STREAM_PUSH_CLIENT_EDIT"
          variant="outline"
          size="sm"
          class="mt-2 w-full"
          @click="onResetToken"
        >
          <IconRefresh class="size-3.5" />
          重置 Token
        </Button>
        <p class="mt-1.5 text-[11px] text-muted-foreground">
          重置后老 token 立即失效；正在推流的连接不会断开，需在推流列表手动中止。
        </p>
      </section>

      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          v-access="PERM.MEDIA_STREAM_PUSH_CLIENT_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="h-3.5 w-3.5" />
          删除该推流方
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  IconX,
  IconTrash,
  IconCopy,
  IconRefresh,
  IconKey,
} from '@tabler/icons-vue'
import type { StreamPushClient } from '@/types/api'
import {
  saveStreamPushClientApi,
  updateStreamPushClientApi,
  resetStreamPushClientTokenApi,
  deleteStreamPushClientApi,
} from '@/api/media'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'
import { Switch } from '@/components/ui/switch'

const props = defineProps<{
  client?: StreamPushClient
  creating?: boolean
}>()
const emit = defineEmits<{
  close: []
  saved: []
  created: [client: StreamPushClient]
  'token-reset': [client: StreamPushClient]
  deleted: [client: StreamPushClient]
}>()

type FormState = {
  clientId: string
  enabled: number
  remark?: string
}

const buildForm = (): FormState => ({
  clientId: props.client?.clientId ?? '',
  enabled: props.client?.enabled ?? 1,
  remark: props.client?.remark ?? '',
})

const form = reactive<FormState>(buildForm())
const errors = reactive<{ clientId: string }>({ clientId: '' })
const saving = ref(false)

watch(
  () => props.client?.id,
  () => {
    Object.assign(form, buildForm())
    errors.clientId = ''
  },
)

const copyToken = async (token: string) => {
  if (!token) return
  try {
    await navigator.clipboard.writeText(token)
    toast.success('已复制')
  } catch {
    toast.warning('复制失败，请手动选择文本')
  }
}

const onSave = async () => {
  if (props.creating && !form.clientId.trim()) {
    errors.clientId = '请输入推流方标识'
    return
  }
  errors.clientId = ''
  saving.value = true
  try {
    if (props.creating) {
      const res = await saveStreamPushClientApi({
        clientId: form.clientId.trim(),
        enabled: form.enabled,
        remark: form.remark?.trim() || undefined,
      })
      toast.success('推流方创建成功')
      emit('created', res.data)
    } else if (props.client) {
      await updateStreamPushClientApi({
        id: props.client.id,
        clientId: props.client.clientId,
        enabled: form.enabled,
        remark: form.remark?.trim() || undefined,
      })
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onResetToken = async () => {
  if (!props.client) return
  const ok = await confirm({
    title: '重置 Token',
    description: `确认重置「${props.client.clientId}」的 token？老 token 立即失效。`,
    confirmText: '重置',
    danger: true,
  })
  if (!ok) return
  const res = await resetStreamPushClientTokenApi(props.client.id)
  toast.success('Token 已重置')
  emit('token-reset', res.data)
}

const onDelete = async () => {
  if (!props.client) return
  const ok = await confirm({
    title: '删除推流方',
    description: `确认删除推流方「${props.client.clientId}」？该 token 立即失效。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteStreamPushClientApi(props.client.id)
  toast.success('推流方已删除')
  emit('deleted', props.client)
}
</script>
