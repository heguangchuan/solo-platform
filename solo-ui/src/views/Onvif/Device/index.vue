<script lang="ts">
export default { name: 'OnvifDevice' }
</script>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, Edit, Delete, Monitor } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { OnvifDevice, OnvifDevicePageRequest } from '@/types/api'
import {
  deleteOnvifDeviceApi,
  getOnvifDevicePageApi,
  probeOnvifDeviceApi,
  saveOnvifDeviceApi,
  updateOnvifDeviceApi,
} from '@/api/onvif'
import ListStatusBar from '@/views/Gb28181/components/ListStatusBar.vue'

const loading = ref(false)
const tableData = ref<OnvifDevice[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<OnvifDevicePageRequest>({ page: 1, pageSize: 10 })

/** 数据来源文案 */
const sourceMap: Record<number, string> = { 1: '系统新增', 2: '表格导入' }
function sourceText(source?: number) {
  return source == null ? '-' : sourceMap[source] ?? '-'
}

// ---------------- 新增/编辑对话框 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const submitting = ref(false)
const form = ref<Partial<OnvifDevice>>(makeEmpty())

function makeEmpty(): Partial<OnvifDevice> {
  return {
    name: '',
    host: '',
    port: 80,
    username: 'admin',
    password: '',
    transport: 'TCP',
    streamType: 0,
  }
}

const rules: FormRules = reactive({
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入设备地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入设备端口', trigger: 'blur' }],
  username: [{ required: true, message: '请输入设备账号', trigger: 'blur' }],
})

// ---------------- 列表 ----------------
async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getOnvifDevicePageApi(params)
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

function handleEdit(row: OnvifDevice) {
  isEdit.value = true
  dialogTitle.value = '编辑设备'
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateOnvifDeviceApi(form.value)
      ElMessage.success('修改成功')
    } else {
      await saveOnvifDeviceApi(form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

// ---------------- 探测 ----------------
async function handleProbe(row: OnvifDevice) {
  loading.value = true
  try {
    await probeOnvifDeviceApi(row.id)
    ElMessage.success('探测已触发')
    await fetchData()
  } finally {
    loading.value = false
  }
}

// ---------------- 删除 ----------------
async function handleDelete(row: OnvifDevice) {
  try {
    await ElMessageBox.confirm(`确定要删除设备 "${row.host}:${row.port}" 吗？（不影响实际设备）`, '提示', {
      type: 'warning',
    })
    await deleteOnvifDeviceApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

onMounted(fetchData)
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="设备名称">
          <el-input v-model="searchForm.name" placeholder="名称模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="设备IP">
          <el-input v-model="searchForm.host" placeholder="IP 模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="设备状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="在线" :value="1" />
            <el-option label="离线" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'onvif:device:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'onvif:device:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 360px)">
        <el-table-column prop="name" label="设备名称" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '—' }}</template>
        </el-table-column>
        <el-table-column label="网络地址" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.host }}:{{ row.port }}</template>
        </el-table-column>
        <el-table-column label="设备状态" align="center" width="85">
          <template #default="{ row }">
            <el-tooltip v-if="row.status !== 1 && row.errorMsg" :content="row.errorMsg" placement="top">
              <ListStatusBar :active="false" />
            </el-tooltip>
            <ListStatusBar v-else :active="row.status === 1" />
          </template>
        </el-table-column>
        <el-table-column prop="manufacturer" label="厂商" show-overflow-tooltip>
          <template #default="{ row }">{{ row.manufacturer || '—' }}</template>
        </el-table-column>
        <el-table-column prop="model" label="型号" show-overflow-tooltip>
          <template #default="{ row }">{{ row.model || '—' }}</template>
        </el-table-column>
        <el-table-column prop="firmware" label="固件版本" show-overflow-tooltip>
          <template #default="{ row }">{{ row.firmware || '—' }}</template>
        </el-table-column>
        <el-table-column label="码流" align="center" width="80">
          <template #default="{ row }">{{ row.streamType === 1 ? '子码流' : '主码流' }}</template>
        </el-table-column>
        <el-table-column prop="transport" label="传输" align="center" width="80" />
        <el-table-column label="数据来源" align="center" width="100">
          <template #default="{ row }">{{ sourceText(row.source) }}</template>
        </el-table-column>
        <el-table-column prop="lastCheckTime" label="最近探测" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastCheckTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="探测">
                <el-button
                  v-permission="'onvif:device:probe'"
                  type="primary"
                  link
                  :icon="Monitor"
                  @click="handleProbe(row)"
                />
              </el-tooltip>
              <el-tooltip content="编辑">
                <el-button
                  v-permission="'onvif:device:edit'"
                  type="primary"
                  link
                  :icon="Edit"
                  @click="handleEdit(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button
                  v-permission="'onvif:device:delete'"
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
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false" top="10vh">
      <el-alert
        title="保存后会立即向设备发起一次探测，回填厂商、型号、固件、RTSP 流地址等信息"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="设备名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="设备地址" prop="host">
          <el-input v-model="form.host" placeholder="设备 IP，如 192.168.1.10" />
        </el-form-item>
        <el-form-item label="设备端口" prop="port">
          <el-input-number
            v-model="form.port"
            :min="1"
            :max="65535"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="设备账号" prop="username">
          <el-input v-model="form.username" autocomplete="off" />
        </el-form-item>
        <el-form-item label="设备密码">
          <el-input v-model="form.password" type="password" autocomplete="new-password" show-password />
        </el-form-item>
        <el-form-item label="传输协议">
          <el-select v-model="form.transport" style="width: 100%">
            <el-option label="TCP" value="TCP" />
            <el-option label="UDP" value="UDP" />
          </el-select>
        </el-form-item>
        <el-form-item label="码流类型">
          <el-select v-model="form.streamType" style="width: 100%">
            <el-option label="主码流" :value="0" />
            <el-option label="子码流" :value="1" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleConfirm">确 定</el-button>
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
  gap: 8px;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
