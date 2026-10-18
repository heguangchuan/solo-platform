<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconDeviceCctv class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新增 ONVIF 设备' : device?.name || `${device?.host}:${device?.port}` }}
        </div>
        <div v-if="creating" class="text-xs text-muted-foreground">
          保存后会自动探测一次，取回在线状态与流地址
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 设备信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">设备信息</h4>

      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">名称</Label>
          <Input v-model="form.name" class="h-8 text-xs" placeholder="显示名（可选）" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">
            IP 地址<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.host"
              class="h-8 font-mono text-xs"
              placeholder="如 192.168.1.64"
            />
            <p v-if="errors.host" class="mt-1 text-[11px] text-destructive">{{ errors.host }}</p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">
            端口<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model.number="form.port"
              type="number"
              min="1"
              max="65535"
              class="h-8 font-mono text-xs"
              placeholder="ONVIF 服务端口，默认 80"
            />
            <p v-if="errors.port" class="mt-1 text-[11px] text-destructive">{{ errors.port }}</p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">账号</Label>
          <Input
            v-model="form.username"
            class="h-8 font-mono text-xs"
            placeholder="鉴权用户名（可选）"
          />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">密码</Label>
          <Input
            v-model="form.password"
            type="password"
            class="h-8 font-mono text-xs"
            placeholder="鉴权密码（可选）"
            autocomplete="new-password"
          />
        </div>
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button size="sm" :loading="saving" @click="onSave">保存</Button>
      </div>
    </section>

    <!-- 运行状态 -->
    <template v-if="!creating && device">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">运行状态</h4>
        <div class="space-y-2 text-xs">
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">在线状态</span>
            <span :class="device.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'">
              {{ device.status === 1 ? '在线' : '离线' }}
            </span>
          </div>
          <div class="flex items-start justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">流地址</span>
            <span
              class="min-w-0 truncate text-right font-mono"
              :class="device.streamUri ? '' : 'text-muted-foreground'"
              :title="device.streamUri"
            >
              {{ device.streamUri || '未探测到' }}
            </span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">最近探测</span>
            <span class="font-mono">{{ device.lastCheckTime || '—' }}</span>
          </div>
        </div>

        <div class="mt-3">
          <Button
            v-access="PERM.ONVIF_DEVICE_PROBE"
            variant="outline"
            size="sm"
            class="w-full"
            :loading="probing"
            @click="onProbe"
          >
            <IconRefresh class="size-3.5" />
            立即探测
          </Button>
          <p class="mt-1.5 text-[11px] text-muted-foreground/70">
            探测为同步执行（TCP + ONVIF 握手），可能需要数秒
          </p>
        </div>
      </section>

      <!-- 危险操作 -->
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          v-access="PERM.ONVIF_DEVICE_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="h-3.5 w-3.5" />
          删除该设备
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { IconDeviceCctv, IconX, IconTrash, IconRefresh } from '@tabler/icons-vue'
import type { OnvifDevice, OnvifDeviceRequest } from '@/types/api'
import {
  saveOnvifDeviceApi,
  updateOnvifDeviceApi,
  probeOnvifDeviceApi,
  deleteOnvifDeviceApi,
} from '@/api/onvif'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'

const props = defineProps<{
  device?: OnvifDevice
  creating?: boolean
}>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [device: OnvifDevice]
}>()

const buildForm = (): OnvifDeviceRequest => ({
  id: props.device?.id,
  name: props.device?.name ?? '',
  host: props.device?.host ?? '',
  port: props.device?.port ?? 80,
  username: props.device?.username ?? '',
  password: props.device?.password ?? '',
})

const form = reactive(buildForm())
const saving = ref(false)
const probing = ref(false)
const errors = reactive<{ host: string; port: string }>({ host: '', port: '' })

watch(
  () => props.device?.id,
  () => {
    Object.assign(form, buildForm())
    errors.host = ''
    errors.port = ''
  },
)

const validate = (): boolean => {
  errors.host = form.host.trim() ? '' : '请输入设备 IP'
  errors.port =
    form.port && form.port >= 1 && form.port <= 65535 ? '' : '请输入 1-65535 的端口'
  return !errors.host && !errors.port
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    const payload: OnvifDeviceRequest = {
      ...form,
      name: form.name?.trim() || undefined,
      host: form.host.trim(),
      username: form.username?.trim() || undefined,
      password: form.password || undefined,
    }
    if (props.creating) {
      await saveOnvifDeviceApi(payload)
      toast.success('设备创建成功')
      emit('created')
    } else if (props.device) {
      await updateOnvifDeviceApi({ ...payload, id: props.device.id })
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onProbe = async () => {
  if (!props.device) return
  probing.value = true
  try {
    await probeOnvifDeviceApi(props.device.id)
    toast.success('探测完成')
    emit('saved')
  } finally {
    probing.value = false
  }
}

const onDelete = async () => {
  if (!props.device) return
  const ok = await confirm({
    title: '删除 ONVIF 设备',
    description: `确认删除设备「${props.device.name || `${props.device.host}:${props.device.port}`}」？删除后不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteOnvifDeviceApi(props.device.id)
  toast.success('设备已删除')
  emit('deleted', props.device)
}
</script>
