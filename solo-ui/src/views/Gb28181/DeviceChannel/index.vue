<script lang="ts">
export default { name: 'Gb28181DeviceChannel' }
</script>
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Refresh, Back } from '@element-plus/icons-vue'
import type { GbDeviceChannelPageRequest, GbDeviceChannelVO } from '@/types/api'
import { getGbDeviceChannelPageApi } from '@/api/gb28181'
import PlayDialog from '@/views/Gb28181/Play/components/PlayDialog.vue'
import PlaybackDialog from '@/views/Gb28181/Playback/components/PlaybackDialog.vue'
import ChannelDetailDialog from './components/ChannelDetailDialog.vue'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const tableData = ref<GbDeviceChannelVO[]>([])
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

function handlePlay(row: GbDeviceChannelVO) {
  playDialogRef.value?.open(row.deviceId, row.channelId, row.name)
}

const playbackDialogRef = ref<InstanceType<typeof PlaybackDialog> | null>(null)

function handlePlayback(row: GbDeviceChannelVO) {
  playbackDialogRef.value?.open(row.deviceId, row.channelId, row.name)
}

const detailDialogRef = ref<InstanceType<typeof ChannelDetailDialog> | null>(null)

function handleDetail(row: GbDeviceChannelVO) {
  detailDialogRef.value?.open(row)
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
        <el-form-item label="设备编码">
          <el-input v-model="searchForm.deviceId" placeholder="所属设备国标编码精确" clearable />
        </el-form-item>
        <el-form-item label="通道编码">
          <el-input v-model="searchForm.channelId" placeholder="通道国标编码模糊" clearable />
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
        <el-table-column prop="channelId" label="通道编码" />
        <el-table-column prop="name" label="名称" show-overflow-tooltip />
        <el-table-column prop="deviceId" label="设备编码" />
        <el-table-column prop="deviceName" label="设备名称" show-overflow-tooltip />
        <el-table-column label="云台" align="center">
          <template #default="{ row }">{{ ptzTypeMap[row.ptzType ?? 0] }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="110">
          <template #default="{ row }">
            <ListStatusBar :active="row.status === 'ON'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'gb28181:device-channel:list'"
              type="primary"
              link
              @click="handleDetail(row)"
            >详情</el-button>
            <el-button
              v-permission="'gb28181:play:start'"
              :disabled="row.status !== 'ON'"
              type="primary"
              link
              @click="handlePlay(row)"
            >播放</el-button>
            <el-button
              v-permission="'gb28181:playback:start'"
              type="primary"
              link
              @click="handlePlayback(row)"
            >回放</el-button>
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
    <PlaybackDialog ref="playbackDialogRef" />
    <ChannelDetailDialog ref="detailDialogRef" />
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
