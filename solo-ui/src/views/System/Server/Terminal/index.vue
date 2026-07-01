<script lang="ts">
export default { name: 'SystemServerTerminal' }
</script>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Delete, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import XTerminal from '@/components/XTerminal.vue'

const route = useRoute()
const userStore = useUserStore()

const terminalRef = ref<InstanceType<typeof XTerminal>>()
const serverId = computed(() => String(route.query.serverId || ''))
const serverName = computed(() => String(route.query.serverName || '服务器'))
const status = ref<'connecting' | 'connected' | 'closed' | 'error'>('closed')
const statusReason = ref('')

const wsUrl = computed(() => {
  const token = userStore.token
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/api/ws/terminal/${serverId.value}?token=${encodeURIComponent(token)}`
})

const statusText = computed(() => {
  if (status.value === 'connecting') return '连接中'
  if (status.value === 'connected') return '已连接'
  if (status.value === 'error') return '连接异常'
  return '已断开'
})

const statusType = computed(() => {
  if (status.value === 'connected') return 'success'
  if (status.value === 'connecting') return 'warning'
  if (status.value === 'error') return 'danger'
  return 'info'
})

function handleStatus(payload: { status: 'connecting' | 'connected' | 'closed' | 'error'; reason?: string }) {
  status.value = payload.status
  statusReason.value = payload.reason || ''
}

function clearScreen() {
  terminalRef.value?.clear()
}

function reconnect() {
  terminalRef.value?.connect()
}

function disconnect() {
  terminalRef.value?.disconnect()
}
</script>

<template>
  <div>
    <el-card shadow="hover">
      <div class="terminal-header">
        <div>
          <div class="terminal-title">Web 终端 - {{ serverName }}</div>
          <div class="terminal-subtitle">服务器ID：{{ serverId }}</div>
        </div>
        <div class="terminal-actions">
          <el-tag :type="statusType">{{ statusText }}</el-tag>
          <span v-if="statusReason" class="status-reason">{{ statusReason }}</span>
          <el-button :icon="Delete" @click="clearScreen">清屏</el-button>
          <el-button :icon="Refresh" @click="reconnect">重连</el-button>
          <el-button type="danger" :icon="SwitchButton" @click="disconnect">断开</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="hover" class="terminal-card">
      <div class="terminal-wrapper">
        <XTerminal ref="terminalRef" :ws-url="wsUrl" @status="handleStatus" />
      </div>
    </el-card>

    <el-alert
      class="terminal-boundary"
      type="info"
      show-icon
      :closable="false"
      title="终端提示：当前为真实 SSH 终端，支持输入任意命令，请谨慎操作。"
    />
  </div>
</template>

<style scoped>
.terminal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.terminal-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.terminal-subtitle {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.terminal-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-reason {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.terminal-card {
  margin-top: 16px;
}

.terminal-wrapper {
  height: calc(100vh - 260px);
  min-height: 420px;
}

.terminal-boundary {
  margin-top: 16px;
}
</style>
