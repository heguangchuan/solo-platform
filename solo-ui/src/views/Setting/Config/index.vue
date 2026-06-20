<script lang="ts">
export default { name: 'SettingConfig' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Download, Close, Check } from '@element-plus/icons-vue'
import type { SysConfig, ConfigRequest, ConfigPageRequest } from '@/types/api'
import {
  getConfigPageApi,
  addConfigApi,
  editConfigApi,
  deleteConfigApi,
  deleteConfigBatchApi,
  exportConfigApi,
} from '@/api/config'
import { downloadBlob } from '@/utils/download'

const loading = ref(false)
const tableData = ref<SysConfig[]>([])
const selectedRows = ref<SysConfig[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<ConfigPageRequest>({ page: 1, pageSize: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<string>('')
const form = ref<ConfigRequest>({
  configName: '',
  configKey: '',
  configValue: '',
  remark: '',
})

const rules = {
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configKey: [{ required: true, message: '请输入参数编码', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入参数取值', trigger: 'blur' }],
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.value = { page: 1, pageSize: 10 }
  handleSearch()
}

async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getConfigPageApi(params)
    tableData.value = res.data.rows
    total.value = res.data.total
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  fetchData()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

function handleAdd() {
  dialogTitle.value = '新增参数'
  editingId.value = ''
  form.value = {
    configName: '',
    configKey: '',
    configValue: '',
    remark: '',
  }
  dialogVisible.value = true
}

function handleEdit(row: SysConfig) {
  dialogTitle.value = '编辑参数'
  editingId.value = row.id
  form.value = {
    configName: row.configName,
    configKey: row.configKey,
    configValue: row.configValue,
    remark: row.remark,
  }
  dialogVisible.value = true
}

async function handleConfirm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    if (!editingId.value) {
      await addConfigApi(form.value)
    } else {
      await editConfigApi(editingId.value, form.value)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // handled
  }
}

function handleSelectionChange(rows: SysConfig[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条数据吗？`, '提示', {
      type: 'warning',
    })
    await deleteConfigBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

async function handleExport() {
  const ids = selectedRows.value.map((row) => row.id)
  const res = await exportConfigApi({ ...searchForm.value, ids: ids.length ? ids : undefined })
  downloadBlob(res.data, '参数列表.xlsx')
}

async function handleDelete(row: SysConfig) {
  try {
    await ElMessageBox.confirm(`确定要删除参数 "${row.configName}" 吗？`, '提示', { type: 'warning' })
    await deleteConfigApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
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
        <el-form-item label="参数名称">
          <el-input v-model="searchForm.configName" placeholder="请输入参数名称" clearable />
        </el-form-item>
        <el-form-item label="参数编码">
          <el-input v-model="searchForm.configKey" placeholder="请输入参数编码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'setting:config:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'setting:config:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'setting:config:delete'" type="primary" :icon="Delete" @click="handleBatchDelete">删除</el-button>
        <el-button v-permission="'setting:config:export'" type="primary" :icon="Download" @click="handleExport">导出</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange" max-height="calc(100vh - 400px)">
        <el-table-column type="selection" width="55" / max-height="calc(100vh - 400px)">
        <el-table-column prop="configName" label="参数名称" min-width="160" / max-height="calc(100vh - 400px)">
        <el-table-column prop="configKey" label="参数编码" min-width="180" / max-height="calc(100vh - 400px)">
        <el-table-column prop="configValue" label="参数取值" min-width="200" show-overflow-tooltip / max-height="calc(100vh - 400px)">
        <el-table-column prop="remark" label="参数备注" min-width="180" show-overflow-tooltip / max-height="calc(100vh - 400px)">
        <el-table-column prop="createTime" label="创建时间" min-width="180" / max-height="calc(100vh - 400px)">
        <el-table-column label="操作" width="160" fixed="right" max-height="calc(100vh - 400px)">
          <template #default="{ row }">
            <el-button v-permission="'setting:config:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-permission="'setting:config:delete'"
              type="danger"
              link
              @click="handleDelete(row)"
            >
              删除
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" placeholder="请输入参数名称" />
        </el-form-item>
        <el-form-item label="参数编码" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如：sys.title" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="参数取值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="请输入参数取值" />
        </el-form-item>
        <el-form-item label="参数备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="handleConfirm">确定</el-button>
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
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
