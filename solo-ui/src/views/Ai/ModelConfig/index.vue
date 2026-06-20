<script lang="ts">
export default { name: 'AiModelConfig' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Close, Check } from '@element-plus/icons-vue'
import type { AiModelConfig, AiModelConfigRequest, AiModelConfigPageRequest, AiModelProviderOption } from '@/types/api'
import {
  getModelConfigPageApi,
  addModelConfigApi,
  editModelConfigApi,
  deleteModelConfigApi,
  deleteModelConfigBatchApi,
  getModelProviderOptionsApi,
} from '@/api/ai'

const loading = ref(false)
const tableData = ref<AiModelConfig[]>([])
const selectedRows = ref<AiModelConfig[]>([])
const providerOptions = ref<AiModelProviderOption[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<AiModelConfigPageRequest>({ page: 1, pageSize: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<string>('')
const form = ref<AiModelConfigRequest>({
  provider: '',
  modelName: '',
  modelType: 'TEXT',
  apiKey: '',
  baseUrl: '',
  modelParams: '',
  supportVision: 0,
  status: 1,
  sort: 1,
})

const rules = {
  provider: [{ required: true, message: '请选择模型来源', trigger: 'change' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
}

const modelTypeOptions = [
  { label: '文本', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '向量', value: 'EMBEDDING' },
  { label: '音频', value: 'AUDIO' },
]

function providerLabel(value: string) {
  return providerOptions.value.find((item) => item.value === value)?.label || value
}

function handleProviderChange(value: string) {
  const provider = providerOptions.value.find((item) => item.value === value)
  if (!provider) return
  if (!form.value.baseUrl && provider.defaultBaseUrl) {
    form.value.baseUrl = provider.defaultBaseUrl
  }
  form.value.supportVision = provider.supportVision ? 1 : 0
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
    const res = await getModelConfigPageApi(params)
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
  dialogTitle.value = '新增模型配置'
  editingId.value = ''
  form.value = {
    provider: '',
    modelName: '',
    modelType: 'TEXT',
    apiKey: '',
    baseUrl: '',
    modelParams: '',
    supportVision: 0,
    status: 1,
    sort: 1,
  }
  dialogVisible.value = true
}

function handleEdit(row: AiModelConfig) {
  dialogTitle.value = '编辑模型配置'
  editingId.value = row.id
  form.value = {
    provider: row.provider,
    modelName: row.modelName,
    modelType: row.modelType,
    apiKey: '',
    baseUrl: row.baseUrl,
    modelParams: row.modelParams,
    supportVision: row.supportVision ?? 0,
    status: row.status,
    sort: row.sort,
  }
  dialogVisible.value = true
}

async function handleConfirm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!editingId.value && !form.value.apiKey?.trim()) {
    ElMessage.warning('请输入API密钥')
    return
  }

  try {
    if (!editingId.value) {
      await addModelConfigApi(form.value)
    } else {
      await editModelConfigApi(editingId.value, form.value)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // handled
  }
}

function handleSelectionChange(rows: AiModelConfig[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条数据吗？`, '提示', { type: 'warning' })
    await deleteModelConfigBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

async function handleDelete(row: AiModelConfig) {
  try {
    await ElMessageBox.confirm(`确定要删除模型配置 "${row.modelName}" 吗？`, '提示', { type: 'warning' })
    await deleteModelConfigApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

const statusTypeMap: Record<number, string> = {
  1: 'success',
  0: 'danger',
}

async function loadProviderOptions() {
  try {
    const res = await getModelProviderOptionsApi()
    providerOptions.value = res.data || []
  } catch {
    ElMessage.error('加载模型来源失败')
  }
}

onMounted(() => {
  loadProviderOptions()
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="模型来源">
          <el-select v-model="searchForm.provider" placeholder="请选择模型来源" clearable style="width: 160px">
            <el-option v-for="opt in providerOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="searchForm.modelType" placeholder="请选择模型类型" clearable style="width: 140px">
            <el-option v-for="opt in modelTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="searchForm.modelName" placeholder="请输入模型名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'ai:modelConfig:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'ai:modelConfig:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'ai:modelConfig:delete'" type="primary" :icon="Delete" @click="handleBatchDelete">删除</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="provider" label="模型来源" width="120">
          <template #default="{ row }">{{ providerLabel(row.provider) }}</template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型名称" width="200" />
        <el-table-column prop="modelType" label="模型类型" width="110">
          <template #default="{ row }">
            <el-tag>{{ modelTypeOptions.find((o) => o.value === row.modelType)?.label || row.modelType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="API地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sort" label="模型排序" width="100" />
        <el-table-column label="支持图片" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.supportVision === 1 ? 'success' : 'info'" size="small">
              {{ row.supportVision === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否启用" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'ai:modelConfig:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'ai:modelConfig:delete'" type="primary" link @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" :top="'5vh'" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-height: 70vh; overflow-y: auto; padding-right: 10px;">
        <el-form-item label="模型来源" prop="provider">
          <el-select v-model="form.provider" placeholder="请选择模型来源" style="width: 100%" @change="handleProviderChange">
            <el-option v-for="opt in providerOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="请输入模型名称" style="width: 100%" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="form.modelType" placeholder="请选择模型类型" style="width: 100%">
            <el-option v-for="opt in modelTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="API密钥" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" :placeholder="editingId ? '留空表示不修改原 API 密钥' : '请输入API密钥'" style="width: 100%" />
          <div v-if="editingId && form.apiKey === ''" class="form-tip">
            当前配置{{ tableData.find((item) => item.id === editingId)?.hasApiKey ? '已' : '未' }}设置密钥，留空不修改。
          </div>
        </el-form-item>
        <el-form-item label="API地址">
          <el-input v-model="form.baseUrl" placeholder="请输入API基础地址" style="width: 100%" />
        </el-form-item>
        <el-form-item label="模型参数">
          <el-input v-model="form.modelParams" type="textarea" :rows="2" placeholder="请输入JSON格式的模型参数" />
        </el-form-item>
        <el-form-item label="支持图片">
          <el-switch v-model="form.supportVision" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="模型排序">
          <el-input-number v-model="form.sort" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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

.form-tip {
  margin-top: 4px;
  line-height: 18px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
