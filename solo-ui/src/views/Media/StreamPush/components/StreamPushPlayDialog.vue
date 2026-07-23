<script setup lang="ts">
/**
 * 推流播放弹窗
 * <p>父组件 ref 调 {@code open(row)}。推流是持久流，只做"取地址 → 播放 → 关窗销毁"，不动 hook 记录。</p>
 */
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { StreamPush, StreamPushPlayUrl } from '@/types/api'
import { getStreamPushPlayUrlsApi } from '@/api/gb28181'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'

const visible = ref(false)
const loading = ref(false)
const statusText = ref('')
const errorText = ref('')
const titleText = ref('')

const playUrls = ref<StreamPushPlayUrl | null>(null)
const playerRef = ref<InstanceType<typeof JessibucaPlayer> | null>(null)

async function open(row: StreamPush) {
  if (row.pushing !== 1) {
    ElMessage.warning('该推流已断开，无法播放')
    return
  }
  titleText.value = `${row.app}/${row.stream}`
  visible.value = true
  errorText.value = ''
  statusText.value = ''
  playUrls.value = null
  await nextTick()
  await loadAndPlay(row.id)
}

async function loadAndPlay(id: string) {
  loading.value = true
  statusText.value = '获取播放地址...'
  try {
    const res = await getStreamPushPlayUrlsApi(id)
    playUrls.value = res.data
    const url = pickUrl(res.data)
    if (!url) {
      errorText.value = '未获取到可用的 WS-FLV / HTTP-FLV 地址'
      statusText.value = ''
      return
    }
    statusText.value = ''
    await nextTick()
    playerRef.value?.play(url)
  } catch (e) {
    errorText.value = e instanceof Error ? e.message : String(e)
    statusText.value = ''
  } finally {
    loading.value = false
  }
}

function pickUrl(info: StreamPushPlayUrl): string | undefined {
  if (window.location.protocol === 'https:' && info.wsFlvUrl) {
    return info.wsFlvUrl.replace(/^ws:\/\//, 'wss://')
  }
  return info.wsFlvUrl || info.flvUrl
}

function onStreamEnd() {
  errorText.value = ''
  statusText.value = '视频流已结束'
}

async function handleRefresh() {
  playerRef.value?.stop()
  if (playUrls.value) {
    const url = pickUrl(playUrls.value)
    if (url) {
      await nextTick()
      playerRef.value?.play(url)
    }
  }
}

function onDialogClose() {
  playerRef.value?.destroy()
  visible.value = false
  playUrls.value = null
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="'播放 - ' + titleText"
    width="900px"
    top="6vh"
    :close-on-click-modal="false"
    :before-close="onDialogClose"
  >
    <div v-if="errorText" class="status-bar status-error">
      <span>{{ errorText }}</span>
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
}
.status-info { background: #ecf5ff; color: #409eff; }
.status-error { background: #fef0f0; color: #f56c6c; }
</style>
