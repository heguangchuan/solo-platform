<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full"
        :class="priorityBgClass(alarm.alarmPriority)"
      >
        <IconBell class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ alarm.channelName || alarm.channelId || '报警记录' }}
        </div>
        <div class="truncate text-xs text-muted-foreground font-mono">
          {{ alarm.deviceId || '—' }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 报警信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">报警信息</h4>
      <div class="space-y-2 text-xs">
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">处置状态</span>
          <span class="text-xs" :class="handleStatusClass(alarm.handleStatus)">
            {{ handleStatusLabel(alarm.handleStatus) }}
          </span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">报警级别</span>
          <span class="text-xs" :class="priorityTextClass(alarm.alarmPriority)">
            {{ priorityLabel(alarm.alarmPriority) }}
          </span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">报警方式</span>
          <span class="truncate">{{ alarm.alarmMethodDesc || alarm.alarmMethod || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">报警类型</span>
          <span class="truncate">{{ alarmTypeText(alarm.alarmType) }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">通道编码</span>
          <span class="truncate font-mono">{{ alarm.channelId || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">报警时间</span>
          <span class="font-mono">{{ alarm.alarmTime || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">接收时间</span>
          <span class="font-mono">{{ alarm.receiveTime || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">经纬度</span>
          <span class="font-mono">
            <template v-if="alarm.longitude != null && alarm.latitude != null">
              {{ alarm.longitude }}, {{ alarm.latitude }}
            </template>
            <template v-else>—</template>
          </span>
        </div>
      </div>
      <div v-if="alarm.alarmDescription" class="mt-3 rounded-md bg-muted/50 px-2.5 py-2 text-xs leading-relaxed">
        {{ alarm.alarmDescription }}
      </div>
    </section>

    <!-- 处置记录（已处置过的才显示） -->
    <template v-if="alarm.handleStatus !== 0">
      <div class="my-4 h-px bg-border" />
      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">处置记录</h4>
        <div class="space-y-2 text-xs">
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">处置人</span>
            <span class="truncate">{{ alarm.handleUser || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">处置时间</span>
            <span class="font-mono">{{ alarm.handleTime || '—' }}</span>
          </div>
          <div v-if="alarm.handleRemark" class="rounded-md bg-muted/50 px-2.5 py-2 text-xs leading-relaxed">
            {{ alarm.handleRemark }}
          </div>
        </div>
      </section>
    </template>

    <!-- 操作 -->
    <div class="my-4 h-px bg-border" />
    <section class="space-y-4">
      <Button
        variant="outline"
        size="sm"
        class="w-full"
        @click="emit('handle')"
      >
        <CheckCheck class="size-3.5" />
        处置该报警
      </Button>

      <div>
        <h4 class="mb-2 text-[13px] font-medium text-destructive">危险操作</h4>
        <p class="mb-3 text-[11px] text-muted-foreground">删除报警记录后不可恢复。</p>
        <Button
          variant="destructive"
          size="sm"
          class="w-full"
          @click="emit('delete')"
        >
          <IconTrash class="size-3.5" />
          删除该报警
        </Button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { IconBell, IconX, IconTrash } from '@tabler/icons-vue'
import { CheckCheck } from 'lucide-vue-next'
import Button from '@/components/ui/button/Button.vue'
import type { GbDeviceAlarm } from '@/types/api'

defineProps<{
  alarm: GbDeviceAlarm
}>()
const emit = defineEmits<{
  close: []
  handle: []
  delete: []
}>()

const priorityLabel = (p?: number | null) => {
  if (p == null) return '—'
  return ['一级', '二级', '三级', '四级'][p - 1] ?? String(p)
}

const priorityTextClass = (p?: number | null) =>
  ({
    1: 'text-red-400',
    2: 'text-amber-400',
    3: 'text-sky-400',
    4: 'text-muted-foreground',
  })[p ?? 0] ?? 'text-muted-foreground'

const priorityBgClass = (p?: number | null) =>
  ({
    1: 'bg-red-500/10 text-red-400',
    2: 'bg-amber-500/10 text-amber-400',
    3: 'bg-sky-500/10 text-sky-400',
    4: 'bg-primary/10 text-primary',
  })[p ?? 0] ?? 'bg-primary/10 text-primary'

const handleStatusLabel = (s?: number | null) =>
  (['未处理', '已确认', '已处置', '误报'] as const)[s ?? 0] ?? '—'

const handleStatusClass = (s?: number | null) =>
  ({
    0: 'text-red-400',
    1: 'text-amber-400',
    2: 'text-emerald-400',
    3: 'text-muted-foreground',
  })[s ?? 0] ?? 'text-muted-foreground'

/** GB28181 设备报警子类型（AlarmType） */
const ALARM_TYPE_TEXT: Record<string, string> = {
  '1': '视频丢失',
  '2': '视频遮挡',
  '3': '移动侦测',
  '4': '硬盘满',
  '5': '硬盘故障',
  '6': '网线断开',
  '7': 'IP冲突',
  '8': '非法访问',
  '9': '视频信号异常',
  '10': '录像异常',
  '11': '智能分析',
}
const alarmTypeText = (t?: string | null) =>
  t == null || t === '' ? '—' : ALARM_TYPE_TEXT[t] ?? `类型${t}`
</script>
