<script lang="ts">
export default { name: 'TaskLog' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { SysTaskLog, TaskLogPageRequest } from '@/types/api'
import { getTaskLogPageApi } from '@/api/task'
import { Search, Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const tableData = ref<SysTaskLog[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const searchForm = ref<TaskLogPageRequest>({ page: 1, pageSize: 10 })

const resultStatusTypeMap: Record<number, string> = {
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
    const res = await getTaskLogPageApi(params)
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
        <el-form-item label="执行结果">
          <el-select v-model="searchForm.resultStatus" placeholder="请选择" clearable style="width: 120px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'task:log:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="taskName" label="任务名称" min-width="140" />
        <el-table-column prop="taskCode" label="任务编码" min-width="150" />
        <el-table-column prop="handlerName" label="处理器" min-width="130" />
        <el-table-column prop="taskParams" label="参数" min-width="180" show-overflow-tooltip />
        <el-table-column prop="executionTime" label="执行时间" min-width="170" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="110" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="resultStatusTypeMap[row.resultStatus]">{{ row.resultStatus === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="220" show-overflow-tooltip />
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
  </div>
</template>

<style scoped>
.search-card {
  margin-bottom: 0;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
