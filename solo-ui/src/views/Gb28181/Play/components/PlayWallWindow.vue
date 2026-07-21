<script setup lang="ts">
/**
 * 监控墙单个播放窗口
 */
import { ref, onUnmounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { PlayResultVO } from '@/types/api'
import { startPlayApi, stopPlayApi } from '@/api/gb28181'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'

interface Props {
  /** 是否激活（选中状态） */
  active?: boolean
}

interface Emits {
  (e: 'click'): void
  (e: 'closed', streamId: string): void
}

const props = withDefaults(defineProps<Props>(), {
  active: false,
})
const emit = defineEmits<Emits>()

const playerRef = ref<InstanceType<typeof JessibucaPlayer> | null>(null)
const loading = ref(false)
const playing = ref(false)
const channelName = ref('')
const streamId = ref<string | null>(null)
const deviceId = ref<string | null>(null)
const channelId = ref<string | null>(null)

/** 开始播放 */
async function play(device: string, channel: string, name: string) {
  if (playing.value && streamId.value) {
    // 先停止当前播放
    await stop()
  }

  loading.value = true
  try {
    const res = await startPlayApi({ deviceId: device, channelId: channel })
    const result = res.data
    streamId.value = result.streamId
    deviceId.value = device
    channelId.value = channel
    channelName.value = name

    // 选择播放URL
    let url = result.wsFlvUrl
    if (!url) {
      url = result.flvUrl
    }
    // HTTPS 环境下 ws -> wss
    if (window.location.protocol === 'https:' && url?.startsWith('ws://')) {
      url = url.replace('ws://', 'wss://')
    }

    if (url) {
      playerRef.value?.play(url)
      playing.value = true
    }
  } catch (e) {
    console.error('播放启动失败', e)
  } finally {
    loading.value = false
  }
}

/** 停止播放 */
async function stop() {
  if (!streamId.value) {
    playing.value = false
    return
  }

  try {
    await stopPlayApi({ streamId: streamId.value })
  } catch (e) {
    console.error('停止播放失败', e)
  } finally {
    playerRef.value?.destroy()
    playing.value = false
    streamId.value = null
    channelName.value = ''
  }
}

/** 刷新重新播放 */
async function refresh() {
  if (!deviceId.value || !channelId.value || !channelName.value) {
    return
  }
  await play(deviceId.value, channelId.value, channelName.value)
}

/** 处理窗口点击 */
function handleClick() {
  emit('click')
}

/** 关闭播放 */
async function handleClose() {
  const id = streamId.value
  await stop()
  emit('closed', id || '')
}

/** 流结束回调 */
function handleStreamEnd() {
  playing.value = false
}

// 组件卸载时清理
onUnmounted(async () => {
  if (streamId.value) {
    await stop()
  }
})

defineExpose({
  play,
  stop,
  refresh,
  isPlaying: playing,
})
</script>

<template>
  <div
    class="play-wall-window"
    :class="{ 'is-active': active }"
    @click="handleClick"
  >
    <div class="window-header">
      <span class="channel-name" v-if="playing">{{ channelName }}</span>
      <span class="channel-name empty" v-else>暂无播放</span>
      <div class="window-actions" v-if="playing">
        <el-button link type="primary" size="small" @click.stop="refresh">
          刷新
        </el-button>
        <el-button link type="danger" size="small" @click.stop="handleClose">
          关闭
        </el-button>
      </div>
    </div>
    <div class="player-container">
      <JessibucaPlayer
        ref="playerRef"
        height="100%"
        @stream-end="handleStreamEnd"
      />
      <el-empty v-if="!playing && !loading" description="点击左侧选择通道播放" />
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
