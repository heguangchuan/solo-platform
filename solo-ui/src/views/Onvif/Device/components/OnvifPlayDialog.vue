<script setup lang="ts">
/**
 * ONVIF 实时点播弹窗
 * <p>调用方持有 ref，调 {@code open(device)} 打开。start → wsFlvUrl → Jessibuca；关窗 stop + destroy。</p>
 */
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { OnvifDevice, OnvifPlayResultVO } from '@/types/api'
import { startOnvifPlayApi, stopOnvifPlayApi } from '@/api/onvif'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'

const visible = ref(false)
const loading = ref(false)
const statusText = ref('')
const errorText = ref('')
const titleText = ref('')

const currentDeviceId = ref<string | number | null>(null)
const streamInfo = ref<OnvifPlayResultVO | null>(null)
const playerRef = ref<InstanceType<typeof JessibucaPlayer> | null>(null)

async function open(device: OnvifDevice) {
  currentDeviceId.value = device.id
  titleText.value = device.name || `${device.host}:${device.port}`
  visible.value = true
  errorText.value = ''
  statusText.value = ''
  streamInfo.value = null
  await nextTick()
  await startPlay()
}

async function startPlay() {
  if (!currentDeviceId.value) return
  loading.value = true
  statusText.value = '正在发起点播...'
  errorText.value = ''
  try {
    const res = await startOnvifPlayApi({ deviceId: currentDeviceId.value })
    streamInfo.value = res.data
    statusText.value = ''
    const url = pickUrl(res.data)
    if (!url) {
      errorText.value = '后端未返回可用的 WS-FLV / HTTP-FLV 地址'
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

function pickUrl(info: OnvifPlayResultVO): string | undefined {
  if (window.location.protocol === 'https:' && info.wsFlvUrl) {
    return info.wsFlvUrl.replace(/^ws:\/\//, 'wss://')
  }
  return info.wsFlvUrl || info.flvUrl
}

async function handleRefresh() {
  await stopBackend(true)
  playerRef.value?.stop()
  streamInfo.value = null
  await startPlay()
}

function onStreamEnd() {
  errorText.value = ''
  statusText.value = '视频流已结束'
}

async function stopBackend(silent: boolean) {
  if (!streamInfo.value?.streamId) return
  try {
    await stopOnvifPlayApi({ streamId: streamInfo.value.streamId })
  } catch (e) {
    if (!silent) {
      ElMessage.warning('停止点播异常：' + (e instanceof Error ? e.message : String(e)))
    }
  }
}

async function onDialogClose() {
  playerRef.value?.destroy()
  await stopBackend(true)
  visible.value = false
  streamInfo.value = null
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="'实时点播 - ' + titleText"
    width="900px"
    top="6vh"
    :close-on-click-modal="false"
    :before-close="onDialogClose"
  >
    <div v-if="errorText" class="status-bar status-error">
      <span>{{ errorText }}</span>
      <el-button v-if="!loading" size="small" @click="startPlay">重新点播</el-button>
    </div>
    <div v-else-if="statusText" class="status-bar status-info">{{ statusText }}</div>

    <JessibucaPlayer
      ref="playerRef"
      height="480px"
      @error="errorText = $event"
      @stream-end="onStreamEnd"
      @refresh="handleRefresh"
    />

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
</style>
