<script setup lang="ts">
/**
 * 实时点播弹窗
 * <p>调用方持有 ref，调 {@code open(deviceId, channelId, name)} 打开。
 * 内部：start API → 拿到 wsFlvUrl → Jessibuca.play；关闭弹窗时调 stop API + 播放器 destroy。</p>
 */
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, VideoCamera } from '@element-plus/icons-vue'
import type { PlayResultVO } from '@/types/api'
import { snapPlayApi, startPlayApi, stopPlayApi } from '@/api/gb28181'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'

const visible = ref(false)
const loading = ref(false)
const statusText = ref('')
const errorText = ref('')

const deviceIdRef = ref('')
const channelIdRef = ref('')
const channelNameRef = ref('')
const transport = ref<'UDP' | 'TCP-passive'>('UDP')
const transportOptions = [
  { label: 'UDP', value: 'UDP' },
  { label: 'TCP', value: 'TCP-passive' },
] as const

const streamInfo = ref<PlayResultVO | null>(null)
const snapPreview = ref('')
const playerRef = ref<InstanceType<typeof JessibucaPlayer> | null>(null)

async function open(deviceId: string, channelId: string, name?: string) {
  deviceIdRef.value = deviceId
  channelIdRef.value = channelId
  channelNameRef.value = name ?? ''
  visible.value = true
  errorText.value = ''
  statusText.value = ''
  streamInfo.value = null
  snapPreview.value = ''
  await nextTick()
  await startPlay()
}

async function startPlay() {
  if (!deviceIdRef.value || !channelIdRef.value) return
  loading.value = true
  statusText.value = '正在发起点播...'
  errorText.value = ''
  try {
    const res = await startPlayApi({
      deviceId: deviceIdRef.value,
      channelId: channelIdRef.value,
      transport: transport.value,
    })
    streamInfo.value = res.data
    statusText.value = '流已就绪，正在解码画面...'
    const url = pickPlayUrl(res.data)
    if (!url) {
      errorText.value = '后端未返回可用的 WS-FLV 地址'
      return
    }
    await nextTick()
    playerRef.value?.play(url)
  } catch (e) {
    errorText.value = e instanceof Error ? e.message : String(e)
    statusText.value = ''
  } finally {
    loading.value = false
  }
}

function pickPlayUrl(info: PlayResultVO): string | undefined {
  if (window.location.protocol === 'https:' && info.wsFlvUrl) {
    return info.wsFlvUrl.replace(/^ws:\/\//, 'wss://')
  }
  return info.wsFlvUrl || info.flvUrl
}

async function handleSwitchTransport() {
  // 切换收流模式：先 stop 当前会话，再以新 transport 重新点播
  await handleStopBackend(false)
  playerRef.value?.stop()
  streamInfo.value = null
  await startPlay()
}

async function handleSnap() {
  if (!streamInfo.value?.streamId) {
    ElMessage.warning('流尚未就绪')
    return
  }
  try {
    const res = await snapPlayApi({ streamId: streamInfo.value.streamId })
    snapPreview.value = res.data
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function handleStopBackend(silent: boolean) {
  if (!streamInfo.value?.streamId) return
  try {
    await stopPlayApi({ streamId: streamInfo.value.streamId })
  } catch (e) {
    if (!silent) {
      ElMessage.warning('停止点播异常：' + (e instanceof Error ? e.message : String(e)))
    }
  }
}

async function onDialogClose() {
  // 1) 客户端：destroy 播放器，避免 WS 还在跑
  playerRef.value?.destroy()
  // 2) 后端：发 BYE + 释放 SSRC
  await handleStopBackend(true)
  visible.value = false
  streamInfo.value = null
  snapPreview.value = ''
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="'实时点播' + (channelNameRef ? ' - ' + channelNameRef : '')"
    width="840px"
    top="6vh"
    :close-on-click-modal="false"
    :before-close="onDialogClose"
  >
    <div v-if="errorText" class="status-bar status-error">
      <span>{{ errorText }}</span>
      <el-button v-if="!loading" size="small" @click="startPlay">重新点播</el-button>
    </div>
    <div v-else-if="statusText" class="status-bar status-info">{{ statusText }}</div>

    <JessibucaPlayer ref="playerRef" height="420px" @error="errorText = $event" />

    <div class="toolbar">
      <div class="left">
        <span class="toolbar-label">收流方式</span>
        <el-radio-group v-model="transport" size="small" :disabled="loading" @change="handleSwitchTransport">
          <el-radio-button v-for="opt in transportOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="right">
        <el-button v-permission="'gb28181:play:snap'" :icon="Camera" :disabled="!streamInfo" size="small" @click="handleSnap">截图</el-button>
        <el-button :icon="VideoCamera" type="primary" size="small" :loading="loading" @click="startPlay">重新点播</el-button>
      </div>
    </div>

    <div v-if="snapPreview" class="snap-preview">
      <div class="snap-title">最新截图</div>
      <img :src="snapPreview" alt="snap" />
    </div>

    <template #footer>
      <el-button @click="onDialogClose">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.status-bar {
  padding: 6px 12px;
  margin-bottom: 8px;
  border-radius: 4px;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.status-info {
  background: #ecf5ff;
  color: #409eff;
}
.status-error {
  background: #fef0f0;
  color: #f56c6c;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}
.toolbar .left,
.toolbar .right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.toolbar-label {
  font-size: 13px;
  color: #606266;
}

.snap-preview {
  margin-top: 12px;
}
.snap-title {
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
.snap-preview img {
  max-width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
</style>
