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
        <div class="text-xs text-muted-foreground">录入国标设备</div>
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

      <!-- 同步通道目录 -->
      <section v-if="!creating && device" class="mt-4 rounded-md border bg-muted/20 p-3">
        <div class="mb-2 flex items-center justify-between">
          <h4 class="text-[13px] font-medium">通道目录</h4>
          <div class="flex items-center gap-1">
            <!-- 同步按钮（纯图标） -->
            <Button
              v-access="PERM.GB_DEVICE_SYNC"
              variant="outline"
              size="icon-sm"
              class="h-7 w-7"
              title="同步通道目录"
              :disabled="syncing || syncStatus?.status === 'RUNNING' || device.status !== 1"
              :loading="syncing"
              @click="onSync"
            >
              <IconRefresh class="size-3.5" />
            </Button>
            <!-- 同步中：旁边显示取消按钮 -->
            <Button
              v-if="syncStatus?.status === 'RUNNING'"
              v-access="PERM.GB_DEVICE_SYNC"
              variant="ghost"
              size="icon-sm"
              class="h-7 w-7"
              title="取消同步"
              :disabled="cancelling"
              @click="onCancelSync"
            >
              <IconPlayerStopFilled class="size-4" />
            </Button>
          </div>
        </div>

        <div v-if="syncStatus && syncStatus.status !== 'IDLE'" class="space-y-3">
          <div class="flex items-center gap-2 text-xs">
            <component :is="syncStateIcon" class="size-4 shrink-0" :class="syncStateIconClass" />
            <span class="shrink-0 text-muted-foreground">{{ syncStateText }}</span>
            <span class="ml-auto font-mono text-[11px] text-muted-foreground">
              已同步 {{ syncStatus.received }} 项
            </span>
          </div>

          <div class="relative h-4 w-full overflow-hidden rounded bg-muted">
            <div
              class="absolute inset-y-0 left-0 rounded bg-primary/70 transition-all duration-500"
              :style="{ width: syncPercent + '%' }"
            />
            <span class="absolute inset-0 flex items-center justify-center text-[10px] font-medium">
              {{ syncProgressText }}
            </span>
          </div>

          <div class="grid grid-cols-2 gap-x-4 gap-y-1.5 text-[11px] text-muted-foreground">
            <div class="flex items-center justify-between">
              <span>设备通告总数</span>
              <span class="font-mono">{{ syncStatus.total > 0 ? syncStatus.total + ' 项' : '未通告' }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span>重传丢弃</span>
              <span class="font-mono">{{ syncStatus.duplicateCount }} 项</span>
            </div>
            <div class="flex items-center justify-between">
              <span>最后更新</span>
              <span class="font-mono">
                {{ syncStatus.lastUpdateAtMs ? new Date(syncStatus.lastUpdateAtMs).toLocaleTimeString() : '—' }}
              </span>
            </div>
          </div>

          <p
            v-if="syncStatus.status === 'FAILED'"
            class="rounded-md border border-amber-500/40 bg-amber-500/10 p-2 text-[11px] text-amber-700 dark:text-amber-400"
          >
            {{ syncStatus.errorMsg || '未收齐设备通告的全部通道，已入库部分可再次同步补齐。' }}
          </p>
          <p
            v-else-if="syncStatus.status === 'SUCCESS'"
            class="rounded-md border border-emerald-500/40 bg-emerald-500/10 p-2 text-[11px] text-emerald-700 dark:text-emerald-400"
          >
            同步完成，共写入 {{ syncStatus.received }} 项。
          </p>
        </div>

        <p v-else class="text-[11px] text-muted-foreground">
          手动拉取该设备最新通道目录；设备在线时可执行。
        </p>
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
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import {
  IconDeviceCctv,
  IconX,
  IconTrash,
  IconInfoCircle,
  IconRefresh,
  IconCircleCheck,
  IconAlertTriangle,
  IconPlayerStopFilled,
} from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  saveGbDeviceApi,
  updateGbDeviceApi,
  deleteGbDeviceApi,
  syncGbDeviceApi,
  cancelGbDeviceSyncApi,
  getDeviceSyncStatusApi,
  parseSyncStatus,
} from '@/api/gb28181'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import type { GbDevice } from '@/types/api'
import type { GbSyncStatus } from '@/types/api'

const props = defineProps<{
  device?: GbDevice
  creating?: boolean
}>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [device: GbDevice]
  synced: [deviceId: string]
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

watch(
  () => props.device?.id,
  () => {
    Object.assign(form, buildForm())
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

/* 设备校验 */
const errors = reactive({ deviceId: '', name: '' })

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

const saving = ref(false)

const onSave = async () => {
  if (!validateDevice()) return

  saving.value = true
  try {
    if (props.creating) {
      await saveGbDeviceApi({
        device: { ...form, deviceId: form.deviceId.trim(), name: form.name.trim() },
        channels: [],
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

/* 同步通道目录（内嵌进度，不使用弹窗） */
const syncing = ref(false)
const cancelling = ref(false)
const syncStatus = ref<GbSyncStatus | null>(null)

const syncStateText = computed(() => {
  switch (syncStatus.value?.status) {
    case 'RUNNING':
      return '同步中'
    case 'SUCCESS':
      return '已完成'
    case 'FAILED':
      return '部分完成'
    default:
      return '未同步'
  }
})

const syncStateIcon = computed(() => {
  switch (syncStatus.value?.status) {
    case 'RUNNING':
      return IconRefresh
    case 'SUCCESS':
      return IconCircleCheck
    case 'FAILED':
      return IconAlertTriangle
    default:
      return IconRefresh
  }
})

const syncStateIconClass = computed(() => {
  switch (syncStatus.value?.status) {
    case 'RUNNING':
      return 'animate-spin text-primary'
    case 'SUCCESS':
      return 'text-emerald-500'
    case 'FAILED':
      return 'text-amber-500'
    default:
      return 'text-muted-foreground'
  }
})

const syncPercent = computed(() => {
  const s = syncStatus.value
  if (!s || s.total <= 0) return 0
  return Math.min(100, Math.round((s.received / s.total) * 100))
})

const syncProgressText = computed(() => {
  const s = syncStatus.value
  if (!s) return '-'
  return s.total > 0 ? `${s.received} / ${s.total}` : `${s.received}`
})

let syncPollTimer: number | null = null

const stopSyncPoll = () => {
  if (syncPollTimer !== null) {
    clearInterval(syncPollTimer)
    syncPollTimer = null
  }
}

const startSyncPoll = () => {
  stopSyncPoll()
  // 服务端约 5s 更新一次进度，前端 2s 拉取即可
  syncPollTimer = window.setInterval(() => {
    if (!props.device || syncStatus.value?.status !== 'RUNNING') {
      stopSyncPoll()
      return
    }
    void loadSyncStatus(true)
  }, 2000)
}

/**
 * 读取同步进度。
 * 只调用 GET /sync-status，不会触发 SIP 同步；真正触发同步只能走 onSync 的 POST /sync。
 */
const loadSyncStatus = async (notifyOnFinished = false) => {
  const d = props.device
  if (!d) {
    syncStatus.value = null
    return
  }
  const prev = syncStatus.value?.status
  try {
    const res = await getDeviceSyncStatusApi(d.deviceId)
    const next = parseSyncStatus(res.data, d.deviceId)
    syncStatus.value = next

    const finished = next.status === 'SUCCESS' || next.status === 'FAILED'
    if (notifyOnFinished && finished && prev === 'RUNNING') {
      emit('synced', d.deviceId)
    }
    if (next.status === 'RUNNING' && syncPollTimer === null) {
      startSyncPoll()
    } else if (finished) {
      stopSyncPoll()
    }
  } catch {
    syncStatus.value = null
  }
}

const onSync = async () => {
  const d = props.device
  if (!d || d.status !== 1 || syncing.value || syncStatus.value?.status === 'RUNNING') return
  syncing.value = true
  stopSyncPoll()
  try {
    // POST /sync 是唯一触发 SIP 同步的请求
    const res = await syncGbDeviceApi(d.deviceId)
    if (res.data?.result === 'ALREADY_RUNNING') {
      toast.info('设备已有同步在进行中')
    }
    // 直接使用 POST 响应里携带的初始 status，不再多发一次 GET
    if (res.data?.status) {
      syncStatus.value = parseSyncStatus(res.data.status, d.deviceId)
      if (syncStatus.value.status === 'RUNNING') startSyncPoll()
    }
  } finally {
    syncing.value = false
  }
}

const onCancelSync = async () => {
  const d = props.device
  if (!d || cancelling.value) return
  cancelling.value = true
  stopSyncPoll()
  try {
    await cancelGbDeviceSyncApi(d.deviceId)
    syncStatus.value = null
    toast.success('已取消同步')
  } finally {
    cancelling.value = false
  }
}

watch(
  () => props.device?.deviceId,
  (deviceId) => {
    stopSyncPoll()
    syncing.value = false
    cancelling.value = false
    syncStatus.value = null
    if (deviceId) {
      // 只读已有状态，不触发同步
      void loadSyncStatus(false)
    }
  },
  { immediate: true },
)

onUnmounted(stopSyncPoll)
</script>
