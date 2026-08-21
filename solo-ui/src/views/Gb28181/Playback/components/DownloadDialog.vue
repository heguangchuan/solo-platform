<script setup lang="ts">
/**
 * 录像下载任务弹窗
 *
 * <p>调用方持有 ref，调 {@code open(streamId)} 打开（streamId 由调用方先调
 * {@code startDownloadApi} 拿到）。</p>
 *
 * <p>交互：手动刷新状态按钮。**不轮询**：用户点 "刷新状态" 才发 GET，下载慢就慢，
 * 不打扰用户，也不浪费后端资源。READY 后 "立即下载" 走浏览器原生下载
 * （{@code window.location.href = downloadUrl}），由 MinIO 1h 预签名 URL 直接吐文件。</p>
 *
 * <p>状态机：INVITING → RECORDING → READY / FAILED / TIMEOUT。终态关闭弹窗后服务端
 * 仍保留 1 小时，期间可再次打开看 URL。</p>
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { DownloadResultVO } from '@/types/api'
import { getDownloadStatusApi, stopDownloadApi } from '@/api/gb28181'

const visible = ref(false)
const streamIdRef = ref('')
/** 录像段描述（仅展示用，"通道名 MM-DD HH:mm:ss ~ MM-DD HH:mm:ss"） */
const labelRef = ref('')
const info = ref<DownloadResultVO | null>(null)
const loading = ref(false)
const stopping = ref(false)

/** 状态文案 + 颜色（按后端 DownloadResultResponse 常量） */
const STATUS_LABEL: Record<string, { text: string; type: 'info' | 'warning' | 'success' | 'danger' }> = {
  INVITING: { text: '正在邀请设备...', type: 'info' },
  RECORDING: { text: '设备录制中，等待 ZLM 落盘...', type: 'info' },
  READY: { text: '下载已就绪', type: 'success' },
  FAILED: { text: '下载失败', type: 'danger' },
  TIMEOUT: { text: '下载超时', type: 'danger' },
  NOT_FOUND: { text: '任务不存在或已过期', type: 'warning' },
}

function statusView(s?: string) {
  if (!s) return { text: '未知', type: 'warning' as const }
  return STATUS_LABEL[s] || { text: s, type: 'warning' as const }
}

async function open(streamId: string, label: string) {
  streamIdRef.value = streamId
  labelRef.value = label
  info.value = null
  visible.value = true
  // 打开即拉一次，让用户立刻看到当前状态
  await refresh()
}

async function refresh() {
  if (!streamIdRef.value) return
  loading.value = true
  try {
    const res = await getDownloadStatusApi(streamIdRef.value)
    info.value = res.data
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

/** 立即下载：浏览器 GET 预签名 URL，原生触发文件下载 */
function downloadNow() {
  const url = info.value?.downloadUrl
  if (!url) {
    ElMessage.warning('下载链接尚未生成')
    return
  }
  window.location.href = url
}

/** 复制链接到剪贴板，方便用户用 wget / 迅雷 等下载工具 */
async function copyLink() {
  const url = info.value?.downloadUrl
  if (!url) {
    ElMessage.warning('下载链接尚未生成')
    return
  }
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.error('复制失败，请手动选中')
  }
}

/** 主动取消（发 BYE + 关 RTP 端口 + 释放 SSRC + 标 FAILED） */
async function stop() {
  if (!streamIdRef.value) return
  stopping.value = true
  try {
    await stopDownloadApi(streamIdRef.value)
    ElMessage.success('已取消下载')
    await refresh()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    stopping.value = false
  }
}

function close() {
  visible.value = false
  streamIdRef.value = ''
  info.value = null
  labelRef.value = ''
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="录像下载"
    width="520px"
    top="20vh"
    :close-on-click-modal="false"
    @close="close"
  >
    <div v-if="labelRef" class="dl-label">录像段：{{ labelRef }}</div>
    <div class="dl-meta">streamId: {{ streamIdRef }}</div>

    <div v-if="info" class="dl-status-row">
      <el-tag :type="statusView(info.status).type" effect="dark" size="large">
        {{ statusView(info.status).text }}
      </el-tag>
      <span v-if="info.durationSec" class="dl-meta">时长: {{ info.durationSec }} 秒</span>
    </div>

    <el-alert
      v-if="info?.errorMsg"
      :title="info.errorMsg"
      type="error"
      :closable="false"
      show-icon
      style="margin-top: 12px"
    />

    <div v-if="info?.status === 'READY' && info.downloadUrl" class="dl-link-box">
      <div class="dl-link-label">下载链接（MinIO 预签名，1 小时内有效）</div>
      <el-input v-model="info.downloadUrl" readonly size="small">
        <template #append>
          <el-button @click="copyLink">复制</el-button>
        </template>
      </el-input>
    </div>

    <template #footer>
      <el-button @click="close">关闭</el-button>
      <el-button
        v-if="info && (info.status === 'INVITING' || info.status === 'RECORDING')"
        type="danger"
        plain
        :loading="stopping"
        @click="stop"
      >取消下载</el-button>
      <el-button :loading="loading" @click="refresh">刷新状态</el-button>
      <el-button
        v-if="info?.status === 'READY' && info.downloadUrl"
        type="primary"
        @click="downloadNow"
      >立即下载</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dl-label {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}
.dl-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}
.dl-status-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}
.dl-link-box {
  margin-top: 16px;
}
.dl-link-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
</style>
