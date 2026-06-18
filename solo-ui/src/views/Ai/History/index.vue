<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getTaskListApi, type AiGenerationTask } from '@/api/ai'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import TaskDetailDialog from './components/TaskDetailDialog.vue'

defineOptions({ name: 'AiHistory' })

const detailDialog = ref<InstanceType<typeof TaskDetailDialog>>()
const loading = ref(false)
const tasks = ref<AiGenerationTask[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const taskType = ref('')
const status = ref('')

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

const getStatusTagType = (status: string) => {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'PROCESSING':
      return 'warning'
    case 'PENDING':
      return 'info'
    default:
      return ''
  }
}

const getStatusText = (status: string) => {
  const item = statusOptions.find((o) => o.value === status)
  return item?.label || status
}

const getTaskTypeText = (type: string) => {
  const item = taskTypeOptions.find((o) => o.value === type)
  return item?.label || type
}

const formatTime = (timeStr: string) => {
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

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await getTaskListApi({
      page: currentPage.value,
      pageSize: pageSize.value,
      taskType: taskType.value || undefined,
      status: status.value || undefined
    })
    tasks.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    ElMessage.error('加载任务列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadTasks()
}

const handleReset = () => {
  taskType.value = ''
  status.value = ''
  handleSearch()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  loadTasks()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadTasks()
}

onMounted(() => {
  loadTasks()
})
</script>

<template>
  <div class="ai-history">
    <el-card shadow="never">
      <!-- 搜索栏 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="任务类型">
          <el-select v-model="taskType" placeholder="请选择" style="width: 140px">
            <el-option
              v-for="item in taskTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status" placeholder="请选择" style="width: 140px">
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

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tasks" stripe>
        <el-table-column prop="id" label="任务ID" width="80" align="center" />
        <el-table-column prop="taskType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ getTaskTypeText(row.taskType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型" width="120" align="center" />
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
            <el-button type="primary" link size="small" @click="detailDialog?.open(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 详情弹窗 -->
    <TaskDetailDialog ref="detailDialog" />
  </div>
</template>

<style scoped lang="scss">
.ai-history {
  .search-form {
    margin-bottom: 16px;
  }

  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
}
</style>
