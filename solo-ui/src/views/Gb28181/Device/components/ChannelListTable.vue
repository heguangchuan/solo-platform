<script setup lang="ts">
/**
 * 通道列表表格（可复用组件）
 * <p>供弹窗和独立页面共同使用</p>
 */
import { ref, nextTick, onMounted, watch } from 'vue'
import type { GbDeviceChannel, GbDeviceChannelPageRequest, GbDeviceChannelVO, GbPtzPreset } from '@/types/api'
import {
  getGbDeviceChannelPageApi,
  getGbDeviceApi,
  saveGbDeviceChannelApi,
  updateGbDeviceChannelApi,
  deleteGbDeviceChannelApi,
  batchDeleteGbDeviceChannelApi,
  getPtzPresetListApi,
} from '@/api/gb28181'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, View, Edit, VideoCamera, VideoPause, Delete, Location } from '@element-plus/icons-vue'
import PlayDialog from '@/views/Gb28181/Play/components/PlayDialog.vue'
import PlaybackDialog from '@/views/Gb28181/Playback/components/PlaybackDialog.vue'
import ChannelDetailDialog from '@/views/Gb28181/DeviceChannel/components/ChannelDetailDialog.vue'
import PtzPresetDialog from '@/views/Gb28181/DeviceChannel/components/PtzPresetDialog.vue'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'

interface Props {
  deviceId?: string
  /** 是否显示设备编码列（独立页面需要显示，弹窗不需要） */
  showDeviceColumn?: boolean
  /** 新增按钮是否由外部接管（独立页面用左设备选择+右多通道弹窗） */
  externalAdd?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showDeviceColumn: true,
  externalAdd: false,
})

const emit = defineEmits<{
  (e: 'external-add'): void
}>()

const loading = ref(false)
const tableData = ref<GbDeviceChannelVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedRows = ref<GbDeviceChannelVO[]>([])

const searchForm = ref<GbDeviceChannelPageRequest>({
  page: 1,
  pageSize: 20,
  deviceId: props.deviceId || '',
})

/** 数据来源文案 */
const sourceMap: Record<number, string> = { 1: '系统新增', 2: '表格导入', 3: '设备注册', 4: '级联平台(历史)' }
function sourceText(source?: number) {
  return source == null ? '-' : sourceMap[source] ?? '-'
}

const ptzTypeMap: Record<number, string> = {
  0: '未知',
  1: '球机',
  2: '半球',
  3: '固定枪机',
  4: '遥控枪机',
}

/** 资源类型文案：parental 0=通道 / 1=分组 */
const parentalMap: Record<number, { label: string; tagType: 'primary' | 'warning' }> = {
  0: { label: '通道', tagType: 'primary' },
  1: { label: '分组', tagType: 'warning' },
}
function parentalLabel(parental?: number) {
  if (parental == null) return '-'
  return parentalMap[parental]?.label ?? '-'
}

function makeEmpty(): Partial<GbDeviceChannel> {
  return {
    channelId: '',
    reportName: '',
    name: '',
    manufacturer: '',
    model: '',
    owner: '',
    civilCode: '',
    address: '',
    ptzType: 0,
    longitude: undefined,
    latitude: undefined,
  }
}

// ---------------- 新增/编辑对话框 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const form = ref<Partial<GbDeviceChannel>>(makeEmpty())

const rules = {
  channelId: [
    { required: true, message: '请输入通道国标编码（20位）', trigger: 'blur' },
    { pattern: /^\d{20}$/, message: '通道国标编码为20位数字', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入通道名称', trigger: 'blur' }],
}

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
  // 父组件传了 deviceId（设备详情页嵌入场景）时该值是锁定的归属条件，重置不能清；
  // 独立通道管理页没有这个 prop，设备编码是普通搜索项，一并清空
  searchForm.value = { page: 1, pageSize: 20, deviceId: props.deviceId || '' }
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

// ---------------- 新增/编辑 ----------------
async function handleAdd() {
  if (props.externalAdd) {
    emit('external-add')
    return
  }
  if (!searchForm.value.deviceId) {
    ElMessage.warning('所属设备不存在，无法新增通道')
    return
  }
  // 新增通道需要所属设备的数据库主键，通过设备国标编码反查
  let device
  try {
    const res = await getGbDeviceApi(searchForm.value.deviceId!)
    device = res.data
  } catch {
    return
  }
  if (!device) {
    ElMessage.error('所属设备不存在，无法新增通道')
    return
  }
  isEdit.value = false
  dialogTitle.value = '新增通道'
  form.value = { ...makeEmpty(), deviceId: device.deviceId }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function handleEdit(row: GbDeviceChannelVO) {
  isEdit.value = true
  dialogTitle.value = '编辑通道'
  form.value = { ...row }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isEdit.value) {
    await updateGbDeviceChannelApi(form.value)
    ElMessage.success('修改成功')
  } else {
    await saveGbDeviceChannelApi(form.value)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(row: GbDeviceChannelVO) {
  try {
    await ElMessageBox.confirm(`确定要删除通道 "${row.channelId}" 吗？`, '提示', { type: 'warning' })
    await deleteGbDeviceChannelApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

function handleSelectionChange(rows: GbDeviceChannelVO[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的通道')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 条通道吗？此操作不可恢复。`,
      '提示',
      { type: 'warning' }
    )
    const ids = selectedRows.value.map(r => r.id)
    await batchDeleteGbDeviceChannelApi(ids)
    ElMessage.success('删除成功')
    fetchData()
    selectedRows.value = []
  } catch {
    // cancelled
  }
}

const playDialogRef = ref<InstanceType<typeof PlayDialog> | null>(null)

function handlePlay(row: GbDeviceChannelVO) {
  playDialogRef.value?.open(row.deviceId, row.channelId, row.id, row.name)
}

const playbackDialogRef = ref<InstanceType<typeof PlaybackDialog> | null>(null)

function handlePlayback(row: GbDeviceChannelVO) {
  playbackDialogRef.value?.open(row.deviceId, row.channelId, row.id, row.name)
}

const ptzPresetDialogRef = ref<InstanceType<typeof PtzPresetDialog> | null>(null)

function handlePtzPreset(row: GbDeviceChannelVO) {
  ptzPresetDialogRef.value?.open({
    deviceId: row.deviceId!,
    channelDbId: String(row.id!),
    channelId: row.channelId!,
    channelName: row.name!,
  })
}

const detailDialogRef = ref<InstanceType<typeof ChannelDetailDialog> | null>(null)

function handleDetail(row: GbDeviceChannelVO) {
  detailDialogRef.value?.open(row)
}

watch(
  () => props.deviceId,
  (v) => {
    searchForm.value.deviceId = v || ''
    handleSearch()
  },
)

onMounted(() => {
  fetchData()
})

defineExpose({ fetchData })
</script>

<template>
  <div>
    <el-card shadow="hover" v-if="showDeviceColumn" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="通道名称">
          <el-input v-model="searchForm.name" placeholder="请输入通道名称" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="通道编码">
          <el-input v-model="searchForm.channelId" placeholder="请输入通道国标编码" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="设备编码">
          <el-input v-model="searchForm.deviceId" placeholder="请输入设备国标编码" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="分组编码">
          <el-input v-model="searchForm.parentId" placeholder="请输入分组国标编码" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="通道状态">
          <el-select v-model="searchForm.status" placeholder="请选择通道状态" clearable style="width: 240px">
            <el-option label="在线" value="ON" />
            <el-option label="离线" value="OFF" />
            <el-option label="未注册" value="UNREGISTERED" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据来源">
          <el-select v-model="searchForm.source" placeholder="请选择数据来源" clearable style="width: 240px">
            <el-option label="系统新增" :value="1" />
            <el-option label="表格导入" :value="2" />
            <el-option label="设备注册" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="searchForm.parental" placeholder="请选择资源类型" clearable style="width: 240px">
            <el-option label="全部" :value="undefined" />
            <el-option label="通道" :value="0" />
            <el-option label="分组" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'gb28181:device-channel:list'" type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" :style="{ 'margin-top': showDeviceColumn ? '16px' : 0 }">
      <div class="table-toolbar">
        <el-button v-permission="'gb28181:device-channel:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'gb28181:device-channel:delete'" type="danger" :icon="Delete" @click="handleBatchDelete" :disabled="selectedRows.length === 0">删除</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(65vh - 200px)" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column prop="name" label="通道名称" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || row.firstName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="channelId" label="通道编码" width="200" show-overflow-tooltip />
        <el-table-column prop="deviceId" label="设备编码" v-if="showDeviceColumn" width="200" show-overflow-tooltip />
        <el-table-column label="云台类型" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.parental === 1 ? '—' : ptzTypeMap[row.ptzType ?? 0] }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="85" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.parental === 1" class="text-secondary">—</span>
            <ListStatusBar v-else-if="row.status === 'ON' || row.status === 'OFF'" :active="row.status === 'ON'" />
            <el-tag v-else type="info" size="small">未注册</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据来源" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ sourceText(row.source) }}</template>
        </el-table-column>
        <el-table-column label="资源类型" align="center" width="100">
          <template #default="{ row }">
            <el-tag :type="parentalMap[row.parental ?? 0]?.tagType" disable-transitions size="small">
              {{ parentalLabel(row.parental) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="详情">
                <el-button
                  v-permission="'gb28181:device-channel:list'"
                  type="primary"
                  link
                  :icon="View"
                  @click="handleDetail(row)"
                />
              </el-tooltip>
              <el-tooltip content="编辑">
                <el-button
                  v-permission="'gb28181:device-channel:edit'"
                  type="primary"
                  link
                  :icon="Edit"
                  @click="handleEdit(row)"
                />
              </el-tooltip>
              <el-tooltip content="播放">
                <el-button
                  :disabled="row.status !== 'ON'"
                  type="primary"
                  link
                  :icon="VideoCamera"
                  @click="handlePlay(row)"
                />
              </el-tooltip>
              <el-tooltip content="回放">
                <el-button
                  type="primary"
                  link
                  :icon="VideoPause"
                  @click="handlePlayback(row)"
                />
              </el-tooltip>
              <el-tooltip content="预置位" v-if="(row.ptzType ?? 0) !== 3">
                <el-button
                  v-permission="'gb28181:ptz:preset'"
                  type="primary"
                  link
                  :icon="Location"
                  @click="handlePtzPreset(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button
                  v-permission="'gb28181:device-channel:delete'"
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
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="780px" :close-on-click-modal="false" top="10vh" max-height="80vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="通道编码" prop="channelId">
          <el-input v-model="form.channelId" placeholder="请输入通道国标编码（20位）" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="通道名称" prop="name">
          <el-input v-model="form.name" placeholder="通道显示名称" />
        </el-form-item>
        <el-form-item label="设备厂商">
          <el-input v-model="form.manufacturer" placeholder="厂商" />
        </el-form-item>
        <el-form-item label="设备型号">
          <el-input v-model="form.model" placeholder="型号" />
        </el-form-item>
        <el-form-item label="设备归属">
          <el-input v-model="form.owner" placeholder="设备归属" />
        </el-form-item>
        <el-form-item label="行政编码">
          <el-input v-model="form.civilCode" placeholder="行政区划编码" />
        </el-form-item>
        <el-form-item label="安装地址">
          <el-input v-model="form.address" placeholder="安装地址" />
        </el-form-item>
        <el-form-item label="云台类型">
          <el-select v-model="form.ptzType" style="width: 100%">
            <el-option v-for="(label, val) in ptzTypeMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="经度坐标">
          <el-input-number v-model="form.longitude" :precision="6" :step="0.000001" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="纬度坐标">
          <el-input-number v-model="form.latitude" :precision="6" :step="0.000001" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>

    <PlayDialog ref="playDialogRef" />
    <PlaybackDialog ref="playbackDialogRef" />
    <ChannelDetailDialog ref="detailDialogRef" />
    <PtzPresetDialog ref="ptzPresetDialogRef" />
  </div>
</template>

<style scoped>
.search-card {
  margin-bottom: 0;
}

.table-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
