<script lang="ts">
export default { name: 'Gb28181MediaServer' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import type { GbMediaServer, GbMediaServerPageRequest } from '@/types/api'
import {
  getGbMediaServerPageApi,
  saveGbMediaServerApi,
  deleteGbMediaServerApi,
} from '@/api/gb28181'
import MediaServerDetailDialog from './components/MediaServerDetailDialog.vue'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'

const loading = ref(false)
const tableData = ref<GbMediaServer[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<GbMediaServerPageRequest>({ page: 1, pageSize: 10 })

// ---------------- 编辑对话框 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const form = ref<Partial<GbMediaServer>>(makeEmpty())

function makeEmpty(): Partial<GbMediaServer> {
  return {
    serverId: '',
    name: '',
    hostIp: '',
    httpPort: 80,
    secret: '',
    streamIp: '',
    rtpPortStart: 30000,
    rtpPortEnd: 35000,
  }
}

const rules = {
  serverId: [{ required: true, message: '请输入节点标识（与 ZLM mediaServerId 一致）', trigger: 'blur' }],
  hostIp: [{ required: true, message: '请输入管理地址', trigger: 'blur' }],
  httpPort: [{ required: true, message: '请输入管理端口', trigger: 'blur' }],
}

// ---------------- 列表 ----------------
async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getGbMediaServerPageApi(params)
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

// ---------------- 编辑/新增 ----------------
function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增节点'
  form.value = makeEmpty()
  dialogVisible.value = true
}

function handleEdit(row: GbMediaServer) {
  isEdit.value = true
  dialogTitle.value = '编辑节点'
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  await saveGbMediaServerApi(form.value)
  ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(row: GbMediaServer) {
  try {
    await ElMessageBox.confirm(`确定要删除节点 "${row.serverId}" 吗？`, '提示', { type: 'warning' })
    await deleteGbMediaServerApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

const detailDialogRef = ref<InstanceType<typeof MediaServerDetailDialog> | null>(null)

function handleDetail(row: GbMediaServer) {
  detailDialogRef.value?.open(row)
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="节点标识">
          <el-input v-model="searchForm.serverId" placeholder="serverId 模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="节点状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="在线" :value="1" />
            <el-option label="离线" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'gb28181:media-server:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'gb28181:media-server:save'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 360px)">
        <el-table-column prop="serverId" label="节点标识" />
        <el-table-column prop="name" label="节点名称" />
        <el-table-column prop="hostIp" label="管理地址" />
        <el-table-column prop="httpPort" label="管理端口" align="center" />
        <el-table-column label="端口范围" align="center">
          <template #default="{ row }">{{ row.rtpPortStart }} - {{ row.rtpPortEnd }}</template>
        </el-table-column>
        <el-table-column label="节点状态" align="center" width="85">
          <template #default="{ row }">
            <ListStatusBar :active="row.status === 1" />
          </template>
        </el-table-column>
        <el-table-column prop="hookAliveTime" label="最近心跳" />
        <el-table-column prop="updateTime" label="更新时间" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'gb28181:media-server:list'"
              type="primary"
              link
              @click="handleDetail(row)"
            >详情</el-button>
            <el-button
              v-permission="'gb28181:media-server:save'"
              type="primary"
              link
              @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-permission="'gb28181:media-server:delete'"
              type="danger"
              link
              @click="handleDelete(row)"
            >删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="节点标识" prop="serverId">
          <el-input v-model="form.serverId" placeholder="与 ZLM config.ini 中 [general].mediaServerId 一致" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="form.name" placeholder="节点显示名" />
        </el-form-item>
        <el-form-item label="管理地址" prop="hostIp">
          <el-input v-model="form.hostIp" placeholder="ZLM 内网 / 管理面 IP，用于 REST API" />
        </el-form-item>
        <el-form-item label="管理端口" prop="httpPort">
          <el-input-number v-model="form.httpPort" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="接口密钥">
          <el-input v-model="form.secret" placeholder="与 ZLM [api].secret 一致" />
        </el-form-item>
        <el-form-item label="播放地址">
          <el-input v-model="form.streamIp" placeholder="HTTP-FLV / HLS / WebRTC 用，留空则同管理地址" />
        </el-form-item>
        <el-form-item label="端口范围">
          <el-input-number v-model="form.rtpPortStart" :min="1024" :max="65535" />
          <span style="margin: 0 8px">-</span>
          <el-input-number v-model="form.rtpPortEnd" :min="1024" :max="65535" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>

    <MediaServerDetailDialog ref="detailDialogRef" />
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
