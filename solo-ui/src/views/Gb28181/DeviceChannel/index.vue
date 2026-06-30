<script lang="ts">
export default { name: 'Gb28181DeviceChannel' }
</script>
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Refresh, Back, VideoPlay } from '@element-plus/icons-vue'
import type { GbDeviceChannel, GbDeviceChannelPageRequest } from '@/types/api'
import { getGbDeviceChannelPageApi } from '@/api/gb28181'
import PlayDialog from '@/views/Gb28181/Play/components/PlayDialog.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const tableData = ref<GbDeviceChannel[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const searchForm = ref<GbDeviceChannelPageRequest>({
  page: 1,
  pageSize: 20,
  deviceId: (route.query.deviceId as string) || '',
})

async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getGbDeviceChannelPageApi(params)
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
  searchForm.value = { page: 1, pageSize: 20, deviceId: searchForm.value.deviceId }
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

function handleBack() {
  router.push({ path: '/gb28181/device' })
}

const playDialogRef = ref<InstanceType<typeof PlayDialog> | null>(null)

function handlePlay(row: GbDeviceChannel) {
  playDialogRef.value?.open(row.deviceId, row.channelId, row.name)
}

const ptzTypeMap: Record<number, string> = {
  0: '未知',
  1: '球机',
  2: '半球',
  3: '固定枪机',
  4: '遥控枪机',
}

watch(
  () => route.query.deviceId,
  (v) => {
    searchForm.value.deviceId = (v as string) || ''
    handleSearch()
  },
)

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="设备 ID">
          <el-input v-model="searchForm.deviceId" placeholder="设备国标 ID 精确" clearable />
        </el-form-item>
        <el-form-item label="通道 ID">
          <el-input v-model="searchForm.channelId" placeholder="通道国标 ID 模糊" clearable />
        </el-form-item>
        <el-form-item label="通道名称">
          <el-input v-model="searchForm.name" placeholder="名称模糊" clearable />
        </el-form-item>
        <el-form-item label="在线">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="ON" value="ON" />
            <el-option label="OFF" value="OFF" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'gb28181:device-channel:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button :icon="Back" @click="handleBack">返回设备列表</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 320px)">
        <el-table-column prop="channelId" label="通道 ID" width="200" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="deviceId" label="所属设备" width="200" />
        <el-table-column prop="manufacturer" label="厂商" width="120" />
        <el-table-column prop="model" label="型号" width="160" />
        <el-table-column label="云台" width="100" align="center">
          <template #default="{ row }">{{ ptzTypeMap[row.ptzType ?? 0] }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ON' ? 'success' : 'info'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="address" label="安装地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="坐标" width="180">
          <template #default="{ row }">
            <span v-if="row.longitude || row.latitude">{{ row.longitude }}, {{ row.latitude }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'gb28181:play:start'"
              :disabled="row.status !== 'ON'"
              type="primary"
              link
              :icon="VideoPlay"
              @click="handlePlay(row)"
            >播放</el-button>
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

    <PlayDialog ref="playDialogRef" />
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
