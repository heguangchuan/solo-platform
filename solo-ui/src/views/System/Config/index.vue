<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  configType: 0,
  remark: '',
})

const rules = {
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configKey: [{ required: true, message: '请输入参数键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入参数值', trigger: 'blur' }],
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
    configType: 0,
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
    configType: row.configType,
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
        <el-form-item label="参数键">
          <el-input v-model="searchForm.configKey" placeholder="请输入参数键" clearable />
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'system:config:list'" type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'system:config:add'" type="primary" size="small" @click="handleAdd">新增</el-button>
        <el-button v-permission="'system:config:delete'" type="primary" size="small" @click="handleBatchDelete">删除</el-button>
        <el-button v-permission="'system:config:export'" type="primary" size="small" @click="handleExport">导出</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="configName" label="参数名称" min-width="160" />
        <el-table-column prop="configKey" label="参数键" min-width="180" />
        <el-table-column prop="configValue" label="参数值" min-width="200" show-overflow-tooltip />
        <el-table-column label="是否内置" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.configType === 1" type="warning">是</el-tag>
            <el-tag v-else type="info">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:config:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-permission="'system:config:delete'"
              type="danger"
              link
              :disabled="row.configType === 1"
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" placeholder="请输入参数名称" />
        </el-form-item>
        <el-form-item label="参数键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如：sys.title" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="参数值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="请输入参数值" />
        </el-form-item>
        <el-form-item label="是否内置">
          <el-switch v-model="form.configType" :active-value="1" :inactive-value="0" />
          <span class="form-tip">内置参数禁止删除</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
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

.form-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
