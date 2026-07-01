<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Terminal } from '@xterm/xterm'
import type { ITheme } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import '@xterm/xterm/css/xterm.css'

interface TerminalStatusPayload {
  status: 'connecting' | 'connected' | 'closed' | 'error'
  reason?: string
}

const props = withDefaults(defineProps<{
  wsUrl: string
  autoConnect?: boolean
  theme?: ITheme
}>(), {
  autoConnect: true,
  theme: () => ({
    background: '#0f172a',
    foreground: '#d1fae5',
    cursor: '#93c5fd',
    selectionBackground: '#334155',
  }),
})

const emit = defineEmits<{
  status: [payload: TerminalStatusPayload]
  open: []
  close: [event?: CloseEvent]
  error: [event?: Event]
}>()

const terminalRef = ref<HTMLDivElement>()
let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null
let ws: WebSocket | null = null
let heartbeatTimer: number | null = null
let resizeObserver: ResizeObserver | null = null

function updateStatus(status: TerminalStatusPayload['status'], reason?: string) {
  emit('status', { status, reason })
}

function initTerminal() {
  if (terminal || !terminalRef.value) return
  terminal = new Terminal({
    cursorBlink: true,
    convertEol: true,
    fontFamily: "Consolas, Monaco, 'Courier New', monospace",
    fontSize: 13,
    scrollback: 3000,
    theme: props.theme,
  })
  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.loadAddon(new WebLinksAddon())
  terminal.open(terminalRef.value)
  terminal.onData((data) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'input', data }))
    }
  })
  terminal.onResize(({ cols, rows }) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'resize', cols, rows }))
    }
  })
  resizeObserver = new ResizeObserver(() => fit())
  resizeObserver.observe(terminalRef.value)
  nextTick(() => fit())
}

function connect() {
  initTerminal()
  if (!props.wsUrl) {
    terminal?.writeln('\r\n缺少 WebSocket 地址，无法连接终端')
    updateStatus('error', '缺少 WebSocket 地址')
    return
  }
  disconnect(false)
  updateStatus('connecting')
  terminal?.writeln('\r\n正在连接 Web 终端...')
  try {
    const socket = new WebSocket(props.wsUrl)
    ws = socket
    socket.onopen = () => {
      if (socket !== ws) return
      updateStatus('connected')
      emit('open')
      terminal?.writeln('\r\nWebSocket 已连接')
      startHeartbeat()
      fit()
    }
    socket.onmessage = (event) => {
      if (socket !== ws) return
      if (event.data === 'pong') return
      try {
        const message = JSON.parse(event.data)
        if (message.type === 'stdout' || message.type === 'stderr') {
          terminal?.write(message.data || '')
        } else if (message.type === 'system') {
          terminal?.writeln(`\r\n${message.data || ''}`)
        } else {
          terminal?.write(message.data || event.data)
        }
      } catch {
        terminal?.write(event.data)
      }
    }
    socket.onclose = (event) => {
      if (socket !== ws) return
      stopHeartbeat()
      ws = null
      updateStatus(event.wasClean ? 'closed' : 'error', event.reason)
      emit('close', event)
      terminal?.writeln(`\r\n连接已关闭：${event.reason || '连接断开'}`)
    }
    socket.onerror = (event) => {
      if (socket !== ws) return
      updateStatus('error')
      emit('error', event)
      terminal?.writeln('\r\n终端连接异常')
    }
  } catch (e) {
    updateStatus('error')
    terminal?.writeln(`\r\n创建 WebSocket 失败：${String(e)}`)
  }
}

function disconnect(sendClose = true) {
  stopHeartbeat()
  if (ws) {
    if (sendClose && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'close' }))
    }
    ws.close()
    ws = null
  }
  updateStatus('closed')
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = window.setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send('ping')
    }
  }, 30000)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function fit() {
  nextTick(() => {
    fitAddon?.fit()
    if (ws && ws.readyState === WebSocket.OPEN && terminal) {
      ws.send(JSON.stringify({ type: 'resize', cols: terminal.cols, rows: terminal.rows }))
    }
  })
}

function clear() {
  terminal?.clear()
}

function focus() {
  terminal?.focus()
}

onMounted(() => {
  initTerminal()
  if (props.autoConnect) connect()
})

onBeforeUnmount(() => {
  disconnect(false)
  resizeObserver?.disconnect()
  resizeObserver = null
  terminal?.dispose()
  terminal = null
  fitAddon = null
})

defineExpose({
  connect,
  disconnect,
  clear,
  fit,
  focus,
  terminal: () => terminal,
})
</script>

<template>
  <div ref="terminalRef" class="x-terminal" />
</template>

<style scoped>
.x-terminal {
  width: 100%;
  height: 100%;
  min-height: 360px;
  border-radius: 8px;
  overflow: hidden;
  background: #0f172a;
}

.x-terminal :deep(.xterm) {
  height: 100%;
  padding: 12px;
}
</style>
