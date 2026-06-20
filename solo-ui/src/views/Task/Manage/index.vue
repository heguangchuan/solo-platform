<script lang="ts">
export default { name: 'TaskManage' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Close, Check } from '@element-plus/icons-vue'
import type { SysTask, TaskPageRequest, TaskRequest } from '@/types/api'
import {
  getTaskPageApi,
  addTaskApi,
  editTaskApi,
  deleteTaskApi,
  deleteTaskBatchApi,
  switchTaskStatusApi,
  runTaskApi,
} from '@/api/task'

const loading = ref(false)
const tableData = ref<SysTask[]>([])
const selectedRows = ref<SysTask[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<TaskPageRequest>({ page: 1, pageSize: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<string>('')
const form = ref<TaskRequest>({
  taskName: '',
  taskCode: '',
  cron: '',
  handlerName: 'exampleTask',
  taskParams: '',
  status: 0,
  remark: '',
})

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskCode: [{ required: true, message: '请输入任务编码', trigger: 'blur' }],
  cron: [{ required: true, message: '请输入Cron表达式', trigger: 'blur' }],
  handlerName: [{ required: true, message: '请输入任务处理器', trigger: 'blur' }],
}

const statusTypeMap: Record<number, string> = {
  1: 'success',
  0: 'danger',
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
    const res = await getTaskPageApi(params)
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

function resetForm() {
  editingId.value = ''
  form.value = {
    taskName: '',
    taskCode: '',
    cron: '',
    handlerName: 'exampleTask',
    taskParams: '',
    status: 0,
    remark: '',
  }
}

function handleAdd() {
  dialogTitle.value = '新增任务'
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: SysTask) {
  dialogTitle.value = '编辑任务'
  editingId.value = row.id
  form.value = {
    taskName: row.taskName,
    taskCode: row.taskCode,
    cron: row.cron,
    handlerName: row.handlerName,
    taskParams: row.taskParams,
    status: row.status,
    remark: row.remark,
  }
  dialogVisible.value = true
}

async function handleConfirm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    if (!editingId.value) {
      await addTaskApi(form.value)
    } else {
      await editTaskApi(editingId.value, form.value)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // handled
  }
}

async function handleToggleStatus(row: SysTask) {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await switchTaskStatusApi(row.id, nextStatus)
    ElMessage.success(nextStatus === 1 ? '已启用' : '已停用')
    fetchData()
  } catch {
    // handled
  }
}

async function handleRun(row: SysTask) {
  try {
    await ElMessageBox.confirm(`确定要立即执行任务 "${row.taskName}" 吗？`, '提示', { type: 'warning' })
    await runTaskApi(row.id)
    ElMessage.success('执行请求已提交')
  } catch {
    // cancelled or error
  }
}

function handleSelectionChange(rows: SysTask[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条任务吗？`, '提示', {
      type: 'warning',
    })
    await deleteTaskBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

async function handleDelete(row: SysTask) {
  try {
    await ElMessageBox.confirm(`确定要删除任务 "${row.taskName}" 吗？`, '提示', { type: 'warning' })
    await deleteTaskApi(row.id)
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
        <el-form-item label="任务名称">
          <el-input v-model="searchForm.taskName" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="任务编码">
          <el-input v-model="searchForm.taskCode" placeholder="请输入任务编码" clearable />
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'task:manage:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'task:manage:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'task:manage:delete'" type="primary" :icon="Delete" @click="handleBatchDelete">删除</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange" max-height="calc(100vh - 400px)">
        <el-table-column type="selection" width="55" / max-height="calc(100vh - 400px)">
        <el-table-column prop="taskName" label="任务名称" min-width="150" / max-height="calc(100vh - 400px)">
        <el-table-column prop="taskCode" label="任务编码" min-width="160" / max-height="calc(100vh - 400px)">
        <el-table-column prop="cron" label="Cron表达式" min-width="170" / max-height="calc(100vh - 400px)">
        <el-table-column prop="handlerName" label="处理器" min-width="140" / max-height="calc(100vh - 400px)">
        <el-table-column prop="taskParams" label="参数" min-width="180" show-overflow-tooltip / max-height="calc(100vh - 400px)">
        <el-table-column label="状态" width="90" max-height="calc(100vh - 400px)">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip / max-height="calc(100vh - 400px)">
        <el-table-column prop="createTime" label="创建时间" min-width="180" / max-height="calc(100vh - 400px)">
        <el-table-column label="操作" width="220" fixed="right" max-height="calc(100vh - 400px)">
          <template #default="{ row }">
            <el-button v-permission="'task:manage:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'task:manage:switch'" type="primary" link @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-permission="'task:manage:run'" type="primary" link @click="handleRun(row)">执行</el-button>
            <el-button v-permission="'task:manage:delete'" type="danger" link @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务编码" prop="taskCode">
          <el-input v-model="form.taskCode" placeholder="如：demo.task" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="Cron表达式" prop="cron">
          <el-input v-model="form.cron" placeholder="如：0/10 * * * * ?" />
        </el-form-item>
        <el-form-item label="任务处理器" prop="handlerName">
          <el-input v-model="form.handlerName" placeholder="如：exampleTask" />
        </el-form-item>
        <el-form-item label="任务参数">
          <el-input v-model="form.taskParams" type="textarea" :rows="3" placeholder="请输入JSON或普通文本参数" />
        </el-form-item>
        <el-form-item label="任务状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
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
