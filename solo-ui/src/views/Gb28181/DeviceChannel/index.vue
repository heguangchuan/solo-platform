<script lang="ts">
export default { name: 'Gb28181DeviceChannel' }
</script>
<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { GbDevice } from '@/types/api'
import { searchGbDeviceApi } from '@/api/gb28181'
import ChannelListTable from '@/views/Gb28181/Device/components/ChannelListTable.vue'
import ChannelBatchAddDialog from './components/ChannelBatchAddDialog.vue'

const tableRef = ref<InstanceType<typeof ChannelListTable> | null>(null)
const addDialogRef = ref<InstanceType<typeof ChannelBatchAddDialog> | null>(null)

// ---------------- 左侧设备列表（滚动加载+后端搜索） ----------------
const deviceLoading = ref(false)
const loadingMore = ref(false)
const deviceList = ref<GbDevice[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const hasMore = ref(true)
const selectedDeviceId = ref<string | undefined>(undefined) // undefined = 全部设备
const deviceSearchKeyword = ref('')
let searchTimer: NodeJS.Timeout | null = null

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

// 防抖搜索：输入停止300ms后调用接口
function handleSearch() {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    fetchDeviceList(true)
  }, 300)
}

// 滚动到底部加载更多
function loadMore() {
  fetchDeviceList()
}

// 搜索关键词变化时触发防抖搜索
watch(deviceSearchKeyword, () => {
  handleSearch()
})

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
  <el-row gutter="16">
    <!-- 左侧设备列表 -->
    <el-col :span="5">
      <el-card shadow="hover" class="device-card">
        <!-- 设备搜索 -->
        <div class="mb-3">
          <el-input
            v-model="deviceSearchKeyword"
            placeholder="搜索设备名称/编码"
            clearable
            @clear="fetchDeviceList(true)"
          />
        </div>

        <!-- 设备列表（无限滚动） -->
        <div
          class="device-list"
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
    </el-col>

    <!-- 右侧通道列表 -->
    <el-col :span="19">
      <ChannelListTable
        ref="tableRef"
        :device-id="selectedDeviceId"
        :external-add="true"
        @external-add="handleExternalAdd"
      />
    </el-col>
  </el-row>

  <ChannelBatchAddDialog ref="addDialogRef" @refresh="handleRefresh" />
</template>

<style scoped>
.device-card {
  height: 100%;
}

.tag-margin {
  margin-left: 8px;
  flex-shrink: 0;
}

.device-list {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
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

.mb-3 {
  margin-bottom: 12px;
}

.ml-1 {
  margin-left: 4px;
}
</style>