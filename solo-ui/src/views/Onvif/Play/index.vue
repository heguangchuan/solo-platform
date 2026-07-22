<script lang="ts">
export default { name: 'OnvifPlay' }
</script>
<script setup lang="ts">
/**
 * ONVIF 实时点播监控墙
 * 左侧：在线 ONVIF 设备列表（点击进空闲格子播放）
 * 右侧：可配置多窗口播放（1×1 2×2 3×3 4×4）
 *
 * 与国标 Play 结构一致，差别在于左侧用平铺设备列表（ONVIF 没有 Catalog 通道树）
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Brush, FullScreen, Grid, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { OnvifDevice } from '@/types/api'
import { getOnvifDevicePageApi } from '@/api/onvif'
import PlayWallWindow from './components/PlayWallWindow.vue'

type LayoutType = '1x1' | '2x2' | '3x3' | '4x4'

const loading = ref(false)
const devices = ref<OnvifDevice[]>([])
const currentLayout = ref<LayoutType>('2x2')
const activeWindowIndex = ref(0)
const isFullscreen = ref(false)

function getWindowCount(layout: LayoutType): number {
  return { '1x1': 1, '2x2': 4, '3x3': 9, '4x4': 16 }[layout]
}

const windowRefs = ref<Array<InstanceType<typeof PlayWallWindow> | null>>(
  Array.from({ length: getWindowCount(currentLayout.value) }, () => null)
)

function handleClearAll() {
  windowRefs.value.forEach(async (win) => {
    await win?.stop()
  })
  ElMessage.success('已清空所有播放')
}

function toggleFullscreen() {
  const wallCard = document.querySelector('.wall-card') as HTMLElement
  if (!isFullscreen.value) {
    if (wallCard?.requestFullscreen) wallCard.requestFullscreen()
    isFullscreen.value = true
  } else {
    if (document.exitFullscreen) document.exitFullscreen()
    isFullscreen.value = false
  }
}

function handleLayoutChange(layout: LayoutType) {
  windowRefs.value.forEach(async (win) => {
    await win?.stop()
  })
  currentLayout.value = layout
  windowRefs.value = Array.from({ length: getWindowCount(layout) }, () => null)
  activeWindowIndex.value = 0
}

function setWindowRef(index: number, el: InstanceType<typeof PlayWallWindow> | null) {
  windowRefs.value[index] = el
}

async function loadDevices() {
  loading.value = true
  try {
    // ONVIF 设备量一般不大，一次性拉一大页即可
    const res = await getOnvifDevicePageApi({ page: 1, pageSize: 200 })
    devices.value = res.data.rows
  } finally {
    loading.value = false
  }
}

function handleDeviceClick(device: OnvifDevice) {
  if (device.status !== 1) {
    ElMessage.warning('设备离线，无法播放')
    return
  }
  if (!device.streamUri) {
    ElMessage.warning('设备尚未探测出 RTSP 地址')
    return
  }

  // 找第一个空闲格子
  let foundIndex = -1
  for (let i = 0; i < windowRefs.value.length; i++) {
    const win = windowRefs.value[i]
    if (!win || !(win.isPlaying ?? false)) {
      foundIndex = i
      break
    }
  }
  if (foundIndex === -1) foundIndex = activeWindowIndex.value

  const targetWindow = windowRefs.value[foundIndex]
  if (targetWindow) {
    activeWindowIndex.value = foundIndex
    targetWindow.play(device)
  }
}

function handleWindowClick(index: number) {
  activeWindowIndex.value = index
}

const windowIndices = computed(() =>
  Array.from({ length: getWindowCount(currentLayout.value) }, (_, i) => i)
)

onMounted(() => {
  loadDevices()
  document.body.style.overflow = 'hidden'
})

onUnmounted(() => {
  windowRefs.value.forEach(async (win) => {
    await win?.stop()
  })
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="play-monitor-wall">
    <el-row :gutter="12" style="height: 100%">
      <!-- 左侧：设备列表 -->
      <el-col :span="6" style="height: 100%">
        <el-card shadow="hover" class="tree-card">
          <template #header>
            <div class="card-header">
              <span>ONVIF 设备（{{ devices.length }}）</span>
              <el-button link :icon="VideoCamera" @click="loadDevices">刷新</el-button>
            </div>
          </template>
          <div v-loading="loading" class="device-list">
            <el-empty v-if="!loading && devices.length === 0" description="暂无设备" :image-size="60" />
            <div
              v-for="d in devices"
              :key="d.id"
              class="device-item"
              :class="{ 'is-offline': d.status !== 1 }"
              @click="handleDeviceClick(d)"
            >
              <span
                class="list-status-bar"
                :class="{ 'is-active': d.status === 1 }"
                role="img"
                :aria-label="d.status === 1 ? '1' : '0'"
              />
              <div class="device-item-info">
                <div class="device-item-name">{{ d.name || `${d.host}:${d.port}` }}</div>
                <div class="device-item-meta">
                  {{ d.manufacturer || '未探测' }} · {{ d.streamType === 1 ? '子码流' : '主码流' }}
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：监控墙 -->
      <el-col :span="18" style="height: 100%">
        <el-card shadow="hover" class="wall-card">
          <template #header>
            <div class="card-header">
              <span>监控墙</span>
              <div class="header-actions">
                <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏'">
                  <el-button text size="small" :icon="FullScreen" @click="toggleFullscreen" />
                </el-tooltip>
                <el-tooltip content="清屏">
                  <el-button text size="small" :icon="Brush" @click="handleClearAll" />
                </el-tooltip>
                <div class="layout-buttons">
                  <el-tooltip content="1×1">
                    <el-button text size="small" :icon="Grid" :class="{ 'is-active': currentLayout === '1x1' }" @click="handleLayoutChange('1x1')" />
                  </el-tooltip>
                  <el-tooltip content="2×2">
                    <el-button text size="small" :icon="Grid" :class="{ 'is-active': currentLayout === '2x2' }" @click="handleLayoutChange('2x2')" />
                  </el-tooltip>
                  <el-tooltip content="3×3">
                    <el-button text size="small" :icon="Grid" :class="{ 'is-active': currentLayout === '3x3' }" @click="handleLayoutChange('3x3')" />
                  </el-tooltip>
                  <el-tooltip content="4×4">
                    <el-button text size="small" :icon="Grid" :class="{ 'is-active': currentLayout === '4x4' }" @click="handleLayoutChange('4x4')" />
                  </el-tooltip>
                </div>
              </div>
            </div>
          </template>

          <div class="play-wall-container" :class="`layout-${currentLayout}`">
            <template v-for="index in windowIndices" :key="`${currentLayout}-${index}`">
              <PlayWallWindow
                :ref="el => setWindowRef(index, el as any)"
                :active="index === activeWindowIndex"
                @click="handleWindowClick(index)"
              />
            </template>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.play-monitor-wall {
  height: calc(100vh - 140px);
  max-height: calc(100vh - 140px);
  overflow: hidden;
}

:deep(html),
:deep(body) {
  overflow: hidden !important;
}

.play-monitor-wall,
:deep(.el-row) {
  height: calc(100vh - 140px) !important;
  max-height: calc(100vh - 140px) !important;
}

.tree-card {
  height: 100%;
  max-height: 100%;
  overflow: hidden;
  margin-bottom: 0 !important;
}

.tree-card :deep(.el-card__body) {
  height: calc(100% - 48px);
  padding: 0;
  overflow: hidden;
}

.device-list {
  height: 100%;
  padding: 8px;
  overflow-y: auto;
  overflow-x: hidden;
}

.device-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.device-item:hover {
  background-color: var(--el-fill-color-light);
}

.device-item.is-offline {
  cursor: not-allowed;
  opacity: 0.65;
}

.list-status-bar {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-danger);
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.35);
}

.list-status-bar.is-active {
  background: var(--el-color-success);
}

.device-item-info {
  flex: 1;
  min-width: 0;
}

.device-item-name {
  font-size: 14px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-item-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wall-card {
  height: 100%;
  max-height: 100%;
  overflow: hidden;
  margin-bottom: 0 !important;
}

.wall-card :deep(.el-card__body) {
  height: calc(100% - 48px);
  overflow: hidden;
  padding: 12px 12px 0 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.layout-buttons {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: 4px;
}

.layout-buttons .el-button {
  color: #909399;
}

.layout-buttons .el-button.is-active {
  color: #409eff;
  background-color: rgba(64, 158, 255, 0.1);
}

.play-wall-container {
  display: grid;
  gap: 8px;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.play-wall-container.layout-1x1 {
  grid-template-columns: 1fr;
  grid-template-rows: 1fr;
}

.play-wall-container.layout-2x2 {
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
}

.play-wall-container.layout-3x3 {
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
}

.play-wall-container.layout-4x4 {
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(4, 1fr);
}
</style>
