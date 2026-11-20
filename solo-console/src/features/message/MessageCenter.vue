<template>
  <PopoverRoot>
    <PopoverTrigger
      class="relative grid h-8 w-8 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus:outline-none"
      title="消息中心"
    >
      <Bell class="h-4 w-4" />
      <span
        v-if="store.unreadCount > 0"
        class="absolute right-1 top-1 grid min-w-[14px] place-items-center rounded-full bg-destructive px-1 text-[9px] font-semibold leading-[14px] text-destructive-foreground"
      >
        {{ store.badgeCount }}
      </span>
    </PopoverTrigger>

    <PopoverPortal>
      <PopoverContent
        align="end"
        :side-offset="8"
        class="z-50 flex w-[360px] flex-col overflow-hidden rounded-lg border border-border bg-popover p-0 text-popover-foreground shadow-xl"
      >
        <!-- 头部 -->
        <div class="flex items-center justify-between border-b border-border px-4 py-2.5">
          <span class="text-sm font-semibold">消息中心</span>
          <button
            v-if="store.unreadCount > 0"
            class="text-xs text-primary hover:underline"
            @click="store.markAllRead()"
          >
            全部已读
          </button>
        </div>

        <!-- 消息列表 -->
        <div class="max-h-[380px] overflow-y-auto py-1">
          <div
            v-if="store.messages.length === 0"
            class="grid place-items-center gap-2 py-10 text-muted-foreground"
          >
            <BellOff class="h-6 w-6" />
            <span class="text-xs">暂无消息</span>
          </div>

          <button
            v-for="msg in displayMessages"
            :key="msg.id"
            class="flex w-full items-start gap-3 px-4 py-2.5 text-left transition-colors hover:bg-accent/60"
            :class="{ 'bg-primary/5': msg.readFlag === 0 }"
            @click="openMessage(msg)"
          >
            <span
              class="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-full"
              :class="levelBg(msg.level)"
            >
              <component :is="levelIcon(msg.level)" class="h-3.5 w-3.5" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="flex items-center gap-1.5">
                <span class="truncate text-[13px] font-medium">{{ msg.title }}</span>
                <span class="h-1.5 w-1.5 shrink-0 rounded-full bg-destructive" v-if="msg.readFlag === 0" />
              </span>
              <span class="mt-0.5 block truncate text-xs text-muted-foreground">
                {{ msg.content }}
              </span>
              <span class="mt-1 block text-[11px] text-muted-foreground/80">
                {{ formatTime(msg.createTime) }}
              </span>
            </span>
          </button>
        </div>

        <!-- 底部 -->
        <button
          v-if="store.messages.length > 0"
          class="border-t border-border py-2 text-center text-xs text-primary hover:bg-accent/60"
          @click="viewAll"
        >
          查看全部消息
        </button>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  PopoverRoot,
  PopoverTrigger,
  PopoverPortal,
  PopoverContent,
} from 'reka-ui'
import {
  Bell,
  BellOff,
  Info,
  AlertTriangle,
  XCircle,
  CheckCircle2,
} from 'lucide-vue-next'
import { useMessageStore } from '@/stores/message'
import type { SysMessage } from '@/types/api'

const router = useRouter()
const store = useMessageStore()

const displayMessages = computed(() => store.messages.slice(0, 6))

const levelIcon = (level: SysMessage['level']) => {
  switch (level) {
    case 'WARNING':
      return AlertTriangle
    case 'ERROR':
      return XCircle
    case 'IMPORTANT':
      return CheckCircle2
    default:
      return Info
  }
}

const levelBg = (level: SysMessage['level']) => {
  switch (level) {
    case 'WARNING':
      return 'bg-[var(--warning)]/12 text-[var(--warning)]'
    case 'ERROR':
      return 'bg-destructive/12 text-destructive'
    case 'IMPORTANT':
      return 'bg-[var(--success)]/12 text-[var(--success)]'
    default:
      return 'bg-primary/12 text-primary'
  }
}

const formatTime = (time: string) => {
  const date = new Date(time)
  const diff = Date.now() - date.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`
  return time.split(' ')[0]
}

const openMessage = (msg: SysMessage) => {
  if (msg.readFlag === 0) store.markRead(msg.id)
  if (msg.route) router.push(msg.route)
}

const viewAll = () => {
  router.push('/messages')
}
</script>
