<script lang="ts">
export default { name: 'Gb28181Platform' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { GbPlatform, GbPlatformPageRequest } from '@/types/api'
import {
  getGbPlatformPageApi,
  saveGbPlatformApi,
  updateGbPlatformApi,
  deleteGbPlatformApi,
  batchDeleteGbPlatformApi,
} from '@/api/gb28181'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'

const loading = ref(false)
const tableData = ref<GbPlatform[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const selectedRows = ref<GbPlatform[]>([])

const searchForm = ref<GbPlatformPageRequest>({ page: 1, pageSize: 10 })

// ---------------- 新增/编辑对话框 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const form = ref<Partial<GbPlatform>>(makeEmpty())

function makeEmpty(): Partial<GbPlatform> {
  return {
    platformId: '',
    name: '',
    enabled: 1,
  }
}

const rules = {
  platformId: [
    { required: true, message: '请输入平台国标ID（20位）', trigger: 'blur' },
    { pattern: /^\d{20}$/, message: '平台国标ID为20位数字', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入平台名称', trigger: 'blur' }],
}

// ---------------- 列表 ----------------
async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getGbPlatformPageApi(params)
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

function handleSelectionChange(rows: GbPlatform[]) {
  selectedRows.value = rows
}

// ---------------- 新增/编辑 ----------------
function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增级联平台'
  form.value = makeEmpty()
  dialogVisible.value = true
}

function handleEdit(row: GbPlatform) {
  isEdit.value = true
  dialogTitle.value = '编辑级联平台'
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isEdit.value) {
    await updateGbPlatformApi(form.value)
    ElMessage.success('修改成功')
  } else {
    await saveGbPlatformApi(form.value)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(row: GbPlatform) {
  try {
    await ElMessageBox.confirm(`确定要删除平台 "${row.name}" 的白名单记录吗？`, '提示', { type: 'warning' })
    await deleteGbPlatformApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的平台')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 个平台的白名单记录吗？`,
      '提示',
      { type: 'warning' }
    )
    const ids = selectedRows.value.map(r => r.id)
    await batchDeleteGbPlatformApi(ids)
    ElMessage.success('删除成功')
    fetchData()
    selectedRows.value = []
  } catch {
    // cancelled
  }
}

onMounted(() => {
  fetchData()
})
</script>
<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="平台编码">
          <el-input v-model="searchForm.platformId" placeholder="国标编码模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="平台名称">
          <el-input v-model="searchForm.name" placeholder="名称模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-select v-model="searchForm.enabled" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'gb28181:platform:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'gb28181:platform:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'gb28181:platform:delete'" type="danger" :icon="Delete" @click="handleBatchDelete" :disabled="selectedRows.length === 0">删除</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 360px)" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column prop="platformId" label="平台编码" min-width="180" show-overflow-tooltip />
        <el-table-column prop="name" label="平台名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="在线状态" align="center" width="85">
          <template #default="{ row }">
            <ListStatusBar v-if="row.status !== undefined && row.status !== 2" :active="row.status === 1" />
            <el-tag v-else-if="row.status === 2" type="info" size="small">未注册</el-tag>
            <span v-else class="no-data">—</span>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" align="center" width="85">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="authKey" label="鉴权密钥" min-width="200" show-overflow-tooltip />
        <el-table-column prop="channelCount" label="通道数" align="center" width="80">
          <template #default="{ row }">{{ row.channelCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="keepaliveTime" label="最近心跳" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="编辑">
              <el-button v-permission="'gb28181:platform:edit'" type="primary" link :icon="Edit" @click="handleEdit(row)" />
            </el-tooltip>
            <el-tooltip content="删除">
              <el-button v-permission="'gb28181:platform:delete'" type="danger" link :icon="Delete" @click="handleDelete(row)" />
            </el-tooltip>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="平台编码" prop="platformId">
          <el-input v-model="form.platformId" placeholder="20位数字" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="平台名称" prop="name">
          <el-input v-model="form.name" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>
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
.no-data {
  color: #999;
}
</style>