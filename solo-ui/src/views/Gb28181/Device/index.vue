<script lang="ts">
export default { name: 'Gb28181Device' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import type { GbDevice, GbDevicePageRequest } from '@/types/api'
import {
  getGbDevicePageApi,
  syncGbDeviceApi,
  deleteGbDeviceApi,
} from '@/api/gb28181'
import DeviceDetailDialog from './components/DeviceDetailDialog.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<GbDevice[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<GbDevicePageRequest>({ page: 1, pageSize: 10 })

async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getGbDevicePageApi(params)
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

async function handleSync(row: GbDevice) {
  try {
    await syncGbDeviceApi(row.deviceId)
    ElMessage.success('已触发设备信息和通道目录同步，请稍后刷新查看')
  } catch {
    // handled
  }
}

function handleViewChannels(row: GbDevice) {
  router.push({ path: '/gb28181/device-channel', query: { deviceId: row.deviceId } })
}

const detailDialogRef = ref<InstanceType<typeof DeviceDetailDialog> | null>(null)

function handleDetail(row: GbDevice) {
  detailDialogRef.value?.open(row)
}

async function handleDelete(row: GbDevice) {
  try {
    await ElMessageBox.confirm(`确定要删除设备 "${row.deviceId}" 的记录吗？（不影响实际设备）`, '提示', {
      type: 'warning',
    })
    await deleteGbDeviceApi(row.deviceId)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
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
        <el-form-item label="设备 ID">
          <el-input v-model="searchForm.deviceId" placeholder="国标 ID 模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="searchForm.name" placeholder="名称模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="在线" :value="1" />
            <el-option label="离线" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'gb28181:device:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 320px)">
        <el-table-column prop="deviceId" label="设备 ID" />
        <el-table-column prop="name" label="名称" show-overflow-tooltip />
        <el-table-column label="状态" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hostAddress" label="网络地址" />
        <el-table-column prop="channelCount" label="通道数" align="center" />
        <el-table-column prop="keepaliveTime" label="最近心跳" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'gb28181:device:list'"
              type="primary"
              link
              @click="handleDetail(row)"
            >详情</el-button>
            <el-button
              v-permission="'gb28181:device-channel:list'"
              type="primary"
              link
              @click="handleViewChannels(row)"
            >通道</el-button>
            <el-button
              v-permission="'gb28181:device:sync'"
              type="primary"
              link
              :disabled="row.status !== 1"
              @click="handleSync(row)"
            >同步</el-button>
            <el-button
              v-permission="'gb28181:device:delete'"
              type="primary"
              link
              @click="handleDelete(row)"
            >删除</el-button>
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

    <DeviceDetailDialog ref="detailDialogRef" />
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
