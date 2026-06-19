<script lang="ts">
export default { name: 'AiAsset' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Close, Check, View } from '@element-plus/icons-vue'
import type { AiAsset, AiAssetRequest, AiAssetPageRequest } from '@/types/api'
import {
  getAssetPageApi,
  addAssetApi,
  editAssetApi,
  deleteAssetApi,
  deleteAssetBatchApi,
} from '@/api/ai'

const loading = ref(false)
const tableData = ref<AiAsset[]>([])
const selectedRows = ref<AiAsset[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<AiAssetPageRequest>({ page: 1, pageSize: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<string>('')
const form = ref<AiAssetRequest>({
  assetType: 'IMAGE',
  fileName: '',
  fileUrl: '',
  coverUrl: '',
  thumbnailUrl: '',
  mimeType: '',
  ext: '',
  size: 0,
  width: 0,
  height: 0,
  prompt: '',
  modelName: '',
  status: 1,
  remark: '',
})

const previewVisible = ref(false)
const previewUrl = ref('')

const rules = {
  assetType: [{ required: true, message: '请选择素材类型', trigger: 'change' }],
  fileName: [{ required: true, message: '请输入文件名', trigger: 'blur' }],
  fileUrl: [{ required: true, message: '请输入文件URL', trigger: 'blur' }],
}

const assetTypeOptions = [
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '音频', value: 'AUDIO' },
  { label: '文档', value: 'DOCUMENT' },
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
    const res = await getAssetPageApi(params)
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
  dialogTitle.value = '新增素材'
  editingId.value = ''
  form.value = {
    assetType: 'IMAGE',
    fileName: '',
    fileUrl: '',
    coverUrl: '',
    thumbnailUrl: '',
    mimeType: '',
    ext: '',
    size: 0,
    width: 0,
    height: 0,
    prompt: '',
    modelName: '',
    status: 1,
    remark: '',
  }
  dialogVisible.value = true
}

function handleEdit(row: AiAsset) {
  dialogTitle.value = '编辑素材'
  editingId.value = row.id
  form.value = {
    assetType: row.assetType,
    fileName: row.fileName,
    fileUrl: row.fileUrl,
    coverUrl: row.coverUrl,
    thumbnailUrl: row.thumbnailUrl,
    mimeType: row.mimeType,
    ext: row.ext,
    size: row.size,
    width: row.width,
    height: row.height,
    prompt: row.prompt,
    modelName: row.modelName,
    status: row.status,
    remark: row.remark,
  }
  dialogVisible.value = true
}

function handlePreview(row: AiAsset) {
  previewUrl.value = row.fileUrl
  previewVisible.value = true
}

async function handleConfirm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    if (!editingId.value) {
      await addAssetApi(form.value)
    } else {
      await editAssetApi(editingId.value, form.value)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // handled
  }
}

function handleSelectionChange(rows: AiAsset[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条数据吗？`, '提示', { type: 'warning' })
    await deleteAssetBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

async function handleDelete(row: AiAsset) {
  try {
    await ElMessageBox.confirm(`确定要删除素材 "${row.fileName}" 吗？`, '提示', { type: 'warning' })
    await deleteAssetApi(row.id)
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

function formatSize(bytes?: number): string {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="文件名称">
          <el-input v-model="searchForm.fileName" placeholder="请输入文件名" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="素材类型">
          <el-select v-model="searchForm.assetType" placeholder="请选择素材类型" clearable style="width: 140px">
            <el-option v-for="opt in assetTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否有效">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="有效" :value="1" />
            <el-option label="无效" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'ai:asset:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'ai:asset:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'ai:asset:delete'" type="primary" :icon="Delete" @click="handleBatchDelete">删除</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column label="预览" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="row.assetType === 'IMAGE'" type="primary" link :icon="View" @click="handlePreview(row)">查看</el-button>
            <el-tag v-else size="small">{{ row.assetType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="assetType" label="素材类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ assetTypeOptions.find((o) => o.value === row.assetType)?.label || row.assetType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="尺寸" width="100">
          <template #default="{ row }">{{ row.width && row.height ? `${row.width}×${row.height}` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="modelName" label="生成模型" width="120" />
        <el-table-column prop="prompt" label="生成指令" min-width="150" show-overflow-tooltip />
        <el-table-column label="是否有效" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'" size="small">
              {{ row.status === 1 ? '有效' : '无效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'ai:asset:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'ai:asset:delete'" type="primary" link @click="handleDelete(row)">删除</el-button>
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
        <el-form-item label="素材类型" prop="assetType">
          <el-select v-model="form.assetType" placeholder="请选择素材类型" style="width: 100%">
            <el-option v-for="opt in assetTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件名称" prop="fileName">
          <el-input v-model="form.fileName" placeholder="请输入文件名" style="width: 100%" />
        </el-form-item>
        <el-form-item label="文件地址" prop="fileUrl">
          <el-input v-model="form.fileUrl" placeholder="请输入文件访问URL" style="width: 100%" />
        </el-form-item>
        <el-form-item label="封面地址">
          <el-input v-model="form.coverUrl" placeholder="请输入封面URL" style="width: 100%" />
        </el-form-item>
        <el-form-item label="缩略图地址">
          <el-input v-model="form.thumbnailUrl" placeholder="请输入缩略图URL" style="width: 100%" />
        </el-form-item>
        <el-form-item label="MIME类型">
          <el-input v-model="form.mimeType" placeholder="请输入MIME类型" style="width: 100%" />
        </el-form-item>
        <el-form-item label="文件扩展名">
          <el-input v-model="form.ext" placeholder="请输入文件扩展名" style="width: 100%" />
        </el-form-item>
        <el-form-item label="文件大小">
          <el-input-number v-model="form.size" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="图片宽度">
          <el-input-number v-model="form.width" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="图片高度">
          <el-input-number v-model="form.height" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生成指令">
          <el-input v-model="form.prompt" type="textarea" :rows="2" placeholder="请输入生成指令" />
        </el-form-item>
        <el-form-item label="生成模型">
          <el-input v-model="form.modelName" placeholder="请输入生成模型名称" style="width: 100%" />
        </el-form-item>
        <el-form-item label="是否有效">
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

    <el-dialog v-model="previewVisible" title="图片预览" width="700px" :close-on-click-modal="false">
      <img :src="previewUrl" alt="预览图片" style="width: 100%; display: block" />
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
