<template>
  <DialogRoot :open="visible" @update:open="(v: boolean) => !v && onClose()">
    <DialogContent class="max-w-md">
      <DialogHeader>
        <DialogTitle>设备目录同步</DialogTitle>
      </DialogHeader>

      <div v-if="status" class="space-y-4">
        <!-- 状态头 -->
        <div class="flex items-center gap-2.5">
          <component
            :is="stateIcon"
            class="size-5 shrink-0"
            :class="stateIconClass"
          />
          <span class="min-w-0 flex-1 truncate text-sm font-medium">{{ deviceName }}</span>
          <Badge :variant="badgeVariant" class="shrink-0">{{ stateText }}</Badge>
        </div>

        <div class="font-mono text-[11px] text-muted-foreground">
          设备编码：{{ deviceId }}
        </div>

        <!-- 进度条 -->
        <div class="relative h-5 w-full overflow-hidden rounded bg-muted">
          <div
            class="absolute inset-y-0 left-0 rounded bg-primary/70 transition-all duration-500"
            :style="{ width: percent + '%' }"
          />
          <span class="absolute inset-0 flex items-center justify-center text-[10px] font-medium">
            {{ progressText }}
          </span>
        </div>

        <!-- 明细 -->
        <div class="grid grid-cols-2 gap-x-6 gap-y-2 rounded-md border p-3 text-xs">
          <div class="flex items-center justify-between gap-2">
            <span class="shrink-0 text-muted-foreground">已同步</span>
            <span><b>{{ status.received }}</b> 项</span>
          </div>
          <div class="flex items-center justify-between gap-2">
            <span class="shrink-0 text-muted-foreground">设备通告总数</span>
            <span>{{ status.total > 0 ? status.total + ' 项' : '未通告' }}</span>
          </div>
          <div class="flex items-center justify-between gap-2">
            <span class="shrink-0 text-muted-foreground">重传丢弃</span>
            <span>{{ status.duplicateCount }} 项</span>
          </div>
          <div class="flex items-center justify-between gap-2">
            <span class="shrink-0 text-muted-foreground">最后更新</span>
            <span class="font-mono">{{
              status.lastUpdateAtMs
                ? new Date(status.lastUpdateAtMs).toLocaleTimeString()
                : '—'
            }}</span>
          </div>
        </div>

        <!-- 提示 -->
        <p
          v-if="status.status === 'RUNNING'"
          class="rounded-md border bg-muted/50 p-2.5 text-[11px] text-muted-foreground"
        >
          同步在后端继续进行，可随时关闭本窗口，稍后重新打开查看进度。
        </p>
        <p
          v-else-if="status.status === 'FAILED'"
          class="rounded-md border border-amber-500/40 bg-amber-500/10 p-2.5 text-[11px] text-amber-700 dark:text-amber-400"
        >
          未收齐设备通告的全部通道（{{ status.errorMsg || '设备中途停止发送' }}）。已入库部分不会被清除，可再次同步补齐。
        </p>
        <p
          v-else-if="status.status === 'SUCCESS'"
          class="rounded-md border border-emerald-500/40 bg-emerald-500/10 p-2.5 text-[11px] text-emerald-700 dark:text-emerald-400"
        >
          已写入 {{ status.received }} 项<template v-if="status.duplicateCount > 0">，重传丢弃 {{ status.duplicateCount }} 项</template>。
        </p>
      </div>

      <div v-else class="flex items-center justify-center gap-2 py-10 text-xs text-muted-foreground">
        <IconRefresh class="size-4 animate-spin" />
        正在获取同步状态...
      </div>

      <DialogFooter>
        <Button variant="outline" size="sm" @click="onClose">关闭</Button>
      </DialogFooter>
    </DialogContent>
  </DialogRoot>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import {
  IconRefresh,
  IconCircleCheck,
  IconAlertTriangle,
} from '@tabler/icons-vue'
import {
  DialogRoot,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import Button from '@/components/ui/button/Button.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import { getDeviceSyncStatusApi, parseSyncStatus } from '@/api/gb28181'
import { toast } from '@/lib/toast'
import type { GbSyncStatus } from '@/types/api'

const emit = defineEmits<{
  completed: [deviceId: string]
}>()

const visible = ref(false)
const deviceId = ref('')
const deviceName = ref('')
const status = ref<GbSyncStatus | null>(null)
/** 收尾结果只提示一次 */
const notified = ref(false)

const stateText = computed(() => {
  switch (status.value?.status) {
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

const stateIcon = computed(() => {
  switch (status.value?.status) {
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

const stateIconClass = computed(() => {
  switch (status.value?.status) {
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

const badgeVariant = computed<'default' | 'secondary' | 'destructive' | 'outline'>(() => {
  switch (status.value?.status) {
    case 'RUNNING':
      return 'secondary'
    case 'SUCCESS':
      return 'default'
    case 'FAILED':
      return 'outline'
    default:
      return 'outline'
  }
})

/** 进度条长度 */
const percent = computed(() => {
  const s = status.value
  if (!s || s.total <= 0) return 0
  return Math.min(100, Math.round((s.received / s.total) * 100))
})

/** 条内文案："已同步 / 通告总数"；未通告总数时只显已同步数 */
const progressText = computed(() => {
  const s = status.value
  if (!s) return '-'
  return s.total > 0 ? `${s.received} / ${s.total}` : `${s.received}`
})

const fetchStatus = async () => {
  try {
    const res = await getDeviceSyncStatusApi(deviceId.value)
    const next = parseSyncStatus(res.data, deviceId.value)
    status.value = next

    // 收尾只 toast 一次，且不自动关窗
    if (
      (next.status === 'SUCCESS' || next.status === 'FAILED') &&
      !notified.value
    ) {
      notified.value = true
      stopPoll()
      if (next.status === 'SUCCESS') {
        toast.success(`设备 ${deviceName.value} 同步完成，共 ${next.received} 项`)
      } else {
        toast.warning(
          `设备 ${deviceName.value} 同步未收齐：已入库 ${next.received} 项` +
            (next.total > 0 ? `（设备通告 ${next.total} 项）` : ''),
        )
      }
      emit('completed', deviceId.value)
    }
  } catch {
    // 单次拉取失败忽略，下个轮询周期再试
  }
}

let pollTimer: number | null = null

const startPoll = () => {
  stopPoll()
  // 每 2s 拉一次，服务端约 5s 更新一次进度
  pollTimer = window.setInterval(() => {
    if (!visible.value) {
      stopPoll()
      return
    }
    if (status.value == null || status.value.status === 'RUNNING') {
      void fetchStatus()
    }
  }, 2000)
}

const stopPoll = () => {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const open = (id: string, name?: string) => {
  deviceId.value = id
  deviceName.value = name || id
  visible.value = true
  notified.value = false
  status.value = null
  void fetchStatus()
  startPoll()
}

const onClose = () => {
  visible.value = false
  stopPoll()
}

onUnmounted(stopPoll)

defineExpose({ open })
</script>
