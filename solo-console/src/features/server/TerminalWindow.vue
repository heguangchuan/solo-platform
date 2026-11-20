<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import {
  IconX,
  IconAlertTriangle,
  IconClearAll,
  IconRefresh,
  IconPlugConnectedX,
  IconChevronRight,
  IconFolder,
} from '@tabler/icons-vue'
import type { SysServer } from '@/types/api'
import type { TerminalStatusPayload } from '@/features/terminal/XTerminal.vue'
import XTerminal from '@/features/terminal/XTerminal.vue'
import ServerFilePanel from './ServerFilePanel.vue'
import { useAuthStore } from '@/stores/auth'
import Button from '@/components/ui/button/Button.vue'

const props = defineProps<{ server: SysServer }>()
const emit = defineEmits<{ close: [] }>()

const authStore = useAuthStore()

const wsUrl = computed(() => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/api/ws/terminal/${props.server.id}?token=${encodeURIComponent(authStore.token)}`
})

/* 重连：更换 key 强制重建 XTerminal，autoConnect 自动发起新连接 */
const terminalKey = ref(0)
const status = ref<TerminalStatusPayload>({ status: 'connecting' })

const onStatus = (payload: TerminalStatusPayload) => {
  status.value = payload
}

const xtermRef = ref<InstanceType<typeof XTerminal>>()

const onClear = () => xtermRef.value?.clear()
const onReconnect = () => {
  terminalKey.value++
  status.value = { status: 'connecting' }
}
const onDisconnect = () => xtermRef.value?.disconnect()

const onClose = () => {
  xtermRef.value?.disconnect()
  emit('close')
}

/* 文件面板折叠 */
const collapsed = ref(false)
const fitAfterToggle = async () => {
  await nextTick()
  setTimeout(() => xtermRef.value?.fit(), 120)
}
const onCollapse = () => {
  collapsed.value = true
  fitAfterToggle()
}
const onExpand = () => {
  collapsed.value = false
  fitAfterToggle()
}

const statusMeta = computed(() => {
  switch (status.value.status) {
    case 'connected':
      return { label: '已连接', cls: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30' }
    case 'connecting':
      return { label: '连接中', cls: 'bg-amber-500/15 text-amber-400 border-amber-500/30' }
    case 'error':
      return { label: '异常', cls: 'bg-red-500/15 text-red-400 border-red-500/30' }
    default:
      return { label: '已断开', cls: 'bg-zinc-500/15 text-zinc-400 border-zinc-500/30' }
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="term">
      <div
        class="fixed inset-0 z-[100] flex flex-col bg-zinc-950/95 backdrop-blur"
      >
        <!-- 头部工具条 -->
        <header
          class="flex h-12 shrink-0 items-center gap-3 border-b border-white/10 px-4 text-zinc-200"
        >
          <!-- 仿 macOS 三色点 -->
          <div class="flex items-center gap-2">
            <span class="size-3 rounded-full bg-[#ff5f57]" />
            <span class="size-3 rounded-full bg-[#febc2e]" />
            <span class="size-3 rounded-full bg-[#28c840]" />
          </div>

          <div class="ml-2 min-w-0">
            <span class="truncate text-[13px] font-medium">
              {{ server.serverName }}
              <span class="ml-1 font-mono text-xs text-zinc-400">
                {{ server.host }}:{{ server.port }}
              </span>
            </span>
          </div>

          <span
            class="inline-flex items-center gap-1 rounded border px-1.5 py-0.5 text-[10px]"
            :class="statusMeta.cls"
          >
            <IconAlertTriangle v-if="status.status === 'error'" class="h-3 w-3" />
            {{ statusMeta.label }}
          </span>

          <div class="flex items-center gap-1 text-[11px] text-amber-400/90">
            <IconAlertTriangle class="h-3.5 w-3.5" />
            真实 SSH 终端，支持任意命令，请谨慎操作
          </div>

          <div class="flex-1" />

          <Button
            variant="ghost"
            size="sm"
            class="h-7 gap-1 text-xs text-zinc-300 hover:bg-white/10 hover:text-white"
            @click="onClear"
          >
            <IconClearAll class="h-3.5 w-3.5" />
            清屏
          </Button>
          <Button
            variant="ghost"
            size="sm"
            class="h-7 gap-1 text-xs text-zinc-300 hover:bg-white/10 hover:text-white"
            @click="onReconnect"
          >
            <IconRefresh class="h-3.5 w-3.5" />
            重连
          </Button>
          <Button
            variant="ghost"
            size="sm"
            class="h-7 gap-1 text-xs text-zinc-300 hover:bg-white/10 hover:text-white"
            @click="onDisconnect"
          >
            <IconPlugConnectedX class="h-3.5 w-3.5" />
            断开
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            class="size-7 text-zinc-400 hover:bg-white/10 hover:text-white"
            title="关闭"
            @click="onClose"
          >
            <IconX class="h-4 w-4" />
          </Button>
        </header>

        <!-- 主体：文件面板 + 终端 -->
        <div class="flex min-h-0 flex-1">
          <!-- 折叠态窄轨 -->
          <div
            v-if="collapsed"
            class="flex w-9 shrink-0 flex-col items-center gap-2 border-r border-white/10 py-2"
          >
            <Button
              variant="ghost"
              size="icon-sm"
              class="size-7 text-zinc-400 hover:bg-white/10 hover:text-white"
              title="展开文件面板"
              @click="onExpand"
            >
              <IconChevronRight class="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              class="size-7 text-zinc-400 hover:bg-white/10 hover:text-white"
              title="文件"
              @click="onExpand"
            >
              <IconFolder class="h-4 w-4" />
            </Button>
          </div>

          <!-- 文件面板 -->
          <div v-else class="w-[280px] shrink-0 border-r border-white/10">
            <ServerFilePanel :server-id="server.id" @collapse="onCollapse" />
          </div>

          <!-- 终端 -->
          <div class="min-w-0 flex-1">
            <XTerminal
              :key="terminalKey"
              ref="xtermRef"
              :ws-url="wsUrl"
              @status="onStatus"
            />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.term-enter-active,
.term-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}
.term-enter-from,
.term-leave-to {
  opacity: 0;
  transform: scale(0.985);
}
</style>
