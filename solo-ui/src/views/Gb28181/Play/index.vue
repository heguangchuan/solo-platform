<script lang="ts">
export default { name: 'Gb28181PlayMonitor' }
</script>
<script setup lang="ts">
/**
 * 实时点播监控墙
 * 左侧：设备通道树
 * 右侧：可配置多窗口播放（1×1 2×2 4×4）
 */
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { CircleClose } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { GbDeviceChannelTreeNode } from '@/types/api'
import { getDeviceChannelTreeApi } from '@/api/gb28181'
import PlayWallWindow from './components/PlayWallWindow.vue'

/** 布局选项 */
type LayoutType = '1x1' | '2x2' | '4x4'

const loading = ref(false)
const treeData = ref<GbDeviceChannelTreeNode[]>([])
const currentLayout = ref<LayoutType>('2x2')
const activeWindowIndex = ref(0)

// 根据布局获取窗口数量
function getWindowCount(layout: LayoutType): number {
  const map: Record<LayoutType, number> = {
    '1x1': 1,
    '2x2': 4,
    '4x4': 16,
  }
  return map[layout]
}

// 使用对象数组，每个元素保存引用
const windowRefs = ref<Array<InstanceType<typeof PlayWallWindow> | null>>(
  Array.from({ length: getWindowCount(currentLayout.value) }, () => null)
)

// 布局改变时
function handleLayoutChange(layout: LayoutType) {
  // 停止所有当前播放
  windowRefs.value.forEach(async (win) => {
    await win?.stop()
  })
  currentLayout.value = layout
  // 重新创建数组，长度正确，全部清空为null
  windowRefs.value = Array.from(
    { length: getWindowCount(layout) },
    () => null
  )
  activeWindowIndex.value = 0
}

// 设置引用
function setWindowRef(index: number, el: InstanceType<typeof PlayWallWindow> | null) {
  windowRefs.value[index] = el
}

// 加载树数据
async function loadTree() {
  loading.value = true
  try {
    const res = await getDeviceChannelTreeApi()
    treeData.value = res.data
  } finally {
    loading.value = false
  }
}

// 处理树节点点击
function handleNodeClick(node: GbDeviceChannelTreeNode) {
  if (node.isDevice || !node.channelId || !node.deviceId) {
    return // 只处理通道节点
  }
  if (node.channelStatus !== 'ON') {
    ElMessage.warning('通道不在线，无法播放')
    return
  }

  // 在当前激活窗口播放
  const activeWindow = windowRefs.value[activeWindowIndex.value]
  if (activeWindow) {
    activeWindow.play(node.deviceId!, node.channelId!, node.label)
  }
}

// 窗口点击激活
function handleWindowClick(index: number) {
  activeWindowIndex.value = index
}

// 计算属性生成窗口索引
const windowIndices = computed(() => {
  return Array.from({ length: getWindowCount(currentLayout.value) }, (_, i) => i)
})

onMounted(() => {
  loadTree()
  // 禁用页面滚动
  document.body.style.overflow = 'hidden'
})

// 页面卸载时恢复滚动
onUnmounted(() => {
  windowRefs.value.forEach(async (win) => {
    await win?.stop()
  })
  // 恢复页面滚动
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="play-monitor-wall">
    <el-row :gutter="12" style="height: 100%">
      <!-- 左侧：设备通道树 -->
      <el-col :span="6" style="height: 100%">
        <el-card shadow="hover" class="tree-card">
          <template #header>
            <div class="card-header">
              <span>设备通道树</span>
            </div>
          </template>
          <el-tree
            v-loading="loading"
            :data="treeData"
            :props="{ children: 'children', label: 'label' }"
            @node-click="handleNodeClick"
            accordion
          >
            <template #default="{ node }">
              <el-icon
                class="status-icon"
                :class="{
                  'online': (node.data.isDevice && node.data.status === 1) ||
                           (!node.data.isDevice && node.data.channelStatus === 'ON'),
                  'offline': (node.data.isDevice && node.data.status !== 1) ||
                             (!node.data.isDevice && node.data.channelStatus !== 'ON'),
                }"
              >
                <CircleClose />
              </el-icon>
              <span class="tree-label">{{ node.label }}</span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右侧：监控墙 -->
      <el-col :span="18" style="height: 100%">
        <el-card shadow="hover" class="wall-card">
          <template #header>
            <div class="card-header">
              <span>监控墙</span>
              <el-radio-group v-model="currentLayout" size="small" @change="handleLayoutChange">
                <el-radio-button value="1x1">1×1</el-radio-button>
                <el-radio-button value="2x2">2×2</el-radio-button>
                <el-radio-button value="4x4">4×4</el-radio-button>
              </el-radio-group>
            </div>
          </template>

          <div
            class="play-wall-container"
            :class="`layout-${currentLayout}`"
          >
            <template v-for="index in windowIndices" :key="`${currentLayout}-${index}`">
              <PlayWallWindow
                :ref="el => setWindowRef(index, el)"
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

/* 强制整个页面不出现滚动条 */
:deep(html),
:deep(body) {
  overflow: hidden !important;
}

/* 强制高度 */
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

.tree-card :deep(.el-tree) {
  padding: 8px;
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
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

.play-wall-container.layout-4x4 {
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(4, 1fr);
}

.status-icon {
  margin-right: 6px;
}

.status-icon.online {
  color: #67c23a;
}

.status-icon.offline {
  color: #909399;
}

.tree-label {
  flex: 1;
}
</style>
