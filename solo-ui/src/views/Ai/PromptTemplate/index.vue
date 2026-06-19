<script lang="ts">
export default { name: 'AiPromptTemplate' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Close, Check, View } from '@element-plus/icons-vue'
import type { AiPromptTemplate, AiPromptTemplateRequest, AiPromptTemplatePageRequest } from '@/types/api'
import {
  getPromptTemplatePageApi,
  addPromptTemplateApi,
  editPromptTemplateApi,
  deletePromptTemplateApi,
  deletePromptTemplateBatchApi,
} from '@/api/ai'

const loading = ref(false)
const tableData = ref<AiPromptTemplate[]>([])
const selectedRows = ref<AiPromptTemplate[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<AiPromptTemplatePageRequest>({ page: 1, pageSize: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<string>('')
const form = ref<AiPromptTemplateRequest>({
  templateName: '',
  templateCode: '',
  templateType: 'TEXT',
  content: '',
  variables: '',
  example: '',
  status: 1,
  sort: 1,
  remark: '',
})

const previewVisible = ref(false)
const previewContent = ref('')

const rules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateType: [{ required: true, message: '请选择模板类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入模板内容', trigger: 'blur' }],
}

const templateTypeOptions = [
  { label: '对话', value: 'CHAT' },
  { label: '文案', value: 'COPYWRITING' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '翻译', value: 'TRANSLATION' },
]

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
    const res = await getPromptTemplatePageApi(params)
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
  dialogTitle.value = '新增提示词模板'
  editingId.value = ''
  form.value = {
    templateName: '',
    templateCode: '',
    templateType: 'CHAT',
      content: '',
    variables: '',
    example: '',
    status: 1,
    sort: 0,
    remark: '',
  }
  dialogVisible.value = true
}

function handleEdit(row: AiPromptTemplate) {
  dialogTitle.value = '编辑提示词模板'
  editingId.value = row.id
  form.value = {
    templateName: row.templateName,
    templateCode: row.templateCode,
    templateType: row.templateType,
    content: row.content,
    variables: row.variables,
    example: row.example,
    status: row.status,
    sort: row.sort,
    remark: row.remark,
  }
  dialogVisible.value = true
}

function handlePreview(row: AiPromptTemplate) {
  previewContent.value = row.content
  previewVisible.value = true
}

async function handleConfirm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    if (!editingId.value) {
      await addPromptTemplateApi(form.value)
    } else {
      await editPromptTemplateApi(editingId.value, form.value)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // handled
  }
}

function handleSelectionChange(rows: AiPromptTemplate[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条数据吗？`, '提示', { type: 'warning' })
    await deletePromptTemplateBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

async function handleDelete(row: AiPromptTemplate) {
  try {
    await ElMessageBox.confirm(`确定要删除模板 "${row.templateName}" 吗？`, '提示', { type: 'warning' })
    await deletePromptTemplateApi(row.id)
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

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="模板名称">
          <el-input v-model="searchForm.templateName" placeholder="请输入模板名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="模板编码">
          <el-input v-model="searchForm.templateCode" placeholder="请输入模板编码" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="模板类型">
          <el-select v-model="searchForm.templateType" placeholder="请选择模板类型" clearable style="width: 140px">
            <el-option v-for="opt in templateTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否启用">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'ai:promptTemplate:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'ai:promptTemplate:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'ai:promptTemplate:delete'" type="primary" :icon="Delete" @click="handleBatchDelete">删除</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="templateName" label="模板名称" width="150" />
        <el-table-column prop="templateCode" label="模板编码" width="150" />
        <el-table-column prop="templateType" label="模板类型" width="110">
          <template #default="{ row }">
            <el-tag>{{ templateTypeOptions.find((o) => o.value === row.templateType)?.label || row.templateType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="模板内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="sort" label="模板排序" width="100" />
        <el-table-column label="是否启用" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handlePreview(row)">预览</el-button>
            <el-button v-permission="'ai:promptTemplate:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'ai:promptTemplate:delete'" type="primary" link @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" :top="'5vh'" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-height: 70vh; overflow-y: auto; padding-right: 10px;">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="请输入模板名称" style="width: 100%" />
        </el-form-item>
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="form.templateCode" placeholder="请输入模板编码" style="width: 100%" />
        </el-form-item>
        <el-form-item label="模板类型" prop="templateType">
          <el-select v-model="form.templateType" placeholder="请选择模板类型" style="width: 100%">
            <el-option v-for="opt in templateTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="请输入模板内容，支持{{变量名}}占位符" />
        </el-form-item>
        <el-form-item label="变量定义">
          <el-input v-model="form.variables" type="textarea" :rows="2" placeholder="请输入JSON格式的变量定义" />
        </el-form-item>
        <el-form-item label="示例输出">
          <el-input v-model="form.example" type="textarea" :rows="2" placeholder="请输入示例输出" />
        </el-form-item>
        <el-form-item label="模板排序">
          <el-input-number v-model="form.sort" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注信息">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="提示词内容预览" width="600px" :close-on-click-modal="false">
      <el-input type="textarea" :model-value="previewContent" :rows="12" readonly />
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
