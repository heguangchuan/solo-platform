<script lang="ts">
export default { name: 'MediaStreamPush' }
</script>
<script setup lang="ts">
/**
 * 推流管理页
 * <p>数据只读，由 ZLM on_stream_changed hook 驱动 upsert / 删除。
 * 用户能做：查看 / 播放 / 播放地址 / 强制中止 / 删离线记录。</p>
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, VideoCamera, Link, CircleClose, Key } from '@element-plus/icons-vue'
import type { StreamPush, StreamPushPageRequest, GbMediaServer } from '@/types/api'
import {
  getStreamPushPageApi,
  getStreamPushPlayUrlsApi,
  stopStreamPushApi,
  deleteStreamPushApi,
  batchDeleteStreamPushApi,
  listGbMediaServerApi,
} from '@/api/gb28181'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'
import StreamPushPlayDialog from './components/StreamPushPlayDialog.vue'
import StreamPushClientDialog from './components/StreamPushClientDialog.vue'

const loading = ref(false)
const tableData = ref<StreamPush[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const selectedRows = ref<StreamPush[]>([])
const mediaServers = ref<GbMediaServer[]>([])

const searchForm = ref<StreamPushPageRequest>({ page: 1, pageSize: 10 })

const originTypeMap: Record<string, string> = {
  rtmp: 'RTMP 推流',
  rtsp: 'RTSP 推流',
  rtc: 'WebRTC 推流',
}
function originTypeText(t?: string) {
  if (!t) return '—'
  return originTypeMap[t] ?? t.toUpperCase()
}

async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getStreamPushPageApi(params)
    tableData.value = res.data.rows
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadMediaServers() {
  try {
    const res = await listGbMediaServerApi()
    mediaServers.value = res.data
  } catch {
    // ignore
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.value = { page: 1, pageSize: 10 }
  handleSearch()
}

function handlePageChange(p: number) {
  currentPage.value = p
  fetchData()
}

function handleSizeChange(s: number) {
  pageSize.value = s
  currentPage.value = 1
  fetchData()
}

function handleSelectionChange(rows: StreamPush[]) {
  selectedRows.value = rows
}

// ---------------- 强制中止 ----------------
async function handleStop(row: StreamPush) {
  try {
    await ElMessageBox.confirm(
      `确定要强制中止 "${row.app}/${row.stream}" 吗？推流方将会断开连接。`,
      '提示',
      { type: 'warning' }
    )
    await stopStreamPushApi(row.id)
    ElMessage.success('已通知 ZLM 关流')
    fetchData()
  } catch {
    // cancelled
  }
}

// ---------------- 删除 ----------------
async function handleDelete(row: StreamPush) {
  try {
    await ElMessageBox.confirm(`确定要删除记录 "${row.app}/${row.stream}" 吗？`, '提示', {
      type: 'warning',
    })
    await deleteStreamPushApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的记录')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 条推流记录吗？（仍在推流的行会跳过）`,
      '提示',
      { type: 'warning' }
    )
    await batchDeleteStreamPushApi(selectedRows.value.map(r => r.id))
    ElMessage.success('操作完成')
    selectedRows.value = []
    fetchData()
  } catch {
    // cancelled
  }
}

// ---------------- 播放 ----------------
const playDialogRef = ref<InstanceType<typeof StreamPushPlayDialog> | null>(null)
function handlePlay(row: StreamPush) {
  playDialogRef.value?.open(row)
}

// ---------------- 授权 ----------------
const clientDialogRef = ref<InstanceType<typeof StreamPushClientDialog> | null>(null)
function handleOpenClientDialog() {
  clientDialogRef.value?.open()
}

async function handleShowPlayUrls(row: StreamPush) {
  const res = await getStreamPushPlayUrlsApi(row.id)
  const d = res.data
  if (!d.wsFlvUrl && !d.flvUrl) {
    ElMessage.warning('推流未在活跃状态，无播放地址')
    return
  }
  ElMessageBox.alert(
    [
      d.wsFlvUrl && `<div style="margin-bottom:6px"><b>WS-FLV</b><br/>${d.wsFlvUrl}</div>`,
      d.flvUrl && `<div style="margin-bottom:6px"><b>HTTP-FLV</b><br/>${d.flvUrl}</div>`,
      d.hlsUrl && `<div style="margin-bottom:6px"><b>HLS</b><br/>${d.hlsUrl}</div>`,
      d.rtcUrl && `<div style="margin-bottom:6px"><b>WebRTC</b><br/>${d.rtcUrl}</div>`,
    ]
      .filter(Boolean)
      .join(''),
    `${row.app}/${row.stream}`,
    { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' }
  ).catch(() => {})
}

onMounted(() => {
  fetchData()
  loadMediaServers()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="应用名称">
          <el-input v-model="searchForm.app" placeholder="app 精确" clearable />
        </el-form-item>
        <el-form-item label="流标识符">
          <el-input v-model="searchForm.stream" placeholder="stream 模糊" clearable />
        </el-form-item>
        <el-form-item label="目标节点">
          <el-select v-model="searchForm.mediaServerId" placeholder="请选择" clearable style="width: 180px">
            <el-option v-for="s in mediaServers" :key="s.serverId" :label="s.name || s.serverId" :value="s.serverId" />
          </el-select>
        </el-form-item>
        <el-form-item label="推流状态">
          <el-select v-model="searchForm.pushing" placeholder="请选择" clearable style="width: 120px">
            <el-option label="推流中" :value="1" />
            <el-option label="已断" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'media:stream-push:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button
          v-permission="'media:stream-push-client:list'"
          type="primary"
          :icon="Key"
          @click="handleOpenClientDialog"
        >
          授权
        </el-button>
        <el-button
          v-permission="'media:stream-push:delete'"
          type="danger"
          :icon="Delete"
          :disabled="selectedRows.length === 0"
          @click="handleBatchDelete"
        >
          删除
        </el-button>
      </div>

      <el-table
        :data="tableData"
        v-loading="loading"
        border
        stripe
        max-height="calc(100vh - 360px)"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column prop="app" label="应用名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="stream" label="流名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="推流类型" width="120" align="center">
          <template #default="{ row }">{{ originTypeText(row.originType) }}</template>
        </el-table-column>
        <el-table-column prop="mediaServerId" label="目标节点" width="140" show-overflow-tooltip />
        <el-table-column prop="clientId" label="推流方" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.clientId || '—' }}</template>
        </el-table-column>
        <el-table-column label="推流状态" align="center" width="90">
          <template #default="{ row }">
            <ListStatusBar :active="row.pushing === 1" />
          </template>
        </el-table-column>
        <el-table-column prop="pushTime" label="开始时间" width="170" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="播放">
                <el-button
                  v-permission="'media:stream-push:list'"
                  type="primary"
                  link
                  :icon="VideoCamera"
                  :disabled="row.pushing !== 1"
                  @click="handlePlay(row)"
                />
              </el-tooltip>
              <el-tooltip content="播放地址">
                <el-button
                  v-permission="'media:stream-push:list'"
                  type="primary"
                  link
                  :icon="Link"
                  :disabled="row.pushing !== 1"
                  @click="handleShowPlayUrls(row)"
                />
              </el-tooltip>
              <el-tooltip content="强制中止">
                <el-button
                  v-permission="'media:stream-push:stop'"
                  type="warning"
                  link
                  :icon="CircleClose"
                  :disabled="row.pushing !== 1"
                  @click="handleStop(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button
                  v-permission="'media:stream-push:delete'"
                  type="danger"
                  link
                  :icon="Delete"
                  :disabled="row.pushing === 1"
                  @click="handleDelete(row)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <StreamPushPlayDialog ref="playDialogRef" />
    <StreamPushClientDialog ref="clientDialogRef" />
  </div>
</template>

<style scoped>
.search-card { margin-bottom: 0; }
.table-toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
