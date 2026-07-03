<script lang="ts">
export default { name: 'AiAsset' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Close, Check } from '@element-plus/icons-vue'
import type { AiAsset, AiAssetRequest, AiAssetPageRequest, SysFile } from '@/types/api'
import FileUpload from '@/components/FileUpload.vue'
import {
  getAssetPageApi,
  addAssetApi,
  editAssetApi,
  deleteAssetApi,
  deleteAssetBatchApi,
} from '@/api/ai'

const loading = ref(false)
const tableData = ref<AiAsset[]>([])
const selectedIds = ref<string[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)

const searchForm = ref<AiAssetPageRequest>({ page: 1, pageSize: 12 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<string>('')
const form = ref<AiAssetRequest>({
  assetType: 'IMAGE',
  fileName: '',
  fileUrl: '',
  coverUrl: '',
})

const rules = {
  assetType: [{ required: true, message: '请选择素材类型', trigger: 'change' }],
  fileName: [{ required: true, message: '请输入素材名称', trigger: 'blur' }],
  fileUrl: [{ required: true, message: '请上传素材文件', trigger: 'change' }],
}

const assetTypeOptions = [
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '音频', value: 'AUDIO' },
  { label: '文档', value: 'DOCUMENT' },
]

const sourceOptions = [
  { label: '系统上传', value: 'SYSTEM_UPLOAD' },
  { label: '模型生成', value: 'MODEL_GENERATE' },
]

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.value = { page: 1, pageSize: 12 }
  handleSearch()
}

async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getAssetPageApi(params)
    tableData.value = res.data.rows
    total.value = res.data.total
    selectedIds.value = []
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
  }
  dialogVisible.value = true
}

function handleUploadSuccess(file: SysFile) {
  form.value.fileUrl = file.url
  if (!form.value.fileName) form.value.fileName = file.originalName
}

function handleCoverSuccess(file: SysFile) {
  form.value.coverUrl = file.url
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

function toggleSelect(id: string) {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) selectedIds.value.push(id)
  else selectedIds.value.splice(idx, 1)
}

async function handleBatchDelete() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 条数据吗？`, '提示', { type: 'warning' })
    await deleteAssetBatchApi(selectedIds.value)
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

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="素材名称">
          <el-input v-model="searchForm.fileName" placeholder="请输入素材名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="素材类型">
          <el-select v-model="searchForm.assetType" placeholder="请选择素材类型" clearable style="width: 140px">
            <el-option v-for="opt in assetTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="素材来源">
          <el-select v-model="searchForm.source" placeholder="请选择素材来源" clearable style="width: 140px">
            <el-option v-for="opt in sourceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
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
        <span v-if="selectedIds.length" class="selected-tip">已选 {{ selectedIds.length }} 项</span>
      </div>

      <div v-loading="loading" class="asset-grid-wrap">
        <div class="asset-grid">
          <div v-for="row in tableData" :key="row.id" class="asset-card" :class="{ 'is-selected': selectedIds.includes(row.id) }">
            <el-checkbox class="card-check" :model-value="selectedIds.includes(row.id)" @change="() => toggleSelect(row.id)" />
            <div class="card-cover">
              <el-image
                v-if="row.assetType === 'IMAGE' || row.coverUrl"
                :src="row.assetType === 'IMAGE' ? row.fileUrl : row.coverUrl"
                :preview-src-list="[row.assetType === 'IMAGE' ? row.fileUrl : row.coverUrl]"
                fit="cover"
                class="cover-img"
                preview-teleported
              />
              <div v-else class="cover-placeholder">
                <el-tag size="large">{{ assetTypeOptions.find((o) => o.value === row.assetType)?.label || row.assetType }}</el-tag>
              </div>
              <el-tag class="cover-type" size="small">{{ assetTypeOptions.find((o) => o.value === row.assetType)?.label || row.assetType }}</el-tag>
            </div>
            <div class="card-body">
              <div class="card-name" :title="row.fileName">{{ row.fileName }}</div>
              <div class="card-meta">
                <el-tag size="small" type="info">{{ sourceOptions.find((o) => o.value === row.source)?.label || row.source }}</el-tag>
                <span class="card-time">{{ row.createTime }}</span>
              </div>
            </div>
            <div class="card-actions">
              <el-button v-permission="'ai:asset:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button v-permission="'ai:asset:delete'" type="danger" link @click="handleDelete(row)">删除</el-button>
            </div>
          </div>
          <el-empty v-if="!loading && !tableData.length" description="暂无素材" style="grid-column: 1 / -1" />
        </div>
      </div>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="素材类型" prop="assetType">
          <el-select v-model="form.assetType" placeholder="请选择素材类型" style="width: 100%">
            <el-option v-for="opt in assetTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="素材名称" prop="fileName">
          <el-input v-model="form.fileName" placeholder="请输入素材名称" style="width: 100%" />
        </el-form-item>
        <el-form-item label="素材文件" prop="fileUrl">
          <FileUpload v-model="form.fileUrl" button-text="上传素材" @success="handleUploadSuccess" />
        </el-form-item>
        <el-form-item v-if="form.assetType !== 'IMAGE'" label="素材封面">
          <FileUpload v-model="form.coverUrl" accept="image/*" button-text="上传封面" @success="handleCoverSuccess" />
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

.selected-tip {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.asset-grid-wrap {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
  min-height: 240px;
  background: var(--el-fill-color-blank);
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.asset-card {
  position: relative;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color);
  transition: box-shadow 0.2s, border-color 0.2s;
}

.asset-card:hover {
  box-shadow: var(--el-box-shadow-light);
}

.asset-card.is-selected {
  border-color: var(--el-color-primary);
}

.card-check {
  position: absolute;
  top: 6px;
  left: 8px;
  z-index: 2;
  background: rgba(255, 255, 255, 0.75);
  border-radius: 4px;
  padding: 0 4px;
}

.card-cover {
  position: relative;
  width: 100%;
  height: 160px;
  background: var(--el-fill-color-light);
}

.cover-img {
  width: 100%;
  height: 100%;
  cursor: zoom-in;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-type {
  position: absolute;
  right: 6px;
  bottom: 6px;
}

.card-body {
  padding: 8px 10px 4px;
}

.card-name {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: 6px;
}

.card-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  padding: 0 10px 8px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
