<script setup lang="ts">
/**
 * 设备通道目录同步进度弹窗
 * <p>父组件持有 ref，调 {@code open(deviceId, deviceName)} 打开。
 * 内部每 2s 轮询 GET /sync-status 拿最新进度；<b>同步中也允许关闭弹窗</b>
 * ——关闭只是不再看进度，后端同步继续跑，重新打开可接着看。</p>
 */
import { ref, computed, onUnmounted, watch } from 'vue'
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
/** 已提示过收尾结果，避免重复弹 message */
const notified = ref(false)

const syncStore = useSyncStore()

const stateText = computed(() => {
  const s = status.value?.status
  if (s === 'RUNNING') return '同步中'
  if (s === 'SUCCESS') return '已完成'
  if (s === 'FAILED') return '部分完成'
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
  if (s === 'FAILED') return '#e6a23c'
  if (s === 'RUNNING') return '#409eff'
  return '#909399'
})

/** 进度条百分比：只决定条的长度，条内文案单独显示数量 */
const percent = computed(() => {
  const s = status.value
  if (!s || !s.total || s.total <= 0) return 0
  return Math.min(100, Math.round((s.received / s.total) * 100))
})

/** 条内文案："已同步 / 通告总数"；设备未通告 total 时只显示已同步数 */
const progressText = computed(() => {
  const s = status.value
  if (!s) return '-'
  return s.total > 0 ? `${s.received} / ${s.total}` : `${s.received}`
})

function open(id: string, name?: string) {
  deviceId.value = id
  deviceName.value = name || id
  visible.value = true
  notified.value = false
  status.value = null
  void syncStore.fetch(id).then(() => {
    status.value = syncStore.get(id) ?? null
  })
  startPoll()
}

let pollTimer: number | null = null

function startPoll() {
  stopPoll()
  // 每 2s 拉一次 HTTP，server 端约 5s 更新一次 Redis
  pollTimer = window.setInterval(() => {
    if (!visible.value) {
      stopPoll()
      return
    }
    if (status.value == null || status.value.status === 'RUNNING') {
      void syncStore.fetch(deviceId.value)
    }
  }, 2000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// 订阅 store 变化（HTTP 轮询写进来）
watch(
  () => syncStore.statusByDevice[deviceId.value],
  (v) => {
    if (!v || !visible.value) return
    status.value = v
    // 收尾只提示一次，且不自动关弹窗——让用户自己看完数量再关
    if ((v.status === 'SUCCESS' || v.status === 'FAILED') && !notified.value) {
      notified.value = true
      stopPoll()
      if (v.status === 'SUCCESS') {
        ElMessage.success(`设备 ${deviceName.value} 同步完成，共 ${v.received} 项`)
      } else {
        ElMessage.warning(
          `设备 ${deviceName.value} 同步未收齐：已入库 ${v.received} 项` +
          (v.total > 0 ? `（设备通告 ${v.total} 项）` : '')
        )
      }
    }
  },
  { deep: true }
)

onUnmounted(stopPoll)

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="设备目录同步"
    :width="props.width"
    :close-on-click-modal="false"
    @closed="stopPoll"
  >
    <div v-if="status" class="sync-progress">
      <div class="header">
        <el-icon :size="22" :color="stateColor" :class="{ spinning: status.status === 'RUNNING' }">
          <component :is="stateIcon" />
        </el-icon>
        <span class="title">{{ deviceName }}</span>
        <el-tag
          :type="status.status === 'SUCCESS' ? 'success' : status.status === 'FAILED' ? 'warning' : 'primary'"
          size="small"
        >
          {{ stateText }}
        </el-tag>
      </div>

      <div class="device-id">设备编码：{{ deviceId }}</div>

      <el-progress
        :percentage="percent"
        :status="status.status === 'SUCCESS' ? 'success' : undefined"
        :stroke-width="18"
        :text-inside="true"
        :format="() => progressText"
        style="margin: 20px 0"
      />

      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="已同步">
          <b>{{ status.received }}</b> 项
        </el-descriptions-item>
        <el-descriptions-item label="设备通告总数">
          {{ status.total > 0 ? status.total + ' 项' : '未通告' }}
        </el-descriptions-item>
        <el-descriptions-item label="重传丢弃">{{ status.duplicateCount }} 项</el-descriptions-item>
        <el-descriptions-item label="最后更新">
          {{ status.lastUpdateAtMs ? new Date(status.lastUpdateAtMs).toLocaleTimeString() : '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="status.status === 'RUNNING'"
        title="同步在后端继续进行，可随时关闭本窗口，稍后重新打开查看进度"
        type="info"
        :closable="false"
        style="margin-top: 16px"
      />
      <el-alert
        v-else-if="status.status === 'FAILED'"
        :title="`未收齐设备通告的全部通道（${status.errorMsg || '设备中途停止发送'}）。已入库部分不会被清除，可再次同步补齐`"
        type="warning"
        :closable="false"
        style="margin-top: 16px"
      />
      <el-alert
        v-else-if="status.status === 'SUCCESS'"
        :title="`已写入 ${status.received} 项${status.duplicateCount > 0 ? '，重传丢弃 ' + status.duplicateCount + ' 项' : ''}`"
        type="success"
        :closable="false"
        style="margin-top: 16px"
      />
    </div>
    <el-empty v-else description="正在获取同步状态..." :image-size="60" />

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
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
