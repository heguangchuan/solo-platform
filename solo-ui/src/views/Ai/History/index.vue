<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getTaskListApi, type AiGenerationTask } from '@/api/ai'
import TaskDetailDialog from './components/TaskDetailDialog.vue'

defineOptions({ name: 'AiHistory' })

const detailDialog = ref<InstanceType<typeof TaskDetailDialog>>()
const loading = ref(false)
const tableData = ref<AiGenerationTask[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref({
  taskType: '',
  status: ''
})

const taskTypeOptions = [
  { label: '全部类型', value: '' },
  { label: '文本', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' }
]

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' }
]

const statusTypeMap: Record<string, string> = {
  SUCCESS: 'success',
  FAILED: 'danger',
  PROCESSING: 'warning',
  PENDING: 'info'
}

const taskTypeMap: Record<string, string> = {
  TEXT: '文本',
  IMAGE: '图片',
  VIDEO: '视频'
}

function formatTime(timeStr: string) {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.value = {
    taskType: '',
    status: ''
  }
  handleSearch()
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getTaskListApi({
      page: currentPage.value,
      pageSize: pageSize.value,
      taskType: searchForm.value.taskType || undefined,
      status: searchForm.value.status || undefined
    })
    tableData.value = res.data.rows || []
    total.value = res.data.total || 0
  } catch {
    ElMessage.error('加载任务列表失败')
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

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="任务类型">
          <el-select v-model="searchForm.taskType" placeholder="请选择" clearable style="width: 120px">
            <el-option
              v-for="item in taskTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 400px)">
        <el-table-column prop="taskType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ taskTypeMap[row.taskType] || row.taskType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : row.status === 'FAILED' ? '失败' : row.status === 'PROCESSING' ? '处理中' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型" min-width="180" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.modelName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="prompt" label="输入内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="tokens" label="消耗Token" width="100" align="center">
          <template #default="{ row }">
            {{ row.tokens || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="cost" label="成本" width="100" align="center">
          <template #default="{ row }">
            {{ row.cost ? `¥${row.cost.toFixed(4)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="finishTime" label="完成时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.finishTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="detailDialog?.open(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <TaskDetailDialog ref="detailDialog" />
  </div>
</template>

<style scoped lang="scss">
.search-card {
  :deep(.el-card__body) {
    padding-bottom: 0;
  }
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
