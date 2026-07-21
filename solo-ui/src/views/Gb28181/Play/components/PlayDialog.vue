<script setup lang="ts">
/**
 * 实时点播弹窗
 * <p>调用方持有 ref，调 {@code open(deviceId, channelId, name)} 打开。
 * 内部：start API → 拿到 wsFlvUrl → Jessibuca.play；关闭弹窗时调 stop API + 播放器 destroy。</p>
 * <p>播放器（{@link JessibucaPlayer}）自带工具栏，弹窗本身保持极简。</p>
 */
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { PlayResultVO } from '@/types/api'
import { startPlayApi, stopPlayApi } from '@/api/gb28181'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'

const visible = ref(false)
const loading = ref(false)
const statusText = ref('')
const errorText = ref('')

const deviceIdRef = ref('')
const channelIdRef = ref('')
const channelNameRef = ref('')
const transport = ref<'UDP' | 'TCP-passive'>('UDP')
const streamType = ref<'main' | 'sub'>('main')

const streamInfo = ref<PlayResultVO | null>(null)
const playerRef = ref<InstanceType<typeof JessibucaPlayer> | null>(null)

async function open(deviceId: string, channelId: string, name?: string) {
  deviceIdRef.value = deviceId
  channelIdRef.value = channelId
  channelNameRef.value = name ?? ''
  visible.value = true
  errorText.value = ''
  statusText.value = ''
  streamInfo.value = null
  streamType.value = 'main'
  transport.value = 'UDP'
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
    statusText.value = ''
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

/**
 * 播放器工具栏抛上来的"刷新"动作：先 stop 旧会话 → 再 start 新会话
 */
async function handleRefresh() {
  await handleStopBackend(true)
  playerRef.value?.stop()
  streamInfo.value = null
  await startPlay()
}

/**
 * 子码流切换 — S2.2 暂占位（INVITE SDP 还没区分主/辅）；预留事件已通；后端 S2.3 / S3 加 subStream 参数后接通
 */
function handleSwitchStream(next: 'main' | 'sub') {
  streamType.value = next
  ElMessage.info('子码流切换功能开发中（S2.3 接通）')
}

async function handleSwitchTransport(next: 'UDP' | 'TCP-passive') {
  transport.value = next
  await handleRefresh()
}

/**
 * 播放器抛上来的"流结束"事件：流被关闭/结束（设备停推、ZLM 关流等）。
 * 只做友好提示，不自动重连；用户可点工具栏"刷新"手动重来。
 */
function onStreamEnd() {
  errorText.value = ''
  statusText.value = '视频流已结束'
}

async function handleStopBackend(silent: boolean) {  if (!streamInfo.value?.streamId) return
  try {
    await stopPlayApi({ streamId: streamInfo.value.streamId })
  } catch (e) {
    if (!silent) {
      ElMessage.warning('停止点播异常：' + (e instanceof Error ? e.message : String(e)))
    }
  }
}

async function onDialogClose() {
  playerRef.value?.destroy()
  await handleStopBackend(true)
  visible.value = false
  streamInfo.value = null
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="'实时点播' + (channelNameRef ? ' - ' + channelNameRef : '')"
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
      :stream-type="streamType"
      :transport="transport"
      @error="errorText = $event"
      @stream-end="onStreamEnd"
      @refresh="handleRefresh"
      @switch-stream="handleSwitchStream"
      @switch-transport="handleSwitchTransport"
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
