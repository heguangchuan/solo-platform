<script lang="ts">
export default { name: 'MediaStreamProxy' }
</script>
<script setup lang="ts">
/**
 * 拉流代理管理页
 * <p>底层调 ZLM addStreamProxy / delStreamProxy，跟 ONVIF/GB 点播共享 media_server。</p>
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, CircleCheck, CircleClose, VideoCamera, Link } from '@element-plus/icons-vue'
import type { StreamProxy, StreamProxyPageRequest, GbMediaServer } from '@/types/api'
import {
  getStreamProxyPageApi,
  saveStreamProxyApi,
  updateStreamProxyApi,
  enableStreamProxyApi,
  disableStreamProxyApi,
  deleteStreamProxyApi,
  batchDeleteStreamProxyApi,
  getStreamProxyPlayUrlsApi,
  listGbMediaServerApi,
} from '@/api/gb28181'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'
import StreamProxyPlayDialog from './components/StreamProxyPlayDialog.vue'

const loading = ref(false)
const tableData = ref<StreamProxy[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const selectedRows = ref<StreamProxy[]>([])
const mediaServers = ref<GbMediaServer[]>([])

const searchForm = ref<StreamProxyPageRequest>({ page: 1, pageSize: 10 })

// ---------------- 新增/编辑 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const form = ref<Partial<StreamProxy>>(makeEmpty())

function makeEmpty(): Partial<StreamProxy> {
  return {
    name: '',
    app: 'proxy',
    stream: '',
    srcUrl: '',
    mediaServerId: '',
    rtpType: 1,
    enableAudio: 1,
    enableMp4: 0,
    enableDisableNoneReader: 0,
    enabled: 1,
    remark: '',
  }
}

const rules = {
  stream: [
    { required: true, message: '请输入 stream 名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_\-.]+$/, message: '只允许字母/数字/下划线/中划线/点', trigger: 'blur' },
  ],
  srcUrl: [{ required: true, message: '请输入源地址（rtsp:// rtmp:// http-flv/hls 等）', trigger: 'blur' }],
  app: [{ required: true, message: '请输入 app', trigger: 'blur' }],
}

// ---------------- 列表 ----------------
async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getStreamProxyPageApi(params)
    tableData.value = res.data.rows
    total.value = res.data.total
  } finally {
    loading.value = false
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

async function loadMediaServers() {
  try {
    const res = await listGbMediaServerApi()
    mediaServers.value = res.data
  } catch {
    // ignore
  }
}

// ---------------- 新增/编辑 ----------------
function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增拉流代理'
  form.value = makeEmpty()
  dialogVisible.value = true
}

function handleEdit(row: StreamProxy) {
  isEdit.value = true
  dialogTitle.value = '编辑拉流代理'
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isEdit.value) {
    await updateStreamProxyApi(form.value)
    ElMessage.success('修改成功')
  } else {
    await saveStreamProxyApi(form.value)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  fetchData()
}

// ---------------- 启停 ----------------
async function handleEnable(row: StreamProxy) {
  await enableStreamProxyApi(row.id)
  ElMessage.success('已推流到 ZLM')
  fetchData()
}

async function handleDisable(row: StreamProxy) {
  await disableStreamProxyApi(row.id)
  ElMessage.success('已停止')
  fetchData()
}

// ---------------- 删除 ----------------
async function handleDelete(row: StreamProxy) {
  try {
    await ElMessageBox.confirm(`确定要删除代理 "${row.app}/${row.stream}" 吗？`, '提示', { type: 'warning' })
    await deleteStreamProxyApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的代理')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 条代理吗？此操作不可恢复。`,
      '提示',
      { type: 'warning' }
    )
    await batchDeleteStreamProxyApi(selectedRows.value.map(r => r.id))
    ElMessage.success('删除成功')
    selectedRows.value = []
    fetchData()
  } catch {
    // cancelled
  }
}

function handleSelectionChange(rows: StreamProxy[]) {
  selectedRows.value = rows
}

// ---------------- 播放 ----------------
const playDialogRef = ref<InstanceType<typeof StreamProxyPlayDialog> | null>(null)

function handlePlay(row: StreamProxy) {
  playDialogRef.value?.open(row)
}

// ---------------- 播放地址 ----------------
async function handleShowPlayUrls(row: StreamProxy) {
  const res = await getStreamProxyPlayUrlsApi(row.id)
  const d = res.data
  if (!d.wsFlvUrl && !d.flvUrl) {
    ElMessage.warning('代理未在推流，无播放地址；请先启用')
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
        <el-form-item label="代理名称">
          <el-input v-model="searchForm.name" placeholder="名称模糊" clearable />
        </el-form-item>
        <el-form-item label="流标识符">
          <el-input v-model="searchForm.stream" placeholder="stream 模糊" clearable />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-select v-model="searchForm.enabled" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="推流状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="已推流" :value="1" />
            <el-option label="未推流" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'media:stream-proxy:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'media:stream-proxy:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button
          v-permission="'media:stream-proxy:delete'"
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
        <el-table-column prop="name" label="代理名称" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '—' }}</template>
        </el-table-column>
        <el-table-column label="应用 / 流" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.app }} / {{ row.stream }}</template>
        </el-table-column>
        <el-table-column prop="srcUrl" label="流媒体源" show-overflow-tooltip min-width="240" />
        <el-table-column prop="mediaServerId" label="目标节点" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.mediaServerId || '自动' }}</template>
        </el-table-column>
        <el-table-column label="推流状态" align="center" width="90">
          <template #default="{ row }">
            <el-tooltip v-if="row.status !== 1 && row.lastError" :content="row.lastError" placement="top">
              <ListStatusBar :active="false" />
            </el-tooltip>
            <ListStatusBar v-else :active="row.status === 1" />
          </template>
        </el-table-column>
        <el-table-column label="启用状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="播放">
                <el-button
                  v-permission="'media:stream-proxy:list'"
                  type="primary"
                  link
                  :icon="VideoCamera"
                  :disabled="row.status !== 1"
                  @click="handlePlay(row)"
                />
              </el-tooltip>
              <el-tooltip content="播放地址">
                <el-button
                  v-permission="'media:stream-proxy:list'"
                  type="primary"
                  link
                  :icon="Link"
                  :disabled="row.status !== 1"
                  @click="handleShowPlayUrls(row)"
                />
              </el-tooltip>
              <el-tooltip :content="row.status === 1 ? '停用' : '启用'">
                <el-button
                  v-permission="'media:stream-proxy:toggle'"
                  :type="row.status === 1 ? 'warning' : 'success'"
                  link
                  :icon="row.status === 1 ? CircleClose : CircleCheck"
                  @click="row.status === 1 ? handleDisable(row) : handleEnable(row)"
                />
              </el-tooltip>
              <el-tooltip content="编辑">
                <el-button
                  v-permission="'media:stream-proxy:edit'"
                  type="primary"
                  link
                  :icon="Edit"
                  @click="handleEdit(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button
                  v-permission="'media:stream-proxy:delete'"
                  type="danger"
                  link
                  :icon="Delete"
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" :close-on-click-modal="false" top="10vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="代理名称">
          <el-input v-model="form.name" placeholder="显示名（可空）" />
        </el-form-item>
        <el-form-item label="应用名称" prop="app">
          <el-input v-model="form.app" placeholder="ZLM app，默认 proxy" />
        </el-form-item>
        <el-form-item label="流标识符" prop="stream">
          <el-input v-model="form.stream" placeholder="ZLM stream 名，唯一标识一路流" />
        </el-form-item>
        <el-form-item label="流媒体源" prop="srcUrl">
          <el-input
            v-model="form.srcUrl"
            type="textarea"
            :rows="2"
            placeholder="rtsp:// rtmp:// http://...flv http://...m3u8"
          />
        </el-form-item>
        <el-form-item label="目标节点">
          <el-select v-model="form.mediaServerId" placeholder="留空自动挑选" clearable style="width: 100%">
            <el-option
              v-for="s in mediaServers"
              :key="s.serverId"
              :label="`${s.name || s.serverId}（${s.hostIp}:${s.httpPort}）`"
              :value="s.serverId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="传输方式">
          <el-select v-model="form.rtpType" style="width: 100%">
            <el-option label="TCP+UDP 自适应" :value="0" />
            <el-option label="TCP" :value="1" />
            <el-option label="UDP" :value="2" />
            <el-option label="组播" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用音频">
          <el-switch v-model="form.enableAudio" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="录制视频">
          <el-switch v-model="form.enableMp4" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="闲时自停">
          <el-switch v-model="form.enableDisableNoneReader" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="立即启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">保存后自动推流到 ZLM</span>
        </el-form-item>
        <el-form-item label="备注信息">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注（可空）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>

    <StreamProxyPlayDialog ref="playDialogRef" />
  </div>
</template>

<style scoped>
.search-card {
  margin-bottom: 0;
}

.table-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
