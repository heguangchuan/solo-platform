<script lang="ts">
export default { name: 'Gb28181Device' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Upload } from '@element-plus/icons-vue'
import type { GbDevice, GbDevicePageRequest } from '@/types/api'
import {
  getGbDevicePageApi,
  syncGbDeviceApi,
  deleteGbDeviceApi,
  saveGbDeviceApi,
  updateGbDeviceApi,
} from '@/api/gb28181'
import DeviceDetailDialog from './components/DeviceDetailDialog.vue'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<GbDevice[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<GbDevicePageRequest>({ page: 1, pageSize: 10 })

/** 数据来源文案 */
const sourceMap: Record<number, string> = { 1: '系统新增', 2: '表格导入', 3: '设备注册' }
function sourceText(source?: number) {
  return source == null ? '-' : sourceMap[source] ?? '-'
}

// ---------------- 新增/编辑对话框 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const form = ref<Partial<GbDevice>>(makeEmpty())

function makeEmpty(): Partial<GbDevice> {
  return {
    deviceId: '',
    name: '',
    manufacturer: '',
    model: '',
    firmware: '',
    sipDomain: '',
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
function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增设备'
  form.value = makeEmpty()
  dialogVisible.value = true
}

function handleEdit(row: GbDevice) {
  isEdit.value = true
  dialogTitle.value = '编辑设备'
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isEdit.value) {
    await updateGbDeviceApi(form.value)
    ElMessage.success('修改成功')
  } else {
    await saveGbDeviceApi(form.value)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
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
        <el-form-item label="设备编码">
          <el-input v-model="searchForm.deviceId" placeholder="国标编码模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="searchForm.name" placeholder="名称模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="在线" :value="1" />
            <el-option label="离线" :value="0" />
            <el-option label="未注册" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="searchForm.source" placeholder="请选择" clearable style="width: 120px">
            <el-option label="系统新增" :value="1" />
            <el-option label="表格导入" :value="2" />
            <el-option label="设备注册" :value="3" />
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
        <el-button v-permission="'gb28181:device:add'" :icon="Upload" disabled>导入</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 360px)">
        <el-table-column prop="deviceId" label="设备编码" />
        <el-table-column prop="name" label="名称" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="110">
          <template #default="{ row }">
            <ListStatusBar v-if="row.status !== 2" :active="row.status === 1" />
            <el-tag v-else type="info" size="small">未注册</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据来源" align="center" width="100">
          <template #default="{ row }">{{ sourceText(row.source) }}</template>
        </el-table-column>
        <el-table-column prop="hostAddress" label="网络地址" />
        <el-table-column prop="channelCount" label="通道数" align="center" />
        <el-table-column prop="keepaliveTime" label="最近心跳" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'gb28181:device:list'"
              type="primary"
              link
              @click="handleDetail(row)"
            >详情</el-button>
            <el-button
              v-permission="'gb28181:device:edit'"
              type="primary"
              link
              @click="handleEdit(row)"
            >编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <el-alert
        title="状态由设备注册流程自动维护，手动录入的设备默认为“未注册”"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="设备编码" prop="deviceId">
          <el-input v-model="form.deviceId" placeholder="设备国标ID（20位）" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="设备名称" prop="name">
          <el-input v-model="form.name" placeholder="设备显示名称" />
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
        <el-form-item label="信令域">
          <el-input v-model="form.sipDomain" placeholder="设备 SIP 域" />
        </el-form-item>
        <el-form-item label="传输协议">
          <el-select v-model="form.transport" style="width: 160px">
            <el-option label="UDP" value="UDP" />
            <el-option label="TCP" value="TCP" />
          </el-select>
        </el-form-item>
        <el-form-item label="字符集">
          <el-select v-model="form.charset" style="width: 160px">
            <el-option label="GB2312" value="GB2312" />
            <el-option label="UTF-8" value="UTF-8" />
          </el-select>
        </el-form-item>
        <el-form-item label="注册有效期">
          <el-input-number v-model="form.expires" :min="0" :max="86400" />
          <span style="margin-left: 8px">秒</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>

    <DeviceDetailDialog ref="detailDialogRef" />
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
