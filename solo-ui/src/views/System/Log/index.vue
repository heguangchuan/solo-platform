<script lang="ts">
export default { name: 'SystemLog' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, Download, Close } from '@element-plus/icons-vue'
import type { SysLog, LogPageRequest } from '@/types/api'
import {
  getLogPageApi,
  exportLogApi,
  deleteLogApi,
  deleteLogBatchApi,
} from '@/api/log'
import { downloadBlob } from '@/utils/download'

const loading = ref(false)
const tableData = ref<SysLog[]>([])
const selectedRows = ref<SysLog[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<LogPageRequest>({ page: 1, pageSize: 10 })
const timeRange = ref<[string, string] | null>(null)

const businessTypeOptions = [
  { label: '新增', value: 'ADD' },
  { label: '修改', value: 'EDIT' },
  { label: '删除', value: 'DELETE' },
  { label: '导出', value: 'EXPORT' },
  { label: '导入', value: 'IMPORT' },
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
  { label: '其他', value: 'OTHER' },
]

const businessTypeMap: Record<string, { label: string; type: string }> = {
  ADD: { label: '新增', type: 'success' },
  EDIT: { label: '修改', type: 'primary' },
  DELETE: { label: '删除', type: 'danger' },
  EXPORT: { label: '导出', type: 'warning' },
  IMPORT: { label: '导入', type: 'warning' },
  LOGIN: { label: '登录', type: 'info' },
  LOGOUT: { label: '登出', type: 'info' },
  OTHER: { label: '其他', type: 'info' },
}

const detailVisible = ref(false)
const detailRow = ref<SysLog | null>(null)

function buildSearchParams(): LogPageRequest {
  const params: LogPageRequest = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
  if (timeRange.value && timeRange.value.length === 2) {
    params.startTime = timeRange.value[0]
    params.endTime = timeRange.value[1]
  } else {
    params.startTime = undefined
    params.endTime = undefined
  }
  return params
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getLogPageApi(buildSearchParams())
    tableData.value = res.data.rows
    total.value = res.data.total
  } catch {
    // handled
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
  timeRange.value = null
  handleSearch()
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

function handleSelectionChange(rows: SysLog[]) {
  selectedRows.value = rows
}

async function handleDelete(row: SysLog) {
  try {
    await ElMessageBox.confirm('确定要删除该日志吗？', '提示', { type: 'warning' })
    await deleteLogApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的日志')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条日志吗？`, '提示', {
      type: 'warning',
    })
    await deleteLogBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleExport() {
  const ids = selectedRows.value.map((row) => row.id)
  const params = { ...buildSearchParams(), ids: ids.length ? ids : undefined }
  const res = await exportLogApi(params)
  downloadBlob(res.data, '操作日志.xlsx')
}

function handleViewDetail(row: SysLog) {
  detailRow.value = row
  detailVisible.value = true
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="操作人员">
          <el-input v-model="searchForm.operatorName" placeholder="请输入操作人员" clearable />
        </el-form-item>
        <el-form-item label="业务模块">
          <el-input v-model="searchForm.module" placeholder="请输入业务模块" clearable />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="searchForm.businessType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="opt in businessTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="响应状态">
          <el-select v-model="searchForm.responseStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户来源">
          <el-select v-model="searchForm.clientSource" placeholder="全部" clearable style="width: 120px">
            <el-option label="web" value="web" />
            <el-option label="app" value="app" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'system:log:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'system:log:delete'" type="primary" :icon="Delete" @click="handleBatchDelete">删除</el-button>
        <el-button v-permission="'system:log:export'" type="primary" :icon="Download" @click="handleExport">导出</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="operatorName" label="操作人员" min-width="120" />
        <el-table-column prop="module" label="业务模块" min-width="120" />
        <el-table-column label="业务类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(businessTypeMap[row.businessType] || { type: 'info' }).type" size="small">
              {{ (businessTypeMap[row.businessType] || { label: row.businessType }).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="操作描述" min-width="160" show-overflow-tooltip />
        <el-table-column prop="requestUrl" label="请求地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="响应状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.responseStatus === 1 ? 'success' : 'danger'" size="small">
              {{ row.responseStatus === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="响应耗时" width="100" align="right">
          <template #default="{ row }">{{ row.costMs }} ms</template>
        </el-table-column>
        <el-table-column prop="clientSource" label="客户来源" width="90" align="center" />
        <el-table-column prop="clientIp" label="客户地址" min-width="140" />
        <el-table-column prop="createTime" label="操作时间" min-width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)">详情</el-button>
            <el-button v-permission="'system:log:delete'" type="primary" link @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="detailVisible" title="日志详情" width="720px" destroy-on-close>
      <el-descriptions v-if="detailRow" :column="2" border>
        <el-descriptions-item label="操作人员">{{ detailRow.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detailRow.createTime }}</el-descriptions-item>
        <el-descriptions-item label="业务模块">{{ detailRow.module }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">
          {{ (businessTypeMap[detailRow.businessType] || { label: detailRow.businessType }).label }}
        </el-descriptions-item>
        <el-descriptions-item label="操作描述" :span="2">{{ detailRow.description }}</el-descriptions-item>
        <el-descriptions-item label="请求方式">{{ detailRow.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="请求地址">{{ detailRow.requestUrl }}</el-descriptions-item>
        <el-descriptions-item label="响应状态">
          {{ detailRow.responseStatus === 1 ? '成功' : '失败' }}
        </el-descriptions-item>
        <el-descriptions-item label="响应耗时">{{ detailRow.costMs }} ms</el-descriptions-item>
        <el-descriptions-item label="客户来源">{{ detailRow.clientSource }}</el-descriptions-item>
        <el-descriptions-item label="客户地址">{{ detailRow.clientIp }}</el-descriptions-item>
        <el-descriptions-item label="客户标识" :span="2">{{ detailRow.userAgent }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="param-block">{{ detailRow.requestParam || '—' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailRow.errorMsg" label="错误信息" :span="2">
          <pre class="param-block error">{{ detailRow.errorMsg }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button :icon="Close" @click="detailVisible = false">关闭</el-button>
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

.param-block {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 220px;
  overflow: auto;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.param-block.error {
  color: var(--el-color-danger);
}
</style>
