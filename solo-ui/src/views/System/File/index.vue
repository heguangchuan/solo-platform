<script lang="ts">
export default { name: 'SystemFile' }
</script>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Link, Upload } from '@element-plus/icons-vue'
import type { FilePageRequest, SysFile } from '@/types/api'
import { deleteFileApi, deleteFileBatchApi, getFilePageApi } from '@/api/file'

const loading = ref(false)
const tableData = ref<SysFile[]>([])
const selectedRows = ref<SysFile[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<FilePageRequest>({ page: 1, pageSize: 10 })

async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getFilePageApi(params)
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

function handleSelectionChange(rows: SysFile[]) {
  selectedRows.value = rows
}

/* ============ 上传弹窗 ============ */
const uploadVisible = ref(false)

function openUploadDialog() {
  uploadVisible.value = true
}

function handleUploadSuccess() {
  uploadVisible.value = false
  fetchData()
}

/* ============ 行操作 ============ */
async function handleDelete(row: SysFile) {
  try {
    await ElMessageBox.confirm(`确定要删除文件 "${row.originalName}" 吗？`, '提示', { type: 'warning' })
    await deleteFileApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 个文件吗？`, '提示', {
      type: 'warning',
    })
    await deleteFileBatchApi(selectedRows.value.map((r) => r.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleCopyUrl(row: SysFile) {
  try {
    await navigator.clipboard.writeText(row.url)
    ElMessage.success('已复制链接到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

/* ============ 工具：格式化文件大小 ============ */
function formatSize(bytes: number | undefined): string {
  if (bytes == null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 简单判定是不是图片，决定是否显示缩略图 */
onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="文件名称">
          <el-input v-model="searchForm.originalName" placeholder="请输入文件名称" clearable />
        </el-form-item>
        <el-form-item label="上传账号">
          <el-input v-model="searchForm.uploaderName" placeholder="请输入上传账号" clearable />
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'system:file:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'system:file:upload'" type="primary" :icon="Plus" @click="openUploadDialog">
          新增
        </el-button>
        <el-button v-permission="'system:file:delete'" type="danger" :icon="Delete" @click="handleBatchDelete">
          删除
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange" max-height="calc(100vh - 400px)">
        <el-table-column type="selection" />
        <el-table-column prop="originalName" label="文件名称" show-overflow-tooltip />
        <el-table-column prop="ext" label="文件类型">
          <template #default="{ row }">{{ row.ext || '—' }}</template>
        </el-table-column>
        <el-table-column label="文件大小">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传账号">
          <template #default="{ row }">{{ row.uploaderName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="复制链接">
                <el-button
                  type="primary"
                  link
                  :icon="Link"
                  @click="handleCopyUrl(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button
                  v-permission="'system:file:delete'"
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

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadVisible" title="上传文件" destroy-on-close :close-on-click-modal="false">
      <FileUpload button-text="选择并上传" plain :show-file-name="false" @success="handleUploadSuccess" />
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
