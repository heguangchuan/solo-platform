<script setup lang="ts">
/**
 * 设备通道目录同步进度弹窗
 * <p>父组件持有 ref，调 {@code open(deviceId, deviceName)} 打开。
 * 内部订阅 WebSocket GB_SYNC_PROGRESS 实时更新进度，完成或失败时自动关闭。</p>
 */
import { ref, computed, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, CircleCheck, CircleClose, Refresh } from '@element-plus/icons-vue'
import { useSyncStore } from '@/stores/sync'
import type { GbSyncStatus } from '@/types/api'

interface Props {
  /** 弹窗宽度（默认 520） */
  width?: string
}
const props = withDefaults(defineProps<Props>(), { width: '520px' })

const visible = ref(false)
const deviceId = ref('')
const deviceName = ref('')
const status = ref<GbSyncStatus | null>(null)
const closing = ref(false)

const syncStore = useSyncStore()

const stateText = computed(() => {
  const s = status.value?.status
  if (s === 'RUNNING') return '同步中'
  if (s === 'SUCCESS') return '已完成'
  if (s === 'FAILED') return '失败'
  return '未同步'
})

const stateIcon = computed(() => {
  const s = status.value?.status
  if (s === 'RUNNING') return Loading
  if (s === 'SUCCESS') return CircleCheck
  if (s === 'FAILED') return CircleClose
  return Refresh
})

const stateColor = computed(() => {
  const s = status.value?.status
  if (s === 'SUCCESS') return '#67c23a'
  if (s === 'FAILED') return '#f56c6c'
  if (s === 'RUNNING') return '#409eff'
  return '#909399'
})

const percent = computed(() => Math.round(status.value?.percent ?? 0))

const elapsedText = computed(() => {
  if (!status.value || !status.value.startedAtMs) return '-'
  const start = status.value.startedAtMs
  const end = status.value.finishedAt
    ? new Date(status.value.finishedAt).getTime()
    : Date.now()
  const sec = Math.max(0, Math.floor((end - start) / 1000))
  if (sec < 60) return `${sec} 秒`
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m} 分 ${s} 秒`
})

const canClose = computed(() => {
  const s = status.value?.status
  return s === 'SUCCESS' || s === 'FAILED' || s === 'IDLE' || closing.value
})

async function open(id: string, name?: string) {
  deviceId.value = id
  deviceName.value = name || id
  visible.value = true
  closing.value = false
  // 初始化 store + 拉一次当前状态
  syncStore.init()
  await syncStore.fetch(id)
  status.value = syncStore.get(id) ?? null
  // 立即看 WebSocket 有没有更新
  startWatch()
}

function startWatch() {
  // 启动后轮询兜底：每 2s 拉一次 HTTP（WebSocket 没推到时也能更新）
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = window.setInterval(async () => {
    if (status.value?.status === 'RUNNING') {
      await syncStore.fetch(deviceId.value)
    }
  }, 2000)
}

let pollTimer: number | null = null
let watcherStop: (() => void) | null = null

// 订阅 store 的 statusByDevice 变化（WebSocket / HTTP 都会写进来）
watch(
  () => syncStore.statusByDevice[deviceId.value],
  (v) => {
    if (!v) return
    status.value = v
    // 收尾：2.5s 后自动关闭
    if ((v.status === 'SUCCESS' || v.status === 'FAILED') && !closing.value) {
      closing.value = true
      setTimeout(() => {
        visible.value = false
        if (v.status === 'SUCCESS') {
          ElMessage.success(`设备 ${deviceName.value} 同步完成，共 ${v.received} 项`)
        } else {
          ElMessage.warning(`设备 ${deviceName.value} 同步失败：${v.errorMsg || '未知原因'}`)
        }
      }, 2500)
    }
  },
  { deep: true }
)

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (watcherStop) watcherStop()
})

function handleClose() {
  if (canClose.value) {
    visible.value = false
  } else {
    ElMessage.info('同步进行中，请等待完成')
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="设备目录同步"
    :width="props.width"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="canClose"
    @close="handleClose"
  >
    <div v-if="status" class="sync-progress">
      <div class="header">
        <el-icon :size="22" :color="stateColor" :class="{ spinning: status.status === 'RUNNING' }">
          <component :is="stateIcon" />
        </el-icon>
        <span class="title">{{ deviceName }}</span>
        <el-tag :type="status.status === 'SUCCESS' ? 'success' : status.status === 'FAILED' ? 'danger' : 'primary'" size="small">
          {{ stateText }}
        </el-tag>
      </div>

      <div class="device-id">设备编码：{{ deviceId }}</div>

      <el-progress
        :percentage="percent"
        :status="status.status === 'FAILED' ? 'exception' : status.status === 'SUCCESS' ? 'success' : undefined"
        :stroke-width="14"
        :format="(p: number) => `${p}%`"
        style="margin: 20px 0"
      />

      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="已收 / 期望">
          <b>{{ status.received }}</b> / {{ status.total || '?' }}
        </el-descriptions-item>
        <el-descriptions-item label="重传丢弃">{{ status.duplicateCount }}</el-descriptions-item>
        <el-descriptions-item label="已用时间">{{ elapsedText }}</el-descriptions-item>
        <el-descriptions-item label="最后更新">
          {{ status.lastUpdateAtMs ? new Date(status.lastUpdateAtMs).toLocaleTimeString() : '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="status.status === 'FAILED' && status.errorMsg"
        :title="`同步失败：${status.errorMsg}`"
        type="error"
        :closable="false"
        style="margin-top: 16px"
      />
      <el-alert
        v-else-if="status.status === 'SUCCESS'"
        :title="`已写入 ${status.received} 项，${status.duplicateCount > 0 ? '重传丢弃 ' + status.duplicateCount + ' 项' : ''}`"
        type="success"
        :closable="false"
        style="margin-top: 16px"
      />
    </div>

    <template #footer>
      <el-button :disabled="!canClose" @click="handleClose">{{ canClose ? '关闭' : '同步中...' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.sync-progress {
  padding: 4px 4px 0;
}
.header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.header .title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.device-id {
  color: #909399;
  font-size: 12px;
  margin-bottom: 10px;
  word-break: break-all;
}
.spinning {
  animation: spin 1.4s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
