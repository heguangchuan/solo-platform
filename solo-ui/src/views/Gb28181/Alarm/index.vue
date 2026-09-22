<script lang="ts">
export default { name: 'Gb28181Alarm' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, View, Check, Delete } from '@element-plus/icons-vue'
import type {
  GbDeviceAlarm,
  GbDeviceAlarmPageRequest,
} from '@/types/api'
import {
  getGbDeviceAlarmPageApi,
  getGbDeviceAlarmApi,
  handleGbDeviceAlarmApi,
  deleteGbDeviceAlarmApi,
} from '@/api/gb28181'

const loading = ref(false)
const tableData = ref<GbDeviceAlarm[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const searchForm = ref<GbDeviceAlarmPageRequest>({ page: 1, pageSize: 20 })
/** el-date-picker 绑定的时间区间 */
const timeRange = ref<[string, string] | null>(null)

/** 报警级别：1 一级(最严重) ~ 4 四级(提示) */
const priorityOptions = [
  { value: 1, label: '一级' },
  { value: 2, label: '二级' },
  { value: 3, label: '三级' },
  { value: 4, label: '四级' },
]

/** 处置状态 */
const handleStatusOptions = [
  { value: 0, label: '未处理' },
  { value: 1, label: '已确认' },
  { value: 2, label: '已处置' },
  { value: 3, label: '误报' },
]

const priorityTagMap: Record<number, string> = {
  1: 'danger',
  2: 'warning',
  3: 'primary',
  4: 'info',
}

const handleStatusTagMap: Record<number, string> = {
  0: 'danger',
  1: 'warning',
  2: 'success',
  3: 'info',
}

function labelOf(options: { value: number; label: string }[], value?: number): string {
  if (value === undefined || value === null) return '-'
  return options.find((o) => o.value === value)?.label ?? String(value)
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.value = { page: 1, pageSize: 20 }
  timeRange.value = null
  handleSearch()
}

async function fetchData() {
  loading.value = true
  try {
    const params: GbDeviceAlarmPageRequest = {
      ...searchForm.value,
      page: currentPage.value,
      pageSize: pageSize.value,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
    }
    const res = await getGbDeviceAlarmPageApi(params)
    tableData.value = res.data.rows
    total.value = res.data.total
  } catch {
    // handled by interceptor
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

// ==================== 详情 ====================

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<GbDeviceAlarm | null>(null)

async function handleDetail(row: GbDeviceAlarm) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getGbDeviceAlarmApi(row.id)
    detail.value = res.data
  } catch {
    // handled
  } finally {
    detailLoading.value = false
  }
}

// ==================== 处置 ====================

const handleVisible = ref(false)
const handleTarget = ref<GbDeviceAlarm | null>(null)
const handleStatus = ref<number>(1)
const handleRemark = ref('')

const handleStatusChoices = [
  { value: 1, label: '已确认', desc: '已知悉该报警，正在跟进' },
  { value: 2, label: '已处置', desc: '报警已处理完毕' },
  { value: 3, label: '误报', desc: '经核实为误报，无需处理' },
]

function openHandle(row: GbDeviceAlarm) {
  handleTarget.value = row
  // 已确认的报警再次处置时默认落到"已处置"
  handleStatus.value = row.handleStatus === 1 ? 2 : 1
  handleRemark.value = row.handleRemark || ''
  handleVisible.value = true
}

async function handleConfirm() {
  if (!handleTarget.value) return
  try {
    await handleGbDeviceAlarmApi({
      id: handleTarget.value.id,
      handleStatus: handleStatus.value,
      handleRemark: handleRemark.value || undefined,
    })
    ElMessage.success('处置成功')
    handleVisible.value = false
    fetchData()
  } catch {
    // handled
  }
}

// ==================== 删除 ====================

async function handleDelete(row: GbDeviceAlarm) {
  try {
    await ElMessageBox.confirm(
      `确定要删除该报警记录吗？删除后不可恢复。`,
      '提示',
      { type: 'warning', confirmButtonText: '确定删除' },
    )
    await deleteGbDeviceAlarmApi(row.id)
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
        <el-form-item label="设备国标ID">
          <el-input
            v-model="searchForm.deviceId"
            placeholder="请输入设备国标ID"
            clearable
          />
        </el-form-item>
        <el-form-item label="通道国标ID">
          <el-input
            v-model="searchForm.channelId"
            placeholder="请输入通道国标ID"
            clearable
          />
        </el-form-item>
        <el-form-item label="报警级别">
          <el-select
            v-model="searchForm.alarmPriority"
            placeholder="全部级别"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="o in priorityOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处置状态">
          <el-select
            v-model="searchForm.handleStatus"
            placeholder="全部状态"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="o in handleStatusOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报警时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            v-permission="'gb28181:alarm:list'"
            type="primary"
            :icon="Search"
            @click="handleSearch"
          >查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <el-table
        :data="tableData"
        v-loading="loading"
        border
        stripe
        max-height="calc(100vh - 360px)"
      >
        <el-table-column prop="deviceId" label="设备国标ID" min-width="160" show-overflow-tooltip />
        <el-table-column prop="channelName" label="通道名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.channelName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="channelId" label="通道国标ID" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.channelId || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="报警级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.alarmPriority" :type="priorityTagMap[row.alarmPriority] || 'info'">
              {{ labelOf(priorityOptions, row.alarmPriority) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="报警方式" width="110" align="center">
          <template #default="{ row }">
            {{ row.alarmMethodDesc || row.alarmMethod || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="alarmDescription" label="报警描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.alarmDescription || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="alarmTime" label="报警时间" width="170">
          <template #default="{ row }">
            {{ row.alarmTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="处置状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="handleStatusTagMap[row.handleStatus] || 'info'">
              {{ labelOf(handleStatusOptions, row.handleStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip content="详情">
              <el-button
                type="primary"
                link
                :icon="View"
                @click="handleDetail(row)"
              />
            </el-tooltip>
            <el-tooltip content="处置">
              <el-button
                v-permission="'gb28181:alarm:handle'"
                type="primary"
                link
                :icon="Check"
                @click="openHandle(row)"
              />
            </el-tooltip>
            <el-tooltip content="删除">
              <el-button
                v-permission="'gb28181:alarm:delete'"
                type="danger"
                link
                :icon="Delete"
                @click="handleDelete(row)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="报警详情" width="640px" destroy-on-close>
      <el-descriptions v-loading="detailLoading" :column="2" border>
        <el-descriptions-item label="设备国标ID" :span="2">
          {{ detail?.deviceId }}
        </el-descriptions-item>
        <el-descriptions-item label="通道名称">
          {{ detail?.channelName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="通道国标ID">
          {{ detail?.channelId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="报警级别">
          <el-tag v-if="detail?.alarmPriority" :type="priorityTagMap[detail.alarmPriority] || 'info'">
            {{ labelOf(priorityOptions, detail?.alarmPriority) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="报警方式">
          {{ detail?.alarmMethodDesc || detail?.alarmMethod || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="报警类型">
          {{ detail?.alarmType || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="报警时间">
          {{ detail?.alarmTime || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="接收时间">
          {{ detail?.receiveTime || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="入库时间">
          {{ detail?.createTime || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="报警描述" :span="2">
          {{ detail?.alarmDescription || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="经度">
          {{ detail?.longitude ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="纬度">
          {{ detail?.latitude ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="处置状态">
          <el-tag :type="handleStatusTagMap[detail?.handleStatus ?? 0] || 'info'">
            {{ labelOf(handleStatusOptions, detail?.handleStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="处置人">
          {{ detail?.handleUser || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="处置时间">
          {{ detail?.handleTime || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="处置备注" :span="2">
          {{ detail?.handleRemark || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 处置 -->
    <el-dialog
      v-model="handleVisible"
      title="报警处置"
      width="480px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div v-if="handleTarget" class="handle-head">
        <el-tag :type="priorityTagMap[handleTarget.alarmPriority || 4] || 'info'">
          {{ labelOf(priorityOptions, handleTarget.alarmPriority) }}
        </el-tag>
        <span class="handle-head-name">
          {{ handleTarget.channelName || handleTarget.channelId || handleTarget.deviceId }}
        </span>
      </div>

      <el-radio-group v-model="handleStatus" class="handle-radio-group">
        <el-radio
          v-for="c in handleStatusChoices"
          :key="c.value"
          :value="c.value"
          class="handle-radio"
        >
          <span class="handle-radio-label">{{ c.label }}</span>
          <span class="handle-radio-desc">{{ c.desc }}</span>
        </el-radio>
      </el-radio-group>

      <el-input
        v-model="handleRemark"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="请输入处置备注（选填）"
      />

      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>
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

.handle-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.handle-head-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.handle-radio-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 18px;
  width: 100%;
}

.handle-radio {
  display: flex;
  align-items: center;
  height: auto;
  padding: 10px 12px;
  margin-right: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  width: 100%;
}

.handle-radio.is-checked {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.handle-radio-label {
  font-weight: 600;
  margin-right: 10px;
}

.handle-radio-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
