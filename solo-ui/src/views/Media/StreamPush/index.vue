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
import { Search, Refresh, Delete, VideoCamera, Link, CircleClose, Plus, CopyDocument, Edit, RefreshRight } from '@element-plus/icons-vue'
import type { StreamPush, StreamPushPageRequest, GbMediaServer, StreamPushClient } from '@/types/api'
import {
  getStreamPushPageApi,
  getStreamPushPlayUrlsApi,
  stopStreamPushApi,
  deleteStreamPushApi,
  batchDeleteStreamPushApi,
  listGbMediaServerApi,
  getStreamPushClientPageApi,
  saveStreamPushClientApi,
  updateStreamPushClientApi,
  resetStreamPushClientTokenApi,
  deleteStreamPushClientApi,
} from '@/api/gb28181'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'
import StreamPushPlayDialog from './components/StreamPushPlayDialog.vue'

const activeTab = ref('client')

// ==================== 推流列表相关 ====================
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

// ==================== 授权管理相关 ====================
const clientLoading = ref(false)
const clientTableData = ref<StreamPushClient[]>([])
const clientFormVisible = ref(false)
const clientFormTitle = ref('')
const clientIsEdit = ref(false)
const clientFormRef = ref()
const clientForm = ref<Partial<StreamPushClient>>({})
const clientRules = {
  clientId: [
    { required: true, message: '请输入推流方标识', trigger: 'blur' },
  ],
}

async function fetchClientData() {
  clientLoading.value = true
  try {
    const res = await getStreamPushClientPageApi({ page: 1, pageSize: 1000 })
    clientTableData.value = res.data.rows
  } finally {
    clientLoading.value = false
  }
}

function handleClientAdd() {
  clientIsEdit.value = false
  clientFormTitle.value = '新增推流方'
  clientForm.value = { clientId: '', enabled: 1 }
  clientFormVisible.value = true
}

function handleClientEdit(row: StreamPushClient) {
  clientIsEdit.value = true
  clientFormTitle.value = '编辑推流方'
  clientForm.value = { ...row }
  clientFormVisible.value = true
}

async function handleClientConfirm() {
  if (!clientFormRef.value) return
  await clientFormRef.value.validate()
  if (clientIsEdit.value) {
    await updateStreamPushClientApi(clientForm.value)
    ElMessage.success('修改成功')
  } else {
    await saveStreamPushClientApi(clientForm.value)
    ElMessage.success('新增成功')
  }
  clientFormVisible.value = false
  fetchClientData()
}

async function handleClientResetToken(row: StreamPushClient) {
  try {
    await ElMessageBox.confirm(
      `确定重置 "${row.clientId}" 的 token 吗？老 token 立即失效，正在推流的连接不会断开（需在推流列表手动中止）。`,
      '提示',
      { type: 'warning' }
    )
    const res = await resetStreamPushClientTokenApi(row.id)
    ElMessage.success('已重置')
    showClientToken(res.data.token, row.clientId)
    fetchClientData()
  } catch {
    // cancelled
  }
}

async function handleClientDelete(row: StreamPushClient) {
  try {
    await ElMessageBox.confirm(`确定要删除推流方 "${row.clientId}" 吗？删除后该 token 立即失效。`, '提示', {
      type: 'warning',
    })
    await deleteStreamPushClientApi(row.id)
    ElMessage.success('删除成功')
    fetchClientData()
  } catch {
    // cancelled
  }
}

function showClientToken(token: string, clientId: string) {
  ElMessageBox.alert(
    `<div style="font-size:12px;color:#909399;margin-bottom:6px">推流方 ${clientId} 的 token：</div>` +
      `<div style="padding:8px;background:#f5f7fa;border-radius:4px;word-break:break-all;font-family:monospace">${token}</div>` +
      `<div style="margin-top:8px;font-size:12px;color:#909399">推流 URL 示例：<br/><code style="color:#409eff">rtmp://&lt;zlm&gt;:1935/&lt;app&gt;/&lt;stream&gt;?token=${token}</code></div>`,
    'Token',
    { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' }
  ).catch(() => {})
}

async function copyClientToken(token: string) {
  try {
    await navigator.clipboard.writeText(token)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

// 页面初始化
onMounted(() => {
  fetchData()
  loadMediaServers()
  fetchClientData()
})
</script>

<template>
  <div>
    <!-- 整个内容区放在同一个大卡片里，Tab在内部，背景完全统一 -->
    <el-card shadow="hover">
      <el-tabs v-model="activeTab">
        <!-- 第一个Tab：授权管理 -->
        <el-tab-pane label="授权管理" name="client">
          <div class="table-toolbar">
            <el-button
              v-permission="'media:stream-push-client:add'"
              type="primary"
              :icon="Plus"
              @click="handleClientAdd"
            >
              新增
            </el-button>
          </div>

          <el-table :data="clientTableData" v-loading="clientLoading" border stripe max-height="calc(100vh - 260px)">
            <el-table-column prop="clientId" label="推流方" min-width="180" show-overflow-tooltip />
            <el-table-column label="Token" show-overflow-tooltip>
              <template #default="{ row }">
                <span style="font-family: monospace; font-size: 12px">{{ row.token }}</span>
                <el-button link type="primary" :icon="CopyDocument" @click="copyClientToken(row.token)" />
              </template>
            </el-table-column>
            <el-table-column label="启用" align="center" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
                  {{ row.enabled === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" width="170" />
            <el-table-column label="操作" width="130" align="center" fixed="right">
              <template #default="{ row }">
                <el-tooltip content="编辑">
                  <el-button
                    v-permission="'media:stream-push-client:edit'"
                    type="primary"
                    link
                    :icon="Edit"
                    @click="handleClientEdit(row)"
                  />
                </el-tooltip>
                <el-tooltip content="重置 Token">
                  <el-button
                    v-permission="'media:stream-push-client:edit'"
                    type="warning"
                    link
                    :icon="RefreshRight"
                    @click="handleClientResetToken(row)"
                  />
                </el-tooltip>
                <el-tooltip content="删除">
                  <el-button
                    v-permission="'media:stream-push-client:delete'"
                    type="danger"
                    link
                    :icon="Delete"
                    @click="handleClientDelete(row)"
                  />
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 第二个Tab：推流列表 -->
        <el-tab-pane label="推流列表" name="push">
          <!-- 搜索区域直接放，用下边框分隔，不需要额外卡片 -->
          <el-form :model="searchForm" inline class="mb-4 pb-4 border-b">
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

          <div class="table-toolbar">
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
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 公共弹窗 -->
    <StreamPushPlayDialog ref="playDialogRef" />

    <!-- 授权管理新增/编辑弹窗 -->
    <el-dialog v-model="clientFormVisible" :title="clientFormTitle" width="480px" :close-on-click-modal="false" append-to-body>
      <el-form ref="clientFormRef" :model="clientForm" :rules="clientRules" label-width="100px">
        <el-form-item label="推流方" prop="clientId">
          <el-input v-model="clientForm.clientId" placeholder="唯一标识" :disabled="clientIsEdit" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="clientForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clientFormVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleClientConfirm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 仅保留基础布局样式，所有颜色完全复用全局变量 */
.table-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.mb-4 {
  margin-bottom: 16px;
}
.pb-4 {
  padding-bottom: 16px;
}
.border-b {
  /* 分隔线用全局边框变量，自动跟随主题变色 */
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
