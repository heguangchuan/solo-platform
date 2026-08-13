<script setup lang="ts">
/**
 * 历史回放弹窗（可复用组件）
 *
 * <p>调用方持有 ref，调 {@code open(deviceId, channelId, name)} 打开。</p>
 *
 * <p>交互两段式：</p>
 * <ol>
 *   <li>查询段：选时间范围 → 查 RecordInfo → 列出录像段</li>
 *   <li>播放段：点录像段 → INVITE Playback → JessibucaPlayer 播放 + 控制条（暂停/继续/倍速/跳转）</li>
 * </ol>
 *
 * <p>播放器复用 {@link JessibucaPlayer}；流媒体协议跟点播一致（WS-FLV）。</p>
 *
 * <p>与点播对齐：可切换主/子码流 + 收流模式（UDP / TCP-passive / TCP-active）。</p>
 */
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, VideoPause } from '@element-plus/icons-vue'
import type { RecordItemResponse, PlaybackResultVO } from '@/types/api'
import {
  queryRecordInfoApi,
  playbackStartApi,
  playbackStopApi,
  playbackControlApi,
} from '@/api/gb28181'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'

const visible = ref(false)
const deviceIdRef = ref('')
const channelIdRef = ref('')
const channelNameRef = ref('')

// 查询段
const timeRange = ref<[string, string] | null>(null)
const querying = ref(false)
const records = ref<RecordItemResponse[]>([])

// 播放段
const playing = ref(false)
const loadingPlay = ref(false)
const statusText = ref('')
const errorText = ref('')
const streamInfo = ref<PlaybackResultVO | null>(null)
const currentRecord = ref<RecordItemResponse | null>(null)
const playerRef = ref<InstanceType<typeof JessibucaPlayer> | null>(null)

// 控制条状态
const paused = ref(false)
const speed = ref(1)
const speedOptions = [0.25, 0.5, 1, 2, 4]
const seekSec = ref(0)
const durationSec = ref(0)

/**
 * 码流档位：value 即发给后端的码流 token，后端原样写进 INVITE 的 SDP {@code a=<token>} 行（如 a=streamnumber:1）。
 * 设备据此选主/子码流做回放；空串 = 主码流（不写 a= 行）。
 * 与点播 {@code PlayDialog} 共用一套写法（厂商 / 国标平台写法不一，前端写死枚举透传）。
 */
const STREAMS = [
  { label: '主码流', value: '' },
  { label: '子码流 (streamnumber:1)', value: 'streamnumber:1' },
  { label: '主码流 (streamnumber:0)', value: 'streamnumber:0' },
  { label: '子码流 (stream:1)', value: 'stream:1' },
  { label: '主码流 (stream:0)', value: 'stream:0' },
  { label: '子码流 (streamprofile:1)', value: 'streamprofile:1' },
  { label: '主码流 (streamprofile:0)', value: 'streamprofile:0' },
  { label: '子码流 (streamMode:sub)', value: 'streamMode:sub' },
  { label: '主码流 (streamMode:main)', value: 'streamMode:main' },
  { label: '子码流 (1)', value: '1' },
  { label: '子码流 (sub)', value: 'sub' },
]
const currentStream = ref('')

/** 收流模式：默认 TCP-passive（与点播对齐；UDP 在很多 NVR 上回放会丢包 / 不通） */
const transport = ref<'UDP' | 'TCP-passive' | 'TCP-active'>('TCP-passive')

function defaultRange(): [string, string] {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const day = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
  return [`${day} 00:00:00`, `${day} 23:59:59`]
}

async function open(deviceId: string, channelId: string, channelDbId?: string, name?: string) {
  deviceIdRef.value = deviceId
  channelIdRef.value = channelId
  channelNameRef.value = name ?? ''
  visible.value = true
  timeRange.value = defaultRange()
  records.value = []
  resetPlayState()
  // 打开即自动查一次当天录像
  await nextTick()
  await handleQuery()
}

function resetPlayState() {
  playing.value = false
  loadingPlay.value = false
  statusText.value = ''
  errorText.value = ''
  streamInfo.value = null
  currentRecord.value = null
  paused.value = false
  speed.value = 1
  seekSec.value = 0
  durationSec.value = 0
  // 每次开弹窗回到默认：码流 / 收流模式是临时偏好，关弹窗重置
  transport.value = 'TCP-passive'
  currentStream.value = ''
}

// ============ 查询 ============

async function handleQuery() {
  if (!timeRange.value) {
    ElMessage.warning('请选择时间范围')
    return
  }
  querying.value = true
  records.value = []
  try {
    const res = await queryRecordInfoApi({
      deviceId: deviceIdRef.value,
      channelId: channelIdRef.value,
      startTime: timeRange.value[0],
      endTime: timeRange.value[1],
    })
    records.value = res.data || []
    if (records.value.length === 0) {
      ElMessage.info('该时间段无录像')
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    querying.value = false
  }
}

// ============ 播放 ============

function fmtIso(iso?: string): string {
  // 设备返回 ISO-8601 "2026-06-30T08:00:00"，转成后端要的 "2026-06-30 08:00:00"
  if (!iso) return ''
  return iso.replace('T', ' ')
}

/** 录像段时间（MM-DD HH:mm:ss），开始结束都带月日，跨不跨天都一样 */
function recClock(iso?: string): string {
  return fmtIso(iso).slice(5, 19)
}

async function handlePlayRecord(row: RecordItemResponse) {
  // 切换录像段 / 码流 / 收流模式 / 刷新都走这里：先停旧会话再起新的。
  // streamId 已带码流 token 后缀，主/子切换是两条独立 ZLM 流，旧流不冲突。
  await stopBackend(true)
  playerRef.value?.stop()

  currentRecord.value = row
  loadingPlay.value = true
  statusText.value = '正在发起回放...'
  errorText.value = ''
  try {
    const res = await playbackStartApi({
      deviceId: deviceIdRef.value,
      channelId: channelIdRef.value,
      startTime: fmtIso(row.startTime),
      endTime: fmtIso(row.endTime),
      transport: transport.value,
      streamNumber: currentStream.value,
    })
    streamInfo.value = res.data
    durationSec.value = res.data.durationSec || 0
    seekSec.value = 0
    paused.value = false
    speed.value = 1
    statusText.value = ''
    playing.value = true
    const url = pickUrl(res.data)
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
    loadingPlay.value = false
  }
}

/** 码流切换：复用已选录像段，streamNumber 变 → 后端 streamId 变 → 新的 ZLM 流 */
async function handleSwitchStream(next: string) {
  if (next === currentStream.value) return
  if (!currentRecord.value) return
  currentStream.value = next
  await handlePlayRecord(currentRecord.value)
}

/** 收流模式切换：复用已选录像段，停旧起新 */
async function handleSwitchTransport(next: 'UDP' | 'TCP-passive' | 'TCP-active') {
  if (next === transport.value) return
  if (!currentRecord.value) return
  transport.value = next
  await handlePlayRecord(currentRecord.value)
}

/** 工具栏"刷新"：复用当前录像段 / 码流 / 模式，停旧起新 */
async function handleRefresh() {
  if (!currentRecord.value) return
  await handlePlayRecord(currentRecord.value)
}

function pickUrl(info: PlaybackResultVO): string | undefined {
  if (window.location.protocol === 'https:' && info.wsFlvUrl) {
    return info.wsFlvUrl.replace(/^ws:\/\//, 'wss://')
  }
  return info.wsFlvUrl || info.flvUrl
}

// ============ 控制条 ============

async function togglePause() {
  if (!streamInfo.value?.streamId) return
  try {
    await playbackControlApi({
      streamId: streamInfo.value.streamId,
      action: paused.value ? 'resume' : 'pause',
    })
    paused.value = !paused.value
    // 客户端播放器同步暂停/继续
    if (paused.value) playerRef.value?.pause()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function handleSpeed(v: number) {
  if (!streamInfo.value?.streamId) return
  try {
    await playbackControlApi({
      streamId: streamInfo.value.streamId,
      action: 'speed',
      param: String(v),
    })
    speed.value = v
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function handleSeek(v: number) {
  if (!streamInfo.value?.streamId) return
  try {
    await playbackControlApi({
      streamId: streamInfo.value.streamId,
      action: 'seek',
      param: String(Math.floor(v)),
    })
    seekSec.value = v
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

function fmtDuration(sec: number): string {
  const s = Math.max(0, Math.floor(sec))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = s % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(ss)}`
}

/**
 * 播放器抛上来的"流结束"事件：回放流被关闭/结束。
 * 只做友好提示，不自动重连；用户可重新点录像段。
 */
function onStreamEnd() {
  playing.value = false
  errorText.value = ''
  statusText.value = '回放已结束'
}

// ============ 关闭 / 清理 ============

async function stopBackend(silent: boolean) {
  if (!streamInfo.value?.streamId) return
  try {
    await playbackStopApi({ streamId: streamInfo.value.streamId })
  } catch (e) {
    if (!silent) ElMessage.warning('停止回放异常：' + (e instanceof Error ? e.message : String(e)))
  }
}

async function onDialogClose() {
  playerRef.value?.destroy()
  await stopBackend(true)
  visible.value = false
  resetPlayState()
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="'历史回放' + (channelNameRef ? ' - ' + channelNameRef : '')"
    width="1000px"
    top="6vh"
    :close-on-click-modal="false"
    :before-close="onDialogClose"
  >
    <div class="playback-body">
      <!-- 左侧：录像段列表 -->
      <div class="record-side">
        <div class="query-bar">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            :clearable="false"
            size="small"
            style="width: 100%"
          />
          <el-button
            type="primary"
            size="small"
            :loading="querying"
            style="margin-top: 8px; width: 100%"
            @click="handleQuery"
          >查询录像</el-button>
        </div>
        <el-scrollbar class="record-list" v-loading="querying">
          <div
            v-for="rec in records"
            :key="rec.startTime + rec.endTime"
            class="record-item"
            :class="{ active: currentRecord === rec }"
            @click="handlePlayRecord(rec)"
          >
            <div class="rec-range">
              <span class="rec-clock">{{ recClock(rec.startTime) }}</span>
              <span class="rec-tilde">~</span>
              <span class="rec-clock">{{ recClock(rec.endTime) }}</span>
            </div>
          </div>
          <el-empty v-if="!querying && records.length === 0" description="无录像" :image-size="60" />
        </el-scrollbar>
      </div>

      <!-- 右侧：播放器 + 控制条 -->
      <div class="player-side">
        <div v-if="errorText" class="status-bar status-error">{{ errorText }}</div>
        <div v-else-if="statusText" class="status-bar status-info">{{ statusText }}</div>

        <JessibucaPlayer
          ref="playerRef"
          height="420px"
          :video-buffer="0.3"
          :streams="STREAMS"
          :current-stream="currentStream"
          :transport="transport"
          @error="errorText = $event"
          @stream-end="onStreamEnd"
          @refresh="handleRefresh"
          @switch-stream="handleSwitchStream"
          @switch-transport="handleSwitchTransport"
        />

        <div v-if="playing" class="control-bar">
          <el-button :icon="paused ? VideoPlay : VideoPause" circle size="small" @click="togglePause" />
          <span class="ctrl-label">倍速</span>
          <el-radio-group :model-value="speed" size="small" @change="handleSpeed">
            <el-radio-button v-for="s in speedOptions" :key="s" :value="s">{{ s }}x</el-radio-button>
          </el-radio-group>
          <div class="seek-wrap">
            <el-slider
              :model-value="seekSec"
              :max="durationSec || 1"
              :format-tooltip="fmtDuration"
              size="small"
              @change="handleSeek"
            />
            <span class="duration">{{ fmtDuration(durationSec) }}</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="onDialogClose">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.playback-body {
  display: flex;
  gap: 12px;
  height: 520px;
}
.record-side {
  width: 250px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ebeef5;
  padding-right: 12px;
}
.query-bar {
  margin-bottom: 8px;
}
.record-list {
  flex: 1;
  min-height: 0;
}
.record-item {
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.record-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
}
.record-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.rec-range {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
}
.rec-clock {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.rec-tilde {
  font-size: 12px;
  color: #c0c4cc;
}
.player-side {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.status-bar {
  padding: 6px 12px;
  margin-bottom: 8px;
  border-radius: 4px;
  font-size: 13px;
}
.status-info {
  background: #ecf5ff;
  color: #409eff;
}
.status-error {
  background: #fef0f0;
  color: #f56c6c;
}
.control-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.ctrl-label {
  font-size: 13px;
  color: #606266;
}
.seek-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.seek-wrap :deep(.el-slider) {
  flex: 1;
}
.duration {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
</style>
