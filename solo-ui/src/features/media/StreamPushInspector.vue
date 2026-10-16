<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconBroadcast class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ push?.app }}/{{ push?.stream }}
        </div>
        <div class="text-xs text-muted-foreground">推流详情（只读）</div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 流信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">流信息</h4>
      <div class="space-y-2 text-xs">
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">应用名</span>
          <span class="truncate font-mono">{{ push.app }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">流标识</span>
          <span class="truncate font-mono">{{ push.stream }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">推流类型</span>
          <Badge variant="secondary">{{ originTypeText(push.originType) }}</Badge>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">流协议</span>
          <span class="font-mono uppercase">{{ push.streamSchema || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">虚拟主机</span>
          <span class="truncate font-mono">{{ push.vhost || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">目标节点</span>
          <span class="truncate font-mono">{{ push.mediaServerId || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">推流方</span>
          <span class="truncate font-mono">{{ push.clientId || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">已持续</span>
          <span>{{ formatDuration(push.aliveSecond) }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">开始时间</span>
          <span class="font-mono">{{ push.pushTime || '—' }}</span>
        </div>
      </div>
    </section>

    <!-- 操作 -->
    <div class="my-4 h-px bg-border" />

    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">操作</h4>
      <div class="flex gap-2">
        <Button size="sm" class="flex-1" @click="emit('play', push)">
          <IconPlayerPlay class="size-3.5" />
          播放
        </Button>
        <Button variant="outline" size="sm" class="flex-1" @click="onStop">
          <IconPlayerStop class="size-3.5" />
          强制中止
        </Button>
      </div>
    </section>

    <!-- 危险操作 -->
    <div class="my-4 h-px bg-border" />

    <section>
      <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
      <Button
        v-access="PERM.MEDIA_STREAM_PUSH_DELETE"
        variant="destructive"
        size="sm"
        class="w-full"
        @click="onDelete"
      >
        <IconTrash class="h-3.5 w-3.5" />
        删除该推流记录
      </Button>
    </section>
  </div>
</template>

<script setup lang="ts">
import {
  IconX,
  IconTrash,
  IconPlayerPlay,
  IconPlayerStop,
  IconBroadcast,
} from '@tabler/icons-vue'
import type { StreamPush } from '@/types/api'
import { stopStreamPushApi, deleteStreamPushApi } from '@/api/media'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Badge from '@/components/ui/badge/Badge.vue'

const props = defineProps<{ push: StreamPush }>()
const emit = defineEmits<{
  close: []
  play: [push: StreamPush]
  stopped: []
  deleted: [push: StreamPush]
}>()

const ORIGIN_TYPE_MAP: Record<string, string> = {
  rtmp: 'RTMP 推流',
  rtsp: 'RTSP 推流',
  rtc: 'WebRTC 推流',
}
const originTypeText = (t?: string) =>
  !t ? '—' : (ORIGIN_TYPE_MAP[t] ?? t.toUpperCase())

const formatDuration = (sec?: number) => {
  if (sec === undefined || sec === null) return '—'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  if (h) return `${h} 小时 ${m} 分`
  if (m) return `${m} 分 ${s} 秒`
  return `${s} 秒`
}

const onStop = async () => {
  const ok = await confirm({
    title: '强制中止推流',
    description: `确认中止「${props.push.app}/${props.push.stream}」？推流方将立即断开连接。`,
    confirmText: '中止',
    danger: true,
  })
  if (!ok) return
  await stopStreamPushApi(props.push.id)
  toast.success('已通知 ZLM 关流')
  emit('stopped')
}

const onDelete = async () => {
  const ok = await confirm({
    title: '删除推流记录',
    description: `确认删除「${props.push.app}/${props.push.stream}」记录？仍在推流的行将跳过。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteStreamPushApi(props.push.id)
  toast.success('已删除')
  emit('deleted', props.push)
}
</script>
