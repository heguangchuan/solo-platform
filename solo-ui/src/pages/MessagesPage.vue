<template>
  <div class="flex h-full gap-4 p-4">
    <!-- 消息列表 -->
    <section class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <header class="flex items-center justify-between border-b border-border px-4 py-3">
        <div>
          <h1 class="text-base font-semibold">全部消息</h1>
          <p class="mt-0.5 text-xs text-muted-foreground">
            共 {{ store.messages.length }} 条，{{ store.unreadCount }} 条未读
          </p>
        </div>
        <div class="flex items-center gap-2">
          <Button size="sm" variant="outline" :disabled="store.unreadCount === 0" @click="store.markAllRead()">
            <CheckCheck class="h-3.5" />
            全部已读
          </Button>
        </div>
      </header>

      <!-- 级别筛选 -->
      <div class="flex items-center gap-1 border-b border-border px-4 py-2">
        <button
          v-for="f in filters"
          :key="f.value"
          class="rounded-md px-2.5 py-1 text-xs transition-colors"
          :class="activeFilter === f.value
            ? 'bg-primary/12 font-medium text-primary'
            : 'text-muted-foreground hover:bg-accent'"
          @click="activeFilter = f.value"
        >
          {{ f.label }}
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-2">
        <div
          v-if="filtered.length === 0"
          class="grid h-full place-items-center gap-2 text-muted-foreground"
        >
          <BellOff class="h-8 w-8" />
          <span class="text-sm">没有匹配的消息</span>
        </div>

        <button
          v-for="msg in filtered"
          :key="msg.id"
          class="mb-1 flex w-full items-start gap-3 rounded-lg px-3 py-3 text-left transition-colors hover:bg-accent/50"
          :class="{
            'bg-primary/5': msg.readFlag === 0,
            'ring-1 ring-primary/20': selected?.id === msg.id,
          }"
          @click="onSelect(msg)"
        >
          <span
            class="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-full"
            :class="levelBg(msg.level)"
          >
            <component :is="levelIcon(msg.level)" class="h-4 w-4" />
          </span>
          <span class="min-w-0 flex-1">
            <span class="flex items-center gap-2">
              <span class="truncate text-sm font-medium">{{ msg.title }}</span>
              <Badge variant="outline" class="text-[10px]">{{ levelLabel(msg.level) }}</Badge>
            </span>
            <span class="mt-1 block text-xs text-muted-foreground">
              {{ msg.content }}
            </span>
            <span class="mt-1.5 block text-[11px] text-muted-foreground/70">
              {{ msg.createTime }}
            </span>
          </span>
          <span v-if="msg.readFlag === 0" class="mt-1 h-2 w-2 shrink-0 rounded-full bg-destructive" />
        </button>
      </div>
    </section>

    <!-- 检视器 -->
    <Transition name="slide-fade">
      <aside
        v-if="selected"
        class="flex w-[340px] flex-none flex-col overflow-y-auto rounded-xl border border-border bg-card p-5"
      >
        <div class="flex items-start justify-between">
          <span
            class="grid h-10 w-10 place-items-center rounded-full"
            :class="levelBg(selected.level)"
          >
            <component :is="levelIcon(selected.level)" class="h-5 w-5" />
          </span>
          <Button variant="ghost" size="icon-sm" @click="selected = null">
            <X class="h-4" />
          </Button>
        </div>

        <h2 class="mt-4 text-base font-semibold">{{ selected.title }}</h2>
        <div class="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
          <Badge variant="secondary">{{ typeLabel(selected.type) }}</Badge>
          <Badge variant="outline">{{ levelLabel(selected.level) }}</Badge>
        </div>

        <div class="my-4 h-px bg-border" />

        <p class="whitespace-pre-wrap text-sm leading-relaxed text-foreground/90">
          {{ selected.content }}
        </p>

        <div class="my-4 h-px bg-border" />

        <dl class="space-y-2.5 text-xs">
          <div class="flex justify-between">
            <dt class="text-muted-foreground">接收账号</dt>
            <dd class="font-mono">{{ selected.username }}</dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-muted-foreground">接收时间</dt>
            <dd>{{ selected.createTime }}</dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-muted-foreground">阅读状态</dt>
            <dd>{{ selected.readFlag === 0 ? '未读' : '已读' }}</dd>
          </div>
        </dl>

        <div class="flex-1" />

        <Button
          v-if="selected.route"
          variant="outline"
          size="sm"
          class="mt-5 w-full"
          @click="goRoute"
        >
          <ExternalLink class="h-3.5" />
          查看关联页面
        </Button>
      </aside>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BellOff,
  X,
  Info,
  AlertTriangle,
  XCircle,
  CheckCircle2,
  CheckCheck,
  ExternalLink,
} from 'lucide-vue-next'
import { useMessageStore } from '@/stores/message'
import Button from '@/components/ui/button/Button.vue'
import { Badge } from '@/components/ui/badge'
import type { SysMessage } from '@/types/api'

const router = useRouter()
const store = useMessageStore()

const selected = ref<SysMessage | null>(null)
const activeFilter = ref<'ALL' | SysMessage['level']>('ALL')

const filters: { label: string; value: 'ALL' | SysMessage['level'] }[] = [
  { label: '全部', value: 'ALL' },
  { label: '普通', value: 'INFO' },
  { label: '重要', value: 'IMPORTANT' },
  { label: '警告', value: 'WARNING' },
  { label: '错误', value: 'ERROR' },
]

const filtered = computed(() =>
  activeFilter.value === 'ALL'
    ? store.messages
    : store.messages.filter((m) => m.level === activeFilter.value)
)

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

const levelLabel = (level: SysMessage['level']) => {
  return { INFO: '普通', IMPORTANT: '重要', WARNING: '警告', ERROR: '错误' }[level]
}

const typeLabel = (type: SysMessage['type']) => {
  return (
    {
      SYSTEM: '系统消息',
      NOTICE: '通知公告',
      REMINDER: '提醒',
      BROADCAST: '广播',
      DEVICE_ALARM: '设备报警',
    }[type] ?? type
  )
}

const onSelect = (msg: SysMessage) => {
  selected.value = msg
  if (msg.readFlag === 0) store.markRead(msg.id)
}

const goRoute = () => {
  if (selected.value?.route) router.push(selected.value.route)
}
</script>

<style scoped>
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.18s ease;
}
.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>
