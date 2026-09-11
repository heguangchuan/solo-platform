<script setup lang="ts">
/**
 * 实时点播弹窗
 * <p>调用方持有 ref，调 {@code open(deviceId, channelId, channelDbId, name)} 打开。
 * 内部：start API → 拿到 wsFlvUrl → Jessibuca.play；关闭弹窗时调 stop API + 播放器 destroy。</p>
 * <p>播放器（{@link JessibucaPlayer}）自带工具栏，包含云台控制浮层，弹窗本身保持极简。</p>
 */
import { nextTick, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { PlayResultVO } from '@/types/api'
import { startPlayApi, stopPlayApi, ptzControlApi } from '@/api/gb28181'
import {
  ptzFocusApi, ptzIrisApi, ptzWiperApi,
  getPtzPresetListApi, callPtzPresetApi, deletePtzPresetApi,
  getCruiseListApi, startCruiseApi, stopCruiseApi,
} from '@/api/gb28181'
import type { GbPtzPreset, GbPtzCruise } from '@/types/api'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'

const visible = ref(false)
const loading = ref(false)
const statusText = ref('')
const errorText = ref('')

const deviceIdRef = ref('')
const channelIdRef = ref('')
const channelNameRef = ref('')
const transport = ref<'UDP' | 'TCP-passive' | 'TCP-active'>('TCP-passive')

/**
 * 码流档位：value 即发给后端的码流 token，后端原样写进 INVite 的 SDP `a=<token>` 行（如 a=streamnumber:1），
 * 设备据此选主/子码流；streamId 后缀则用冒号转下划线的归一值。主码流传空串（不写 a= 行、兼容回放）。
 * 各厂商 / 国标平台写法不一（对齐主流开源国标平台的码流标识枚举），这里把主流写法列出，按真机点选。
 * 当前无通道码流元数据，故前端写死。
 */
const STREAMS = [
  { label: '主码流', value: '' },
  // GB/T 28181-2022 streamnumber
  { label: '子码流 (streamnumber:1)', value: 'streamnumber:1' },
  { label: '主码流 (streamnumber:0)', value: 'streamnumber:0' },
  // stream:
  { label: '子码流 (stream:1)', value: 'stream:1' },
  { label: '主码流 (stream:0)', value: 'stream:0' },
  // streamprofile:
  { label: '子码流 (streamprofile:1)', value: 'streamprofile:1' },
  { label: '主码流 (streamprofile:0)', value: 'streamprofile:0' },
  // streamMode（TP-LINK 等）
  { label: '子码流 (streamMode:sub)', value: 'streamMode:sub' },
  { label: '主码流 (streamMode:main)', value: 'streamMode:main' },
  // 纯序号 / 字面量
  { label: '子码流 (1)', value: '1' },
  { label: '子码流 (sub)', value: 'sub' },
]
const currentStream = ref('')

const streamInfo = ref<PlayResultVO | null>(null)
const playerRef = ref<InstanceType<typeof JessibucaPlayer> | null>(null)

// 预置位列表
const presetList = ref<GbPtzPreset[]>([])
const presetLoading = ref(false)

// 巡航路线列表
const cruiseList = ref<GbPtzCruise[]>([])
const activeCruiseId = ref<number | null>(null)

// PTZ 速度值
const ptzSpeeds = ref({
  move: 100,
  zoom: 5,
  focus: 5,
  iris: 5,
})

/**
 * 处理播放器工具栏抛出的云台控制事件
 */
function handlePtz(direction: string, pressing: boolean) {
  if (!deviceIdRef.value || !channelIdRef.value) return
  const command = direction.replace(/-/g, '')
  if (pressing) {
    ptzControlApi(deviceIdRef.value, channelIdRef.value, command, ptzSpeeds.value.move, ptzSpeeds.value.zoom)
  } else {
    ptzControlApi(deviceIdRef.value, channelIdRef.value, 'stop', 0, 0)
  }
}

function handleFocus(direction: 'near' | 'far' | 'stop', speed: number) {
  if (!deviceIdRef.value || !channelIdRef.value) return
  ptzFocusApi(deviceIdRef.value, channelIdRef.value, direction, speed)
}

function handleIris(direction: 'open' | 'close' | 'stop', speed: number) {
  if (!deviceIdRef.value || !channelIdRef.value) return
  ptzIrisApi(deviceIdRef.value, channelIdRef.value, direction, speed)
}

function handleWiper(action: 'on' | 'off') {
  if (!deviceIdRef.value || !channelIdRef.value) return
  ptzWiperApi(deviceIdRef.value, channelIdRef.value, action)
}

function handleSpeedChange(speeds: { move: number; zoom: number; focus: number; iris: number }) {
  ptzSpeeds.value = speeds
}

async function open(deviceId: string, channelId: string, channelDbId: string, name?: string) {
  deviceIdRef.value = deviceId
  channelIdRef.value = channelId
  channelNameRef.value = name ?? ''
  visible.value = true
  errorText.value = ''
  statusText.value = ''
  streamInfo.value = null
  currentStream.value = ''
  transport.value = 'TCP-passive'
  presetList.value = []
  cruiseList.value = []
  activeCruiseId.value = null
  await nextTick()
  // 加载预置位
  try {
    presetLoading.value = true
    const res = await getPtzPresetListApi(channelDbId)
    presetList.value = res.data || []
  } catch {
    presetList.value = []
  } finally {
    presetLoading.value = false
  }
  // 加载巡航路线
  try {
    const res = await getCruiseListApi(channelDbId)
    cruiseList.value = res.data || []
  } catch {
    cruiseList.value = []
  }
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
      streamNumber: currentStream.value,
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
 * 码流切换（独立流模型）：主/子码流各是一路独立流，可能有别人正在看，
 * 因此切换时**不 stop 旧流**，只发起新码流的点播并让本播放器切到新 URL；
 * 被放弃的旧流若无人观看，由 ZLM 按需拉流（stream-on-demand）自动回收，不会误断他人观看。
 */
async function handleSwitchStream(next: string) {
  if (next === currentStream.value) return
  currentStream.value = next
  await startPlay()
}

async function handleSwitchTransport(next: 'UDP' | 'TCP-passive' | 'TCP-active') {
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

async function handleCallPreset(preset: GbPtzPreset) {
  try {
    await callPtzPresetApi(preset.id!)
    ElMessage.success(`已调用预置位「${preset.name}」`)
  } catch {
    // 失败不打断操作
  }
}

async function handleDeletePreset(preset: GbPtzPreset) {
  try {
    await ElMessageBox.confirm(`确定要删除预置位「${preset.name}」吗？`, '删除确认', { type: 'warning' })
    await deletePtzPresetApi(String(preset.id!))
    const index = presetList.value.findIndex(p => p.id === preset.id)
    if (index >= 0) {
      presetList.value.splice(index, 1)
    }
    ElMessage.success('删除成功')
  } catch {
    // 用户取消
  }
}

async function handleStartCruise(cruise: GbPtzCruise) {
  try {
    await startCruiseApi(cruise.id!)
    activeCruiseId.value = cruise.cruiseId!
    ElMessage.success(`已开始巡航「${cruise.name}」`)
  } catch (e) {
    ElMessage.error('开始巡航失败：' + (e instanceof Error ? e.message : String(e)))
  }
}

async function handleStopCruise(cruise: GbPtzCruise) {
  try {
    await stopCruiseApi(cruise.id!)
    activeCruiseId.value = null
    ElMessage.success('已停止巡航')
  } catch (e) {
    ElMessage.error('停止巡航失败：' + (e instanceof Error ? e.message : String(e)))
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
      :streams="STREAMS"
      :current-stream="currentStream"
      :transport="transport"
      @error="errorText = $event"
      @stream-end="onStreamEnd"
      @refresh="handleRefresh"
      @switch-stream="handleSwitchStream"
      @switch-transport="handleSwitchTransport"
      @ptz="handlePtz"
      @focus="handleFocus"
      @iris="handleIris"
      @wiper="handleWiper"
      @speed-change="handleSpeedChange"
    />

    <!-- 预置位面板 -->
    <div v-if="presetList.length > 0" class="preset-panel" style="margin-top: 12px">
      <div class="preset-title">预置位</div>
      <div class="preset-list">
        <el-tag
          v-for="p in presetList"
          :key="p.id"
          class="preset-tag"
          size="small"
          closable
          @close="handleDeletePreset(p)"
          @click="handleCallPreset(p)"
        >
          {{ p.name }}
        </el-tag>
      </div>
    </div>

    <!-- 巡航面板 -->
    <div v-if="cruiseList.length > 0" class="preset-panel" style="margin-top: 12px">
      <div class="preset-title">巡航路线</div>
      <div class="cruise-list">
        <div
          v-for="c in cruiseList"
          :key="c.id"
          class="cruise-item"
          :class="{ active: activeCruiseId === c.cruiseId }"
        >
          <span class="cruise-name">{{ c.name }}</span>
          <el-button
            v-if="activeCruiseId !== c.cruiseId"
            size="small"
            type="primary"
            link
            @click="handleStartCruise(c)"
          >开始</el-button>
          <el-button
            v-else
            size="small"
            type="danger"
            link
            @click="handleStopCruise(c)"
          >停止</el-button>
        </div>
      </div>
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
.preset-panel {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 8px 12px;
}
.preset-title {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 8px;
}
.preset-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.preset-tag {
  cursor: pointer;
}
.cruise-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cruise-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  border-radius: 4px;
  background: #f5f7fa;
  font-size: 13px;
}
.cruise-item.active {
  background: #ecf5ff;
  color: #409eff;
}
.cruise-name {
  font-weight: 500;
}
</style>
