<script lang="ts">
export default { name: 'Gb28181DeviceChannel' }
</script>
<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { Loading, Search, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import type { GbDevice } from '@/types/api'
import { searchGbDeviceApi } from '@/api/gb28181'
import ChannelListTable from '@/views/Gb28181/Device/components/ChannelListTable.vue'
import ChannelBatchAddDialog from './components/ChannelBatchAddDialog.vue'

const tableRef = ref<InstanceType<typeof ChannelListTable> | null>(null)
const addDialogRef = ref<InstanceType<typeof ChannelBatchAddDialog> | null>(null)

// 左侧设备面板折叠状态
const panelCollapsed = ref(false)

// ---------------- 左侧设备列表（滚动加载+后端搜索） ----------------
const deviceLoading = ref(false)
const loadingMore = ref(false)
const deviceList = ref<GbDevice[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const hasMore = ref(true)
const selectedDeviceId = ref<string | undefined>(undefined) // undefined = 全部设备
const deviceSearchKeyword = ref('')

// 设备状态映射
const statusTextMap: Record<number, { text: string; type: 'success' | 'danger' | 'info' }> = {
  1: { text: '在线', type: 'success' },
  0: { text: '离线', type: 'danger' },
  2: { text: '未注册', type: 'info' }
}

// 加载设备列表（分页+搜索）
async function fetchDeviceList(reset = false) {
  if (loadingMore.value && !reset) return
  if (!hasMore.value && !reset) return

  if (reset) {
    currentPage.value = 1
    hasMore.value = true
    deviceList.value = []
  }

  loadingMore.value = true
  try {
    const res = await searchGbDeviceApi(
      deviceSearchKeyword.value.trim() || undefined,
      currentPage.value,
      pageSize.value
    )
    if (reset) {
      deviceList.value = res.data.rows
    } else {
      deviceList.value = [...deviceList.value, ...res.data.rows]
    }
    // 判断是否还有更多数据
    hasMore.value = res.data.rows.length === pageSize.value
    if (hasMore.value) {
      currentPage.value++
    }
  } finally {
    loadingMore.value = false
  }
}

// 点击搜索图标/按回车触发搜索
function handleSearch() {
  fetchDeviceList(true)
}

// 滚动到底部加载更多
function loadMore() {
  fetchDeviceList()
}

function handleDeviceSelect(deviceId?: string) {
  selectedDeviceId.value = deviceId
  // 触发右侧通道列表刷新
  nextTick(() => {
    tableRef.value?.fetchData()
  })
}

// ---------------- 操作按钮 ----------------
function handleExternalAdd() {
  addDialogRef.value?.open()
}

function handleRefresh() {
  tableRef.value?.fetchData()
}

onMounted(() => {
  fetchDeviceList(true)
})
</script>

<template>
  <!-- 最外层容器：固定高度，占满整个可视区域，不受任何内容影响 -->
  <div class="full-page-container" :class="{ 'left-collapsed': panelCollapsed }">
    <!-- 左侧区域：固定宽度，独立高度，可整体折叠 -->
    <div class="left-panel">
      <el-card shadow="hover" class="device-card">
        <!-- 面板头部：标题 + 收起按钮 -->
        <div class="panel-header">
          <span class="panel-title">设备列表</span>
          <el-icon class="panel-toggle" title="收起设备列表" @click="panelCollapsed = true">
            <DArrowLeft />
          </el-icon>
        </div>

        <!-- 搜索框：固定在顶部，点击图标/按回车才搜索 -->
        <div class="search-box">
          <el-input
            v-model="deviceSearchKeyword"
            placeholder="搜索设备名称/编码"
            clearable
            @clear="fetchDeviceList(true)"
            @keyup.enter="handleSearch"
          >
            <template #suffix>
              <el-icon class="search-icon" @click="handleSearch">
                <Search />
              </el-icon>
            </template>
          </el-input>
        </div>

        <!-- 设备列表：固定高度，内部滚动，无限加载 -->
        <div
          class="device-scroll-area"
          v-infinite-scroll="loadMore"
          :infinite-scroll-disabled="loadingMore || !hasMore"
          infinite-scroll-distance="50"
        >
          <div
            class="device-item"
            v-for="device in deviceList"
            :key="device.id"
            :class="{ active: selectedDeviceId === device.deviceId }"
            @click="handleDeviceSelect(device.deviceId)"
          >
            <div class="device-name">
              <span class="name-text">{{ device.name || device.reportName || device.deviceId }}</span>
              <el-tag :type="statusTextMap[device.status ?? 2].type" size="small" class="tag-margin">
                {{ statusTextMap[device.status ?? 2].text }}
              </el-tag>
            </div>
            <div class="device-desc">{{ device.deviceId }}</div>
          </div>

          <!-- 空状态 -->
          <div v-if="deviceList.length === 0 && !loadingMore" class="empty-text">
            暂无设备
          </div>

          <!-- 加载更多提示 -->
          <div v-if="loadingMore" class="load-more-text">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span class="ml-1">加载中...</span>
          </div>
          <div v-if="!hasMore && deviceList.length > 0" class="load-more-text no-more">
            没有更多设备了
          </div>
        </div>
      </el-card>
    </div>

    <!-- 右侧区域：占满剩余宽度，独立滚动 -->
    <div class="right-panel">
      <ChannelListTable
        ref="tableRef"
        :device-id="selectedDeviceId"
        :external-add="true"
        @external-add="handleExternalAdd"
      />
    </div>

    <!-- 左侧面板收起后的展开按钮（相对最外层容器定位，不随右侧滚动） -->
    <button
      v-if="panelCollapsed"
      class="expand-panel-btn"
      title="展开设备列表"
      @click="panelCollapsed = false"
    >
      <el-icon><DArrowRight /></el-icon>
    </button>
  </div>

  <ChannelBatchAddDialog ref="addDialogRef" @refresh="handleRefresh" />
</template>

<style scoped>
/* 最外层容器：100%视口高度减去顶部导航+页面padding，固定死，不受内容影响 */
.full-page-container {
  position: relative;
  height: calc(100vh - 140px);
  display: flex;
  gap: 16px;
  overflow: hidden;
  transition: gap 0.25s ease;
}

.full-page-container.left-collapsed {
  gap: 0;
}

/* 左侧面板：固定宽度，100%高度，可整体折叠 */
.left-panel {
  width: 20%;
  min-width: 240px;
  max-width: 300px; /* 最大宽度限制，避免过宽 */
  height: 100%;
  flex-shrink: 0;
  transition: width 0.25s ease, min-width 0.25s ease, opacity 0.2s ease;
}

.full-page-container.left-collapsed .left-panel {
  width: 0;
  min-width: 0;
  opacity: 0;
  overflow: hidden;
}

/* 面板头部：标题 + 收起按钮 */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.panel-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.panel-toggle {
  cursor: pointer;
  color: var(--el-text-color-secondary);
  transition: color 0.2s;
}

.panel-toggle:hover {
  color: var(--el-color-primary);
}

/* 折叠后的展开按钮：浮在右侧内容区左上角 */
.expand-panel-btn {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 10;
  display: inline-flex;
  align-items: center;
  padding: 6px 8px;
  cursor: pointer;
  color: var(--el-text-color-regular);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  box-shadow: var(--el-box-shadow-light);
  transition: color 0.2s, border-color 0.2s;
}

.expand-panel-btn:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

/* 右侧面板：占满剩余宽度，100%高度，独立滚动 */
.right-panel {
  flex: 1;
  height: 100%;
  overflow-y: auto;
}

/* 左侧卡片：占满面板高度，flex布局 */
.device-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 搜索框：固定高度，不缩放 */
.search-box {
  margin-bottom: 12px;
  flex-shrink: 0;
}

/* 搜索图标样式 */
.search-icon {
  cursor: pointer;
  color: var(--el-text-color-secondary);
  transition: color 0.2s;
}
.search-icon:hover {
  color: var(--el-color-primary);
}

/* 设备滚动区域：自适应剩余高度，内部滚动 */
.device-scroll-area {
  flex: 1;
  overflow-y: auto;
  min-height: 0; /* 强制不超出父容器 */
}

.device-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid var(--el-border-color-lighter);
  transition: all 0.2s;
}

.device-item:hover {
  background-color: var(--el-fill-color-light);
  border-color: var(--el-border-color-light);
}

.device-item.active {
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.device-name {
  font-weight: 500;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-desc {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-text {
  text-align: center;
  padding: 40px 0;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.load-more-text {
  text-align: center;
  padding: 12px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.load-more-text.no-more {
  color: var(--el-text-color-placeholder);
}

.tag-margin {
  margin-left: 8px;
  flex-shrink: 0;
}

.ml-1 {
  margin-left: 4px;
}
</style>