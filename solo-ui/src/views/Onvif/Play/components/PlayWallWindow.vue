<script setup lang="ts">
/**
 * ONVIF 监控墙单个播放窗口
 * <p>与国标 Play/PlayWallWindow.vue 结构一致，只是走 ONVIF 的 start/stop 接口。</p>
 */
import { onUnmounted, ref } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { OnvifDevice } from '@/types/api'
import { startOnvifPlayApi, stopOnvifPlayApi } from '@/api/onvif'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'

interface Props {
  active?: boolean
}

interface Emits {
  (e: 'click'): void
  (e: 'closed', streamId: string): void
}

const props = withDefaults(defineProps<Props>(), { active: false })
const emit = defineEmits<Emits>()

const playerRef = ref<InstanceType<typeof JessibucaPlayer> | null>(null)
const loading = ref(false)
const playing = ref(false)
const deviceLabel = ref('')
const streamId = ref<string | null>(null)
const lastDevice = ref<OnvifDevice | null>(null)

async function play(device: OnvifDevice) {
  if (playing.value && streamId.value) {
    await stop()
  }

  loading.value = true
  try {
    const res = await startOnvifPlayApi({ deviceId: device.id })
    const result = res.data
    streamId.value = result.streamId
    lastDevice.value = device
    deviceLabel.value = device.name || `${device.host}:${device.port}`

    let url = result.wsFlvUrl || result.flvUrl
    if (window.location.protocol === 'https:' && url?.startsWith('ws://')) {
      url = url.replace('ws://', 'wss://')
    }
    if (url) {
      playerRef.value?.play(url)
      playing.value = true
    }
  } catch (e) {
    console.error('ONVIF 播放启动失败', e)
  } finally {
    loading.value = false
  }
}

async function stop() {
  if (!streamId.value) {
    playing.value = false
    return
  }
  try {
    await stopOnvifPlayApi({ streamId: streamId.value })
  } catch (e) {
    console.error('停止播放失败', e)
  } finally {
    playerRef.value?.destroy()
    playing.value = false
    streamId.value = null
    deviceLabel.value = ''
  }
}

async function refresh() {
  if (!lastDevice.value) return
  await play(lastDevice.value)
}

function handleClick() {
  emit('click')
}

async function handleClose() {
  const id = streamId.value
  await stop()
  emit('closed', id || '')
}

function handleStreamEnd() {
  playing.value = false
}

onUnmounted(async () => {
  if (streamId.value) await stop()
})

defineExpose({
  play,
  stop,
  refresh,
  get isPlaying() {
    return playing.value
  },
})
</script>

<template>
  <div class="play-wall-window" :class="{ 'is-active': active }" @click="handleClick">
    <div class="window-header">
      <span v-if="playing" class="channel-name">{{ deviceLabel }}</span>
      <span v-else class="channel-name empty">暂无播放</span>
      <div v-if="playing" class="window-actions">
        <el-button link type="primary" size="small" @click.stop="refresh">刷新</el-button>
        <el-button link type="danger" size="small" @click.stop="handleClose">关闭</el-button>
      </div>
    </div>
    <div class="player-container">
      <JessibucaPlayer ref="playerRef" height="100%" @stream-end="handleStreamEnd" />
      <el-empty v-if="!playing && !loading" description="点击左侧选择设备播放" />
      <div v-if="loading" class="loading-overlay">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
    </div>
  </div>
</template>

<style scoped>
.play-wall-window {
  border: 2px solid transparent;
  border-radius: 4px;
  overflow: hidden;
  background: #1e1e1e;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  transition: border-color 0.3s;
}

.play-wall-window.is-active {
  border-color: #409eff;
}

.window-header {
  height: 36px;
  background: #2d2d2d;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  flex-shrink: 0;
}

.channel-name {
  color: #fff;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.channel-name.empty {
  color: #888;
}

.window-actions {
  display: flex;
  gap: 4px;
}

.player-container {
  flex: 1;
  position: relative;
  min-height: 0;
  width: 100%;
  background: #1e1e1e;
}

.player-container :deep(.jessibuca-box) {
  width: 100%;
  height: 100%;
  display: block;
}

.player-container :deep(.el-empty) {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  padding: 0;
  background: transparent;
}

.player-container :deep(.el-empty__image) {
  width: 32px;
  height: 32px;
}

.player-container :deep(.el-empty__image svg) {
  width: 100%;
  height: 100%;
  fill: #666;
}

.player-container :deep(.el-empty__description) {
  padding: 4px 0;
}

.player-container :deep(.el-empty__description p) {
  font-size: 12px;
  color: #888;
  margin: 0;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
}

.loading-overlay :deep(.el-icon) {
  font-size: 32px;
  color: #fff;
}
</style>
