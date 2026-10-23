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
          {{ creating ? '新增设备' : deviceName }}
        </div>
        <div v-if="creating" class="text-xs text-muted-foreground">
          录入国标设备，可同时添加通道
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
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            设备编码<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.deviceId"
              class="h-8 font-mono text-xs"
              :disabled="!creating"
              placeholder="设备国标编码（20位）"
            />
            <p v-if="errors.deviceId" class="mt-1 text-[11px] text-destructive">
              {{ errors.deviceId }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            设备名称<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input v-model="form.name" class="h-8 text-xs" placeholder="自定义显示名称" />
            <p v-if="errors.name" class="mt-1 text-[11px] text-destructive">{{ errors.name }}</p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >设备厂商</Label
          >
          <Input v-model="form.manufacturer" class="h-8 text-xs" placeholder="厂商" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >设备型号</Label
          >
          <Input v-model="form.model" class="h-8 text-xs" placeholder="型号" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >固件版本</Label
          >
          <Input v-model="form.firmware" class="h-8 text-xs" placeholder="固件版本" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >收流地址</Label
          >
          <Input v-model="form.recvIp" class="h-8 font-mono text-xs" placeholder="留空用注册源IP" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >传输协议</Label
          >
          <div class="flex-1">
            <Select v-model="form.transport">
              <SelectTrigger class="h-8 w-full text-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="UDP">UDP</SelectItem>
                <SelectItem value="TCP">TCP</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >字符编码</Label
          >
          <div class="flex-1">
            <Select v-model="form.charset">
              <SelectTrigger class="h-8 w-full text-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="GB2312">GB2312</SelectItem>
                <SelectItem value="UTF-8">UTF-8</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >注册期限</Label
          >
          <div class="flex-1">
            <Input
              v-model.number="form.expires"
              type="number"
              min="60"
              class="h-8 font-mono text-xs"
            />
          </div>
        </div>

        <div class="flex items-start gap-3">
          <div class="flex w-20 shrink-0 items-center gap-1">
            <Label class="whitespace-nowrap text-xs text-muted-foreground">目录订阅</Label>
            <span
              class="text-muted-foreground/60"
              title="开启后平台订阅设备目录变化，通道有新增/删除/改名时实时推送，不用手动同步。单位：秒，0 或留空=不订阅，最小 30 秒。"
            >
              <IconInfoCircle class="size-3.5" />
            </span>
          </div>
          <div class="flex-1">
            <Input
              v-model.number="form.subscribeCycleForCatalog"
              type="number"
              min="0"
              placeholder="0 / 留空 = 不订阅，最小 30 秒"
              class="h-8 font-mono text-xs"
            />
          </div>
        </div>
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button
          v-access="creating ? PERM.GB_DEVICE_ADD : PERM.GB_DEVICE_EDIT"
          size="sm"
          :loading="saving"
          @click="onSave"
        >
          保存
        </Button>
      </div>
    </section>

    <!-- 添加通道（仅新增） -->
    <template v-if="creating">
      <div class="my-4 h-px bg-border" />

      <section>
        <div class="mb-3 flex items-center justify-between">
          <h4 class="text-[13px] font-medium">添加通道</h4>
          <Button variant="ghost" size="sm" class="h-7 text-xs" @click="addChannel">
            <IconPlus class="size-3.5" />
            添加通道
          </Button>
        </div>

        <div v-if="!channels.length" class="rounded-md border border-dashed py-5 text-center text-xs text-muted-foreground">
          暂不添加，设备注册后可通过「同步」拉取通道
        </div>

        <div v-else class="space-y-2">
          <div
            v-for="(ch, i) in channels"
            :key="i"
            class="rounded-md border bg-muted/30 p-2.5"
          >
            <div class="flex items-center gap-2">
              <Input
                v-model="ch.channelId"
                class="h-7 flex-1 font-mono text-[11px]"
                placeholder="通道国标编码（20位）"
              />
              <Button
                variant="ghost"
                size="icon-sm"
                class="size-7 shrink-0 text-muted-foreground hover:text-destructive"
                title="移除"
                @click="removeChannel(i)"
              >
                <IconTrash class="size-3.5" />
              </Button>
            </div>
            <Input
              v-model="ch.name"
              class="mt-2 h-7 text-[11px]"
              placeholder="通道名称"
            />
            <p v-if="channelErrors[i]" class="mt-1 text-[11px] text-destructive">
              {{ channelErrors[i] }}
            </p>
          </div>
        </div>
      </section>
    </template>

    <!-- 注册 / 上报信息（已有设备） -->
    <template v-if="!creating && device">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">注册信息</h4>
        <div class="space-y-2 text-xs">
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">设备状态</span>
            <span
              :class="
                device.status === 1
                  ? 'text-emerald-500'
                  : device.status === 0
                    ? 'text-red-500'
                    : 'text-muted-foreground'
              "
            >
              {{ statusText }}
            </span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">数据来源</span>
            <span>{{ sourceText(device.source) }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">网络地址</span>
            <span class="truncate font-mono">{{ device.hostAddress || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">本机地址</span>
            <span class="truncate font-mono">{{ device.localIp || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">信令域名</span>
            <span class="truncate font-mono">{{ device.sipDomain || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">最近注册</span>
            <span class="truncate font-mono">{{ device.registerTime || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">最近心跳</span>
            <span class="truncate font-mono">{{ device.keepaliveTime || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">目录同步</span>
            <span class="truncate font-mono">{{ device.lastCatalogSyncTime || '—' }}</span>
          </div>
        </div>
      </section>

      <!-- 危险操作 -->
      <section class="mt-6">
        <h4 class="mb-2 text-[13px] font-medium text-destructive">危险操作</h4>
        <p class="mb-3 text-[11px] text-muted-foreground">
          删除设备记录不影响实际设备，设备重新注册后会再次出现。
        </p>
        <Button
          v-access="PERM.GB_DEVICE_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="size-3.5" />
          删除该设备
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { IconDeviceCctv, IconX, IconPlus, IconTrash, IconInfoCircle } from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  saveGbDeviceApi,
  updateGbDeviceApi,
  deleteGbDeviceApi,
} from '@/api/gb28181'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import type { GbDevice, GbDeviceChannelDraft } from '@/types/api'

const props = defineProps<{
  device?: GbDevice
  creating?: boolean
}>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [device: GbDevice]
}>()

type FormState = {
  id?: string
  deviceId: string
  name: string
  manufacturer: string
  model: string
  firmware: string
  recvIp: string
  transport: string
  charset: string
  expires: number
  subscribeCycleForCatalog: number
}

const buildForm = (): FormState => ({
  id: props.device?.id,
  deviceId: props.device?.deviceId ?? '',
  name: props.device?.name ?? '',
  manufacturer: props.device?.manufacturer ?? '',
  model: props.device?.model ?? '',
  firmware: props.device?.firmware ?? '',
  recvIp: props.device?.recvIp ?? '',
  transport: props.device?.transport ?? 'UDP',
  charset: props.device?.charset ?? 'GB2312',
  expires: props.device?.expires ?? 3600,
  subscribeCycleForCatalog: props.device?.subscribeCycleForCatalog ?? 0,
})

const form = reactive<FormState>(buildForm())
const channels = ref<GbDeviceChannelDraft[]>([])

watch(
  () => props.device?.id,
  () => {
    Object.assign(form, buildForm())
    channels.value = []
    errors.deviceId = ''
    errors.name = ''
  },
)

const deviceName = computed(
  () => props.device?.name || props.device?.reportName || props.device?.deviceId || '',
)

const statusText = computed(() => {
  const s = props.device?.status
  if (s === 1) return '在线'
  if (s === 2) return '未注册'
  return '离线'
})

const SOURCE_TEXT: Record<number, string> = {
  1: '系统新增',
  2: '表格导入',
  3: '设备注册',
  4: '级联平台(历史)',
}
const sourceText = (source?: number) => (source == null ? '—' : SOURCE_TEXT[source] ?? '—')

/* 通道增删 */
const addChannel = () => channels.value.push({ channelId: '', name: '' })
const removeChannel = (i: number) => channels.value.splice(i, 1)

const errors = reactive({ deviceId: '', name: '' })
const channelErrors = ref<Record<number, string>>({})

const validateDevice = () => {
  errors.deviceId = ''
  errors.name = ''
  let ok = true
  const id = form.deviceId.trim()
  if (!id) {
    errors.deviceId = '请输入设备国标编码'
    ok = false
  } else if (!/^\d{20}$/.test(id)) {
    errors.deviceId = '设备国标编码为 20 位数字'
    ok = false
  }
  if (!form.name.trim()) {
    errors.name = '请输入设备名称'
    ok = false
  }
  return ok
}

const validateChannels = () => {
  channelErrors.value = {}
  let ok = true
  channels.value.forEach((ch, i) => {
    const cid = ch.channelId.trim()
    const nm = ch.name.trim()
    if (!cid && !nm) {
      channelErrors.value[i] = '请补全通道编码和名称，或移除该通道'
      ok = false
    } else if (!/^\d{20}$/.test(cid)) {
      channelErrors.value[i] = '通道国标编码为 20 位数字'
      ok = false
    } else if (!nm) {
      channelErrors.value[i] = '请输入通道名称'
      ok = false
    }
  })
  return ok
}

const saving = ref(false)

const onSave = async () => {
  if (!validateDevice()) return
  if (props.creating && !validateChannels()) return

  saving.value = true
  try {
    if (props.creating) {
      await saveGbDeviceApi({
        device: { ...form, deviceId: form.deviceId.trim(), name: form.name.trim() },
        channels: channels.value.map((ch) => ({
          channelId: ch.channelId.trim(),
          name: ch.name.trim(),
        })),
      })
      toast.success('设备创建成功')
      emit('created')
    } else if (props.device) {
      await updateGbDeviceApi({
        id: props.device.id,
        deviceId: props.device.deviceId,
        name: form.name.trim(),
        manufacturer: form.manufacturer,
        model: form.model,
        firmware: form.firmware,
        recvIp: form.recvIp,
        transport: form.transport,
        charset: form.charset,
        expires: form.expires,
        subscribeCycleForCatalog: form.subscribeCycleForCatalog || 0,
      })
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  if (!props.device) return
  const ok = await confirm({
    title: `确定要删除设备 "${props.device.deviceId}" 的记录吗？`,
    description: '不影响实际设备，设备重新注册后会再次出现。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteGbDeviceApi(props.device.deviceId)
  toast.success('已删除')
  emit('deleted', props.device)
}
</script>
