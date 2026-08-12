
<script lang="ts">
export default { name: 'Gb28181Device' }
</script>
<script setup lang="ts">
import { ref, onMounted, reactive, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, View, Edit, VideoCamera, Delete } from '@element-plus/icons-vue'
import type { GbDevice, GbDevicePageRequest, GbDeviceChannel } from '@/types/api'
import {
  getGbDevicePageApi,
  syncGbDeviceApi,
  deleteGbDeviceApi,
  batchDeleteGbDeviceApi,
  saveGbDeviceApi,
  updateGbDeviceApi,
  detectGbDeviceApi,
  parseSyncStatus,
} from '@/api/gb28181'
import DeviceDetailDialog from './components/DeviceDetailDialog.vue'
import ChannelListDialog from './components/ChannelListDialog.vue'
import SyncProgressDialog from './components/SyncProgressDialog.vue'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<GbDevice[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const selectedRows = ref<GbDevice[]>([])

const searchForm = ref<GbDevicePageRequest>({ page: 1, pageSize: 10 })

/**
 * 数据来源文案。
 * 4=级联平台 已废弃——设备与下级平台不再区分（gb_platform 已翻转为黑名单），
 * 注册流程一律写 3=设备注册。保留 4 的映射仅为兼容历史数据。
 */
const sourceMap: Record<number, string> = { 1: '系统新增', 2: '表格导入', 3: '设备注册' }
/** 含已废弃项的完整映射，仅用于展示历史数据（不进筛选下拉） */
const sourceTextMap: Record<number, string> = { ...sourceMap, 4: '级联平台(历史)' }
function sourceText(source?: number): string {
  return source == null ? '-' : sourceTextMap[source] ?? '-'
}

// ---------------- 新增/编辑对话框 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const currentStep = ref(1)

interface FormData extends Partial<GbDevice> {
  newChannels: GbDeviceChannel[]
}

const form = reactive<FormData>({
  newChannels: [],
  ...makeEmpty()
})

async function nextStep() {
  if (!formRef.value) return
  await formRef.value.validate()
  currentStep.value++
}

function prevStep() {
  currentStep.value--
}

function addEmptyChannel() {
  form.newChannels.push({
    channelId: '',
    name: '',
  } as GbDeviceChannel)
}

function removeChannel(index: number) {
  form.newChannels.splice(index, 1)
}

function makeEmpty(): Partial<GbDevice> {
  return {
    deviceId: '',
    name: '',
    manufacturer: '',
    model: '',
    firmware: '',
    recvIp: '',
    transport: 'UDP',
    charset: 'GB2312',
    expires: 3600,
  }
}

const rules = {
  deviceId: [
    { required: true, message: '请输入设备国标ID（20位）', trigger: 'blur' },
    { pattern: /^\d{20}$/, message: '设备国标ID为20位数字', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  'newChannels.*.channelId': [
    { required: true, message: '请输入通道编码', trigger: 'blur' },
    { pattern: /^\d{20}$/, message: '通道国标ID为20位数字', trigger: 'blur' },
  ],
  'newChannels.*.name': [
    { required: true, message: '请输入通道名称', trigger: 'blur' },
  ],
}

// ---------------- 列表 ----------------
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

// ---------------- 新增/编辑 ----------------
async function handleAdd() {
  isEdit.value = false
  currentStep.value = 1
  dialogTitle.value = '新增设备'
  form.newChannels = []
  Object.assign(form, makeEmpty())
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function handleEdit(row: GbDevice) {
  isEdit.value = true
  currentStep.value = 1
  dialogTitle.value = '编辑设备'
  Object.assign(form, { newChannels: [] }, row)
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isEdit.value) {
    // 编辑只提交设备基本信息，不包含通道
    const { newChannels, ...deviceData } = form
    await updateGbDeviceApi(deviceData)
    ElMessage.success('修改成功')
  } else {
    // 新增：分离设备信息和通道列表
    const { newChannels, ...deviceData } = form
    await saveGbDeviceApi({
      device: deviceData,
      channels: form.newChannels,
    })
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  fetchData()
}

async function handleSync(row: GbDevice) {
  try {
    const res = await syncGbDeviceApi(row.deviceId!)
    // 打开进度弹窗：TRIGGERED（本次触发）或 ALREADY_RUNNING（已在跑）都展示，让用户看进度
    syncProgressDialogRef.value?.open(row.deviceId!, row.name || row.deviceId!)
    if (res.data?.result === 'ALREADY_RUNNING') {
      ElMessage.info('设备已有同步在进行中，已为你打开进度窗口')
    } else if (res.data?.result === 'TRIGGERED') {
      // 弹窗内会显示 RUNNING 状态，无需额外提示
    }
    // 同步完成后弹窗自动关闭并刷新列表（在 dialog 内部 ElMessage 提示）
    // 这里不主动刷新列表，由弹窗关闭后用户操作；或监听 success 后再 refresh
  } catch {
    // handled
  }
}

// ---------------- 存活检测 ----------------
function handleDetect() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要检测的设备')
    return
  }
  const ids = selectedRows.value.map(r => r.id)
  // 后端异步探测（发完即返回），前端不等待结果，直接提示成功
  detectGbDeviceApi(ids).catch(() => {})
  ElMessage.success('检测已发起，请稍后刷新查看设备状态')
}

function handleViewChannels(row: GbDevice) {
  channelListDialogRef.value?.open(row.deviceId!)
}

const detailDialogRef = ref<InstanceType<typeof DeviceDetailDialog> | null>(null)
const channelListDialogRef = ref<InstanceType<typeof ChannelListDialog> | null>(null)
const syncProgressDialogRef = ref<InstanceType<typeof SyncProgressDialog> | null>(null)

function handleDetail(row: GbDevice) {
  detailDialogRef.value?.open(row)
}

async function handleDelete(row: GbDevice) {
  try {
    await ElMessageBox.confirm(`确定要删除设备 "${row.deviceId}" 的记录吗？（不影响实际设备）`, '提示', {
      type: 'warning',
    })
    await deleteGbDeviceApi(row.deviceId!)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的设备')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 台设备吗？此操作不可恢复。`,
      '提示',
      { type: 'warning' }
    )
    // 雪花 ID > 2^53 时 JS Number 会丢失精度，保持字符串传递
    const ids = selectedRows.value.map(r => r.id)
    await batchDeleteGbDeviceApi(ids as any)
    ElMessage.success('删除成功')
    fetchData()
    selectedRows.value = []
  } catch {
    // cancelled
  }
}

function handleSelectionChange(rows: GbDevice[]) {
  selectedRows.value = rows
}

onMounted(() => {
  fetchData()
})
</script>
<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="设备编码">
          <el-input v-model="searchForm.deviceId" placeholder="国标编码模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="searchForm.name" placeholder="名称模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="设备状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="在线" :value="1" />
            <el-option label="离线" :value="0" />
            <el-option label="未注册" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据来源">
          <el-select v-model="searchForm.source" placeholder="请选择" clearable style="width: 120px">
            <el-option v-for="(val, key) in sourceMap" :key="key" :label="val" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'gb28181:device:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'gb28181:device:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'gb28181:device:delete'" type="danger" :icon="Delete" @click="handleBatchDelete" :disabled="selectedRows.length === 0">删除</el-button>
        <el-button v-permission="'gb28181:device:sync'" :icon="Refresh" @click="handleDetect" :disabled="selectedRows.length === 0">检测</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 360px)" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column prop="deviceId" label="设备编码" width="200" show-overflow-tooltip />
        <el-table-column prop="name" label="设备名称" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || row.reportName || row.hostAddress || '—' }}</template>
        </el-table-column>
        <el-table-column label="设备状态" align="center" width="85" show-overflow-tooltip>
          <template #default="{ row }">
            <ListStatusBar v-if="row.status !== 2" :active="row.status === 1" />
            <el-tag v-else type="info" size="small">未注册</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据来源" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ sourceText(row.source) }}</template>
        </el-table-column>
        <el-table-column prop="hostAddress" label="网络地址" show-overflow-tooltip />
        <el-table-column prop="keepaliveTime" label="最近心跳" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="详情">
                <el-button
                  v-permission="'gb28181:device:list'"
                  type="primary"
                  link
                  @click="handleDetail(row)"
                >
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="编辑">
                <el-button
                  v-permission="'gb28181:device:edit'"
                  type="primary"
                  link
                  @click="handleEdit(row)"
                >
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="通道">
                <el-button
                  v-permission="'gb28181:device-channel:list'"
                  type="primary"
                  link
                  @click="handleViewChannels(row)"
                >
                  <el-icon><VideoCamera /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="同步">
                <el-button
                  v-permission="'gb28181:device:sync'"
                  type="primary"
                  link
                  :disabled="row.status !== 1"
                  @click="handleSync(row)"
                >
                  <el-icon><Refresh /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button
                  v-permission="'gb28181:device:delete'"
                  type="danger"
                  link
                  @click="handleDelete(row)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="960px" max-height="80vh" :close-on-click-modal="false" top="10vh">
      <el-steps :active="currentStep" finish-status="success" align-center style="margin-bottom: 24px">
        <el-step title="设备信息" description="填写设备基本信息" />
        <el-step title="添加通道" description="配置设备通道" v-if="!isEdit" />
      </el-steps>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <template v-if="currentStep === 1 || isEdit">
          <el-form-item label="设备编码" prop="deviceId">
            <el-input v-model="form.deviceId" placeholder="请输入设备国标编码（20位）" :disabled="isEdit" />
          </el-form-item>
          <el-form-item label="设备名称" prop="name">
            <el-input v-model="form.name" placeholder="自定义显示名称" />
          </el-form-item>
          <el-form-item label="设备厂商">
            <el-input v-model="form.manufacturer" placeholder="厂商" />
          </el-form-item>
          <el-form-item label="设备型号">
            <el-input v-model="form.model" placeholder="型号" />
          </el-form-item>
          <el-form-item label="固件版本">
            <el-input v-model="form.firmware" placeholder="固件版本" />
          </el-form-item>
          <el-form-item label="收流地址">
            <el-input v-model="form.recvIp" />
          </el-form-item>
          <el-form-item label="传输协议">
            <el-select v-model="form.transport" style="width: 100%">
              <el-option label="UDP" value="UDP" />
              <el-option label="TCP" value="TCP" />
            </el-select>
          </el-form-item>
          <el-form-item label="字符编码">
            <el-select v-model="form.charset" style="width: 100%">
              <el-option label="GB2312" value="GB2312" />
              <el-option label="UTF-8" value="UTF-8" />
            </el-select>
          </el-form-item>
          <el-form-item label="注册期限">
            <el-input v-model.number="form.expires" placeholder="注册期限（秒）" style="width: 100%">
              <template #append>秒</template>
            </el-input>
          </el-form-item>
        </template>

        <template v-if="currentStep === 2 && !isEdit">
          <el-table :data="form.newChannels" border style="width: 100%" :show-header="true">
            <el-table-column label="通道编码" width="240">
              <template #default="{ row, $index }">
              <el-form-item :prop="`newChannels.${$index}.channelId`" class="no-margin">
                <el-input v-model="row.channelId" placeholder="请输入通道国标编码（20位）" style="width: 100%" />
              </el-form-item>
            </template>
            </el-table-column>
            <el-table-column label="通道名称" min-width="200">
              <template #default="{ row, $index }">
              <el-form-item :prop="`newChannels.${$index}.name`" class="no-margin">
                <el-input v-model="row.name" placeholder="通道名称" style="width: 100%" />
              </el-form-item>
            </template>
            </el-table-column>
            <el-table-column width="80" align="center">
              <template #header>
                <el-button type="primary" plain :icon="Plus" size="small" @click="addEmptyChannel">添加</el-button>
              </template>
              <template #default="{ $index }">
                <el-button type="danger" link @click="removeChannel($index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button v-if="currentStep > 1 && !isEdit" type="primary" @click="prevStep">上一步</el-button>
        <el-button v-if="currentStep < 2 && !isEdit" type="primary" @click="nextStep">下一步</el-button>
        <el-button v-if="(isEdit || currentStep === 2)" type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>

    <DeviceDetailDialog ref="detailDialogRef" />
    <ChannelListDialog ref="channelListDialogRef" />
    <SyncProgressDialog ref="syncProgressDialogRef" />
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

.channel-list {
  margin-bottom: 8px;
}

.no-margin {
  margin-bottom: 0;
}

:deep(.el-table td .el-form-item) {
  margin-right: 0;
}

:deep(.el-table td .el-form-item__content) {
  margin-left: 0 !important;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
